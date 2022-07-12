// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.effect.std.Dispatcher
import cats.effect.{ Async, Concurrent, Resource }
import cats.syntax.all._
import cats.{ Applicative, FlatMap, Monad, Parallel }
import mouse.boolean._
import fs2.Stream
import engage.epics.Channel.StreamEvent
import engage.epics.EpicsSystem.TelltaleChannel

import scala.concurrent.duration.FiniteDuration

/**
 * <code>VerifiedEpics</code> keeps a program that involves access to EPICS channels, together with
 * a list of the channels involved. Calling the method <code>verifiedRun</code> on a
 * <code>VerifiedEpics</code> instance will produce a program that adds a verification of all the
 * channels involved before executing the original program. Several constructors and combiners are
 * provided.
 */
object VerifiedEpics {

  trait ChannelTracker[A] {
    val systems: Map[TelltaleChannel, Set[RemoteChannel]]
    val run: A
  }

  case class Const[A](v: A) extends ChannelTracker[A] {
    override val systems: Map[TelltaleChannel, Set[RemoteChannel]] = Map.empty
    override val run: A                                            = v
  }

  case class Apply[A, B](fa: ChannelTracker[A], fab: ChannelTracker[A => B])
      extends ChannelTracker[B] {
    override val systems: Map[TelltaleChannel, Set[RemoteChannel]] = merge(fa.systems, fab.systems)
    override val run: B                                            = fab.run(fa.run)
  }

  case class Bind[A, B](fa: ChannelTracker[A], fafb: A => ChannelTracker[B])
      extends ChannelTracker[B] {
    val b                                                          = fafb(fa.run)
    override val systems: Map[TelltaleChannel, Set[RemoteChannel]] = merge(fa.systems, b.systems)
    override val run: B                                            = b.run
  }

  def merge(
    m1: Map[TelltaleChannel, Set[RemoteChannel]],
    m2: Map[TelltaleChannel, Set[RemoteChannel]]
  ): Map[TelltaleChannel, Set[RemoteChannel]] =
    (m1.toList ++ m2.toList).groupMap(_._1)(_._2).view.mapValues(_.reduce(_ ++ _)).toMap

  def pure[A](v: A): ChannelTracker[A] = Const(v)
  def unit: ChannelTracker[Unit]       = pure[Unit](())

  implicit class Ops[A](v: ChannelTracker[A]) extends AnyRef {
    def ap[B](ff: ChannelTracker[A => B]): ChannelTracker[B] = Apply(v, ff)
    def map[B](ff: A => B): ChannelTracker[B]                = ap(pure(ff))
  }

  type VerifiedEpics[F[_], A] = ChannelTracker[F[A]]

  case class ApplyF[F[_]: FlatMap, A, B](fa: VerifiedEpics[F, A], fab: VerifiedEpics[F, A => B])
      extends VerifiedEpics[F, B] {
    override val systems: Map[TelltaleChannel, Set[RemoteChannel]] = merge(fa.systems, fab.systems)
    override val run: F[B]                                         = fa.run.flatMap { a =>
      fab.run.map { fab =>
        fab(a)
      }
    }
  }

  case class BindF[F[_]: FlatMap, A, B](
    fa:   VerifiedEpics[F, A],
    fafb: F[A] => ChannelTracker[A => F[B]]
  ) extends VerifiedEpics[F, B] {
    val fb                                                         = fafb(fa.run)
    override val systems: Map[TelltaleChannel, Set[RemoteChannel]] = merge(fa.systems, fb.systems)
    override val run: F[B]                                         = fa.run.flatMap(fb.run)
  }

  case class IfF[F[_]: FlatMap, A](
    cond:     F[Boolean],
    trueVal:  VerifiedEpics[F, A],
    falseVal: VerifiedEpics[F, A]
  ) extends VerifiedEpics[F, A] {
    override val systems: Map[TelltaleChannel, Set[RemoteChannel]] =
      merge(trueVal.systems, falseVal.systems)
    override val run: F[A]                                         = cond.flatMap(_.fold(trueVal.run, falseVal.run))
  }

  case class Get[F[_], A](tt: TelltaleChannel, ch: Channel[F, A]) extends VerifiedEpics[F, A] {
    override val systems: Map[TelltaleChannel, Set[RemoteChannel]] = Map(tt -> Set(ch))
    override val run: F[A]                                         = ch.get
  }

  case class Put[F[_]: FlatMap, A](tt: TelltaleChannel, ch: Channel[F, A], fa: F[A])
      extends VerifiedEpics[F, Unit] {
    override val systems: Map[TelltaleChannel, Set[RemoteChannel]] = Map(tt -> Set(ch))
    override val run: F[Unit]                                      = fa.flatMap(ch.put)
  }

  case class EventStream[F[_]: Dispatcher: Concurrent, A](tt: TelltaleChannel, ch: Channel[F, A])
      extends VerifiedEpics[Resource[F, *], Stream[F, StreamEvent[A]]] {
    override val systems: Map[TelltaleChannel, Set[RemoteChannel]] = Map(tt -> Set(ch))
    override val run: Resource[F, Stream[F, StreamEvent[A]]]       = ch.eventStream
  }

  def pureF[F[_]: Applicative, A](v: A): VerifiedEpics[F, A]                            = pure[F[A]](v.pure[F])
  def unit[F[_]: Applicative]: VerifiedEpics[F, Unit]                                   = pure(Applicative[F].unit)
  def readChannel[F[_], A](tt: TelltaleChannel, ch: Channel[F, A]): VerifiedEpics[F, A] =
    Get(tt, ch)
  def writeChannel[F[_]: FlatMap, A](tt: TelltaleChannel, ch: Channel[F, A])(
    fa:                                  F[A]
  ): VerifiedEpics[F, Unit] = Put(tt, ch, fa)
  def eventStream[F[_]: Dispatcher: Concurrent, A](
    tt: TelltaleChannel,
    ch: Channel[F, A]
  ): VerifiedEpics[Resource[F, *], Stream[F, StreamEvent[A]]] = EventStream(tt, ch)
  def ifF[F[_]: FlatMap, A](cond: F[Boolean])(trueVal: => VerifiedEpics[F, A])(
    falseVal:                     => VerifiedEpics[F, A]
  ): VerifiedEpics[F, A] = IfF(cond, trueVal, falseVal)

  implicit class OpsF[F[_]: Monad, A](v: VerifiedEpics[F, A]) {
    def ap[B](ff: VerifiedEpics[F, A => B]): VerifiedEpics[F, B] = ApplyF[F, A, B](v, ff)

    def flatMap[B](ff: F[A] => VerifiedEpics[F, B]): VerifiedEpics[F, B] =
      new ChannelTracker[F[B]] {
        val fb                                                         = ff(v.run)
        override val systems: Map[TelltaleChannel, Set[RemoteChannel]] =
          merge(v.systems, fb.systems)
        override val run: F[B]                                         = v.run.flatMap(_ => fb.run)
      }

    def productR[B](b: VerifiedEpics[F, B]): VerifiedEpics[F, B] = flatMap(_ => b)

    def *>[B](b: VerifiedEpics[F, B]): VerifiedEpics[F, B] = productR(b)

  }

  implicit class VerifiedRun[F[_]: Async: Parallel, A](v: VerifiedEpics[F, A]) {
    def verifiedRun(connectionTimeout: FiniteDuration): F[A] =
      v.systems
        .map { case (k, v) => EpicsSystem(k, v) }
        .map(_.connectionCheck(connectionTimeout))
        .toList
        .parSequence *> v.run
  }

}
