// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.acm

import cats.{ Applicative, Monad }
import cats.effect.{ Resource, Temporal }
import cats.effect.std.Dispatcher
import cats.effect.syntax.temporal._
import cats.syntax.all._
import engage.epics.Channel.StreamEvent
import engage.epics.VerifiedEpics._
import engage.server.ApplyCommandResult
import engage.epics.{ Channel, EpicsService, VerifiedEpics }
import engage.epics.EpicsSystem.TelltaleChannel
import fs2.Stream
import fs2.RaiseThrowable._
import mouse.all.booleanSyntaxMouse

import scala.concurrent.duration.FiniteDuration

trait GeminiApplyCommand[F[_]] {

  /**
   * Given a pair of apply and CAR records, this function produces a program that will trigger the
   * apply record and then monitor the apply and CAR record to finally produce a command result. If
   * the command takes longer than the <code>timeout</code>. The program is contained in a
   * <code>VerifiedEpics</code> that checks the connection to the channels.
   * @param timeout
   *   The timeout for running the command
   * @return
   */
  def post(timeout: FiniteDuration): VerifiedEpics[F, ApplyCommandResult]
}

object GeminiApplyCommand {

  sealed trait CmdPhase extends Product with Serializable

  case object WaitingBusy extends CmdPhase
  case object WaitingIdle extends CmdPhase

  sealed trait PostState                                    extends Product with Serializable
  case object WaitingStart                                  extends PostState
  case class Processing(aout: Option[Int], phase: CmdPhase) extends PostState

  sealed trait Event                   extends Product with Serializable
  case object Started                  extends Event
  case class ApplyValChange(v: Int)    extends Event
  case class CarValChange(v: CarState) extends Event

  final class GeminiApplyCommandImpl[F[_]: Dispatcher: Temporal](
    telltaleChannel: TelltaleChannel,
    apply:           ApplyRecord[F],
    car:             CarRecord[F]
  ) extends GeminiApplyCommand[F] {
    override def post(timeout: FiniteDuration): VerifiedEpics[F, ApplyCommandResult] = {
      val streamsV = for {
        avrs   <- eventStream(telltaleChannel, apply.oval)
        cvrs   <- eventStream(telltaleChannel, car.oval)
        dwr    <- writeChannel(telltaleChannel, apply.dir)(Applicative[F].pure(CadDirective.START))
                    .map(Resource.pure[F, F[Unit]])
        msrr   <- readChannel(telltaleChannel, apply.mess).map(Resource.pure[F, F[String]])
        omsrr  <- readChannel(telltaleChannel, car.omss).map(Resource.pure[F, F[String]])
        clidrr <- readChannel(telltaleChannel, car.clid).map(Resource.pure[F, F[Int]])
      } yield for {
        avs   <- avrs
        cvs   <- cvrs
        dw    <- dwr
        msr   <- msrr
        omsr  <- omsrr
        clidr <- clidrr
      } yield (avs, cvs, dw, msr, omsr, clidr)

      streamsV.map(_.use { case (avs, cvs, dw, msr, omsr, clidr) =>
        processCommand(avs, cvs, dw, msr, omsr, clidr).timeout(timeout)
      })

    }

    private def processCommand(
      avs:   Stream[F, StreamEvent[Int]],
      cvs:   Stream[F, StreamEvent[CarState]],
      dw:    F[Unit],
      msr:   F[String],
      omsr:  F[String],
      clidr: F[Int]
    ): F[ApplyCommandResult] =
      Stream[F, Stream[F, Event]](
        avs.flatMap {
          case StreamEvent.ValueChanged(v) => Stream(ApplyValChange(v))
          case StreamEvent.Disconnected    =>
            Stream.raiseError[F](new Throwable(s"Apply record ${apply.name} disconnected"))
          case _                           => Stream.empty
        },
        cvs.flatMap {
          case StreamEvent.ValueChanged(v) => Stream(CarValChange(v))
          case StreamEvent.Disconnected    =>
            Stream.raiseError[F](new Throwable(s"CAR record ${apply.name} disconnected"))
          case _                           => Stream.empty
        },
        Stream.eval(dw).as(Started)
      ).parJoin(3)
        .mapAccumulate[PostState, F[Option[ApplyCommandResult]]](WaitingStart) { (s, ev) =>
          (s, ev) match {
            case (WaitingStart, Started)                                         =>
              (Processing(None, WaitingBusy), none[ApplyCommandResult].pure[F])
            case (Processing(None, x), ApplyValChange(v)) if v < 0               =>
              (Processing(None, x),
               msr.flatMap(msg =>
                 new Throwable(s"Apply record ${apply.name} failed with error: $msg").raiseError
               )
              )
            case (Processing(None, x), ApplyValChange(v)) if v === 0             =>
              (Processing(None, x),
               new Throwable(
                 s"Apply record ${apply.name} triggered externally while processing."
               ).raiseError
              )
            case (Processing(None, x), ApplyValChange(v)) if v > 0               =>
              (Processing(v.some, x), none[ApplyCommandResult].pure[F])
            case (Processing(v, _), CarValChange(CarState.Error))                =>
              (Processing(v, WaitingIdle),
               omsr.flatMap(msg =>
                 new Throwable(s"CAR record ${car.name} has error: $msg").raiseError
               )
              )
            case (Processing(v, WaitingBusy), CarValChange(CarState.Busy))       =>
              (Processing(v, WaitingIdle), none[ApplyCommandResult].pure[F])
            case (Processing(None, WaitingIdle), CarValChange(CarState.Idle))    =>
              (Processing(None, WaitingBusy), none[ApplyCommandResult].pure[F])
            case (Processing(Some(v), WaitingIdle), CarValChange(CarState.Idle)) =>
              (Processing(Some(v), WaitingBusy),
               clidr.map(c => (c >= v).option(ApplyCommandResult.Completed))
              )
            case (Processing(Some(v), _), CarValChange(CarState.Paused))         =>
              (Processing(Some(v), WaitingBusy),
               clidr.map(c => (c >= v).option(ApplyCommandResult.Paused))
              )
            case _                                                               =>
              (WaitingStart, none[ApplyCommandResult].pure[F])
          }
        }
        .evalMap(_._2)
        .flattenOption
        .take(1)
        .compile
        .lastOrError

  }

  def build[F[_]: Dispatcher: Temporal](
    srv:             EpicsService[F],
    telltaleChannel: TelltaleChannel,
    applyName:       String,
    carName:         String
  ): Resource[F, GeminiApplyCommand[F]] = for {
    apply <- ApplyRecord.build(srv, applyName)
    car   <- CarRecord.build(srv, carName)
  } yield new GeminiApplyCommandImpl[F](telltaleChannel, apply, car)

  def smartSetParam[F[_]: Monad, A, B](
    tt:    TelltaleChannel,
    st:    Channel[F, A],
    pr:    Channel[F, B],
    cmp:   (A, B) => Boolean
  )(value: B): VerifiedEpics[F, Unit] = {
    val statusRead = readChannel(tt, st)
    val paramWrite = writeChannel(tt, pr)(value.pure[F])

    statusRead.flatMap(fa => ifF(fa.map(cmp(_, value)))(VerifiedEpics.unit[F])(paramWrite))
  }
}
