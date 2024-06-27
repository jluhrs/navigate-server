// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Monad
import cats.Parallel
import cats.effect.Resource
import cats.effect.Temporal
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.refined.*
import navigate.epics.EpicsService
import navigate.epics.VerifiedEpics
import navigate.epics.VerifiedEpics.VerifiedEpics
import navigate.epics.VerifiedEpics._
import navigate.server.ApplyCommandResult
import navigate.server.acm.CadDirective
import navigate.server.acm.ParameterList.*

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

trait WfsEpicsSystem[F[_]] {
  def startCommand(timeout: FiniteDuration): WfsEpicsSystem.WfsCommands[F]
}

object WfsEpicsSystem {

  trait WfsCommands[F[_]] {
    def post: VerifiedEpics[F, F, ApplyCommandResult]
    val gains: GainsCommand[F, WfsCommands[F]]
  }

  trait GainsCommand[F[_], +S] {
    def setTipGain(v:   Double): S
    def setTiltGain(v:  Double): S
    def setFocusGain(v: Double): S
  }

  private[tcs] def buildSystem[F[_]: Monad: Temporal: Parallel](
    channels: WfsChannels[F]
  ): WfsEpicsSystem[F] = new WfsEpicsSystem[F] {
    override def startCommand(timeout: FiniteDuration): WfsCommands[F] =
      new WfsCommandsImpl[F](
        new WfsEpicsImpl[F](channels),
        timeout
      )
  }

  def build[F[_]: Dispatcher: Temporal: Parallel](
    service:             EpicsService[F],
    sysName:             String,
    top:                 NonEmptyString,
    telltaleChannelName: NonEmptyString = "health.VAL".refined
  ): Resource[F, WfsEpicsSystem[F]] = for {
    channels <- WfsChannels.build(service, sysName, top, telltaleChannelName)
  } yield buildSystem(channels)

  trait WfsEpics[F[_]] {
    def post(timeout: FiniteDuration): VerifiedEpics[F, F, ApplyCommandResult]
    val gainsCmd: Command3Channels[F, Double, Double, Double]
  }

  // There is no CAR associated these commands. Without feedback, we can only wait and pray.
  val commandWaitTime: FiniteDuration = FiniteDuration(500, TimeUnit.MILLISECONDS)

  class WfsEpicsImpl[F[_]: Monad: Temporal](
    channels: WfsChannels[F]
  ) extends WfsEpics[F] {
    override def post(timeout: FiniteDuration): VerifiedEpics[F, F, ApplyCommandResult] =
      VerifiedEpics.writeChannel(channels.telltale, channels.gainsDir)(
        CadDirective.START.pure[F]
      ) *>
        VerifiedEpics.writeChannel(channels.telltale, channels.resetDir)(
          CadDirective.START.pure[F]
        ) *>
        VerifiedEpics.liftF(Temporal[F].sleep(commandWaitTime).as(ApplyCommandResult.Completed))

    override val gainsCmd: Command3Channels[F, Double, Double, Double] =
      Command3Channels[F, Double, Double, Double](
        channels.telltale,
        channels.tipGain,
        channels.tiltGain,
        channels.focusGain
      )
  }

  private class WfsCommandsImpl[F[_]: Monad: Parallel](
    wfsEpics: WfsEpics[F],
    timeout:  FiniteDuration,
    params:   ParameterList[F] = List.empty[VerifiedEpics[F, F, Unit]]
  ) extends WfsCommands[F] {
    private def addParam(p: VerifiedEpics[F, F, Unit]): WfsCommands[F] =
      WfsCommandsImpl(wfsEpics, timeout, params :+ p)

    override def post: VerifiedEpics[F, F, ApplyCommandResult] =
      params.compile *> wfsEpics.post(timeout)

    override val gains: GainsCommand[F, WfsCommands[F]] = new GainsCommand[F, WfsCommands[F]] {
      override def setTipGain(v: Double): WfsCommands[F] = addParam(wfsEpics.gainsCmd.setParam1(v))

      override def setTiltGain(v: Double): WfsCommands[F] = addParam(wfsEpics.gainsCmd.setParam2(v))

      override def setFocusGain(v: Double): WfsCommands[F] = addParam(
        wfsEpics.gainsCmd.setParam3(v)
      )
    }
  }

}
