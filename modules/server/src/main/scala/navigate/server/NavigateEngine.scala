// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

import cats.Applicative
import cats.MonadThrow
import cats.effect.Async
import cats.effect.Concurrent
import cats.effect.Ref
import cats.effect.Temporal
import cats.effect.kernel.Sync
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import io.circe.syntax.*
import lucuma.core.enums.Site
import lucuma.core.math.Angle
import lucuma.core.model.GuideConfig
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.TimeSpan
import monocle.Focus
import monocle.Lens
import navigate.model.NavigateCommand
import navigate.model.NavigateCommand.*
import navigate.model.NavigateEvent
import navigate.model.NavigateEvent.CommandFailure
import navigate.model.NavigateEvent.CommandPaused
import navigate.model.NavigateEvent.CommandStart
import navigate.model.NavigateEvent.CommandSuccess
import navigate.model.config.ControlStrategy
import navigate.model.config.NavigateEngineConfiguration
import navigate.model.enums.DomeMode
import navigate.model.enums.ShutterMode
import navigate.server.tcs.GuideState
import navigate.server.tcs.InstrumentSpecifics
import navigate.server.tcs.RotatorTrackConfig
import navigate.server.tcs.SlewOptions
import navigate.server.tcs.Target
import navigate.server.tcs.TcsBaseController.TcsConfig
import navigate.server.tcs.TrackingConfig
import navigate.stateengine.StateEngine
import org.http4s.EntityDecoder
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.middleware.Retry
import org.http4s.client.middleware.RetryPolicy
import org.http4s.dsl.io.*
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import NavigateEvent.NullEvent

trait NavigateEngine[F[_]] {
  val systems: Systems[F]
  def eventStream: Stream[F, NavigateEvent]
  def mcsPark: F[Unit]
  def mcsFollow(enable:                              Boolean): F[Unit]
  def rotStop(useBrakes:                             Boolean): F[Unit]
  def rotPark: F[Unit]
  def rotFollow(enable:                              Boolean): F[Unit]
  def rotMove(angle:                                 Angle): F[Unit]
  def rotTrackingConfig(cfg:                         RotatorTrackConfig): F[Unit]
  def ecsCarouselMode(
    domeMode:      DomeMode,
    shutterMode:   ShutterMode,
    slitHeight:    Double,
    domeEnable:    Boolean,
    shutterEnable: Boolean
  ): F[Unit]
  def ecsVentGatesMove(gateEast:                     Double, westGate:       Double): F[Unit]
  def tcsConfig(config:                              TcsConfig): F[Unit]
  def slew(slewOptions:                              SlewOptions, tcsConfig: TcsConfig): F[Unit]
  def instrumentSpecifics(instrumentSpecificsParams: InstrumentSpecifics): F[Unit]
  def oiwfsTarget(target:                            Target): F[Unit]
  def oiwfsProbeTracking(config:                     TrackingConfig): F[Unit]
  def oiwfsPark: F[Unit]
  def oiwfsFollow(enable:                            Boolean): F[Unit]
  def enableGuide(config:                            TelescopeGuideConfig): F[Unit]
  def disableGuide: F[Unit]
  def oiwfsObserve(period:                           TimeSpan): F[Unit]
  def oiwfsStopObserve: F[Unit]
  def getGuideState: F[GuideState]
}

object NavigateEngine {

  def failIfNoEmitsWithin[F[_]: Async, A](
    timeout: FiniteDuration,
    msg:     String
  ): Pipe[F, A, A] = in => {
    import scala.concurrent.TimeoutException
    val now = Temporal[F].realTime

    Stream.eval(now.flatMap(Ref[F].of)).flatMap { lastActivityAt =>
      in.evalTap(_ => now.flatMap(lastActivityAt.set))
        .concurrently {
          Stream.repeatEval {
            (now, lastActivityAt.get)
              .mapN(_ - _)
              .flatMap { elapsed =>
                val t = timeout - elapsed

                Sync[F]
                  .raiseError[Unit](new TimeoutException(msg))
                  .whenA(t <= 0.nanos) >> Temporal[F].sleep(t)
              }
          }
        }
    }
  }

  private case class NavigateEngineImpl[F[_]: Concurrent: Temporal: Logger](
    site:    Site,
    systems: Systems[F],
    conf:    NavigateEngineConfiguration,
    engine:  StateEngine[F, State, NavigateEvent]
  ) extends NavigateEngine[F]
      with Http4sClientDsl[F] {

    // We want to support some retries to observe
    private val clientWithRetry = {
      val max             = 4
      var attemptsCounter = 1
      val policy          = RetryPolicy[F] { (attempts: Int) =>
        if (attempts >= max) None
        else {
          attemptsCounter = attemptsCounter + 1
          10.milliseconds.some
        }
      }
      Retry[F](policy)(systems.client)
    }

    private def postTelescopeGuideConfig(gc: GuideConfig): F[Unit] = {
      val postRequest: Request[F] =
        POST(
          gc.asJson,
          conf.observe / "api" / "observe" / "guide"
        )

      // Update guide state in observe if not simulated
      (Logger[F].info(s"Update guide state in observe") *>
        clientWithRetry
          .expect[String](postRequest)
          .void
          .handleErrorWith(r => Logger[F].error(r)("Error posting guide configuration to observe")))
        .whenA(conf.systemControl.observe === ControlStrategy.FullControl)
    }

    override def eventStream: Stream[F, NavigateEvent] =
      engine.process(startState)

    override def mcsPark: F[Unit] =
      command(engine, McsPark, systems.tcsSouth.mcsPark, Focus[State](_.mcsParkInProgress))

    override def mcsFollow(enable: Boolean): F[Unit] =
      command(engine,
              McsFollow(enable),
              systems.tcsSouth.mcsFollow(enable),
              Focus[State](_.mcsFollowInProgress)
      )

    override def rotStop(useBrakes: Boolean): F[Unit] =
      command(engine,
              CrcsStop(useBrakes),
              systems.tcsSouth.rotStop(useBrakes),
              Focus[State](_.rotStopInProgress)
      )

    override def rotPark: F[Unit] =
      command(engine, CrcsPark, systems.tcsSouth.rotPark, Focus[State](_.rotParkInProgress))

    override def rotFollow(enable: Boolean): F[Unit] =
      command(engine,
              CrcsFollow(enable),
              systems.tcsSouth.rotFollow(enable),
              Focus[State](_.rotFollowInProgress)
      )

    override def rotMove(angle: Angle): F[Unit] =
      command(engine,
              CrcsMove(angle),
              systems.tcsSouth.rotMove(angle),
              Focus[State](_.rotMoveInProgress)
      )

    override def ecsCarouselMode(
      domeMode:      DomeMode,
      shutterMode:   ShutterMode,
      slitHeight:    Double,
      domeEnable:    Boolean,
      shutterEnable: Boolean
    ): F[Unit] = command(
      engine,
      EcsCarouselMode(domeMode, shutterMode, slitHeight, domeEnable, shutterEnable),
      systems.tcsSouth.ecsCarouselMode(domeMode,
                                       shutterMode,
                                       slitHeight,
                                       domeEnable,
                                       shutterEnable
      ),
      Focus[State](_.ecsDomeModeInProgress)
    )

    // TODO
    override def ecsVentGatesMove(gateEast: Double, westGate: Double): F[Unit] = Applicative[F].unit

    override def tcsConfig(config: TcsConfig): F[Unit] = command(
      engine,
      TcsConfigure,
      systems.tcsSouth.tcsConfig(config),
      Focus[State](_.tcsConfigInProgress)
    )

    override def slew(slewOptions: SlewOptions, tcsConfig: TcsConfig): F[Unit] = command(
      engine,
      Slew,
      systems.tcsSouth.slew(slewOptions, tcsConfig),
      Focus[State](_.slewInProgress)
    )

    override def instrumentSpecifics(instrumentSpecificsParams: InstrumentSpecifics): F[Unit] =
      command(
        engine,
        InstSpecifics,
        systems.tcsSouth.instrumentSpecifics(instrumentSpecificsParams),
        Focus[State](_.instrumentSpecificsInProgress)
      )

    override def oiwfsTarget(target: Target): F[Unit] = command(
      engine,
      OiwfsTarget,
      systems.tcsSouth.oiwfsTarget(target),
      Focus[State](_.oiwfsInProgress)
    )

    override def oiwfsProbeTracking(config: TrackingConfig): F[Unit] = command(
      engine,
      OiwfsProbeTracking,
      systems.tcsSouth.oiwfsProbeTracking(config),
      Focus[State](_.oiwfsProbeTrackingInProgress)
    )

    override def oiwfsPark: F[Unit] = command(
      engine,
      OiwfsPark,
      systems.tcsSouth.oiwfsPark,
      Focus[State](_.oiwfsParkInProgress)
    )

    override def oiwfsFollow(enable: Boolean): F[Unit] = command(
      engine,
      OiwfsFollow(enable),
      systems.tcsSouth.oiwfsFollow(enable),
      Focus[State](_.oiwfsFollowInProgress)
    )

    override def rotTrackingConfig(cfg: navigate.server.tcs.RotatorTrackConfig): F[Unit] = command(
      engine,
      RotatorTrackingConfig,
      systems.tcsSouth.rotTrackingConfig(cfg),
      Focus[State](_.rotTrackingConfigInProgress)
    )

    override def enableGuide(config: TelescopeGuideConfig): F[Unit] = command(
      engine,
      EnableGuide,
      systems.tcsSouth.enableGuide(config),
      Focus[State](_.enableGuide)
    ) *> postTelescopeGuideConfig(GuideConfig(config, None))

    override def disableGuide: F[Unit] = command(
      engine,
      DisableGuide,
      systems.tcsSouth.disableGuide,
      Focus[State](_.disableGuide)
    ) *> postTelescopeGuideConfig(GuideConfig.defaultGuideConfig)

    override def oiwfsObserve(period: TimeSpan): F[Unit] = command(
      engine,
      OiwfsObserve,
      systems.tcsSouth.oiwfsObserve(period, false),
      Focus[State](_.oiwfsObserve)
    )

    override def oiwfsStopObserve: F[Unit] = command(
      engine,
      OiwfsStopObserve,
      systems.tcsSouth.oiwfsStopObserve,
      Focus[State](_.oiwfsStopObserve)
    )

    override def getGuideState: F[GuideState] = systems.tcsSouth.getGuideState
  }

  def build[F[_]: Concurrent: Temporal: Logger](
    site:    Site,
    systems: Systems[F],
    conf:    NavigateEngineConfiguration
  ): F[NavigateEngine[F]] = StateEngine
    .build[F, State, NavigateEvent]
    .map(NavigateEngineImpl[F](site, systems, conf, _))

  case class State(
    mcsParkInProgress:             Boolean,
    mcsFollowInProgress:           Boolean,
    rotStopInProgress:             Boolean,
    rotParkInProgress:             Boolean,
    rotFollowInProgress:           Boolean,
    rotMoveInProgress:             Boolean,
    rotTrackingConfigInProgress:   Boolean,
    ecsDomeModeInProgress:         Boolean,
    ecsVentGateMoveInProgress:     Boolean,
    tcsConfigInProgress:           Boolean,
    slewInProgress:                Boolean,
    oiwfsInProgress:               Boolean,
    instrumentSpecificsInProgress: Boolean,
    oiwfsProbeTrackingInProgress:  Boolean,
    oiwfsParkInProgress:           Boolean,
    oiwfsFollowInProgress:         Boolean,
    enableGuide:                   Boolean,
    disableGuide:                  Boolean,
    oiwfsObserve:                  Boolean,
    oiwfsStopObserve:              Boolean
  ) {
    lazy val tcsActionInProgress: Boolean =
      mcsParkInProgress ||
        mcsFollowInProgress ||
        rotStopInProgress ||
        rotParkInProgress ||
        rotFollowInProgress ||
        rotMoveInProgress ||
        rotTrackingConfigInProgress ||
        ecsDomeModeInProgress ||
        ecsVentGateMoveInProgress ||
        tcsConfigInProgress ||
        slewInProgress ||
        oiwfsInProgress ||
        instrumentSpecificsInProgress ||
        oiwfsProbeTrackingInProgress ||
        oiwfsParkInProgress ||
        oiwfsFollowInProgress ||
        enableGuide ||
        disableGuide ||
        oiwfsObserve ||
        oiwfsStopObserve
  }

  val startState: State = State(
    mcsParkInProgress = false,
    mcsFollowInProgress = false,
    rotStopInProgress = false,
    rotParkInProgress = false,
    rotFollowInProgress = false,
    rotMoveInProgress = false,
    rotTrackingConfigInProgress = false,
    ecsDomeModeInProgress = false,
    ecsVentGateMoveInProgress = false,
    tcsConfigInProgress = false,
    slewInProgress = false,
    oiwfsInProgress = false,
    instrumentSpecificsInProgress = false,
    oiwfsProbeTrackingInProgress = false,
    oiwfsParkInProgress = false,
    oiwfsFollowInProgress = false,
    enableGuide = false,
    disableGuide = false,
    oiwfsObserve = false,
    oiwfsStopObserve = false
  )

  private def command[F[_]: MonadThrow: Logger](
    engine:  StateEngine[F, State, NavigateEvent],
    cmdType: NavigateCommand,
    cmd:     F[ApplyCommandResult],
    f:       Lens[State, Boolean]
  ): F[Unit] = engine.offer(
    engine.getState.flatMap { st =>
      if (!st.tcsActionInProgress) {
        engine
          .modifyState(f.replace(true))
          .as(CommandStart(cmdType)) *>
          engine.lift(
            Logger[F].info(s"Start command ${cmdType.name}") *>
              cmd.attempt
                .map {
                  case Right(ApplyCommandResult.Paused)    => CommandPaused(cmdType)
                  case Right(ApplyCommandResult.Completed) => CommandSuccess(cmdType)
                  case Left(e)                             =>
                    CommandFailure(cmdType,
                                   s"${cmdType.name} command failed with error: ${e.getMessage}"
                    )
                }
                .flatMap { x =>
                  Logger[F].info(s"Command ${cmdType.name} ended with result $x").as(x)
                }
          ) <*
          engine.modifyState(f.replace(false))
      } else {
        engine.lift(
          Logger[F]
            .warn(s"Cannot execute command ${cmdType.name} because a TCS command is in progress.")
            .as(NullEvent)
        ) *> engine.void
      }
    }
  )

}
