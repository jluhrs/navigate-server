// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

import cats.{Applicative, MonadThrow}
import cats.effect.{Async, Concurrent, Ref, Temporal}
import cats.effect.kernel.Sync
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import navigate.model.NavigateCommand.*
import navigate.model.{NavigateCommand, NavigateEvent}
import navigate.model.NavigateEvent.{CommandFailure, CommandPaused, CommandStart, CommandSuccess}
import navigate.model.config.NavigateEngineConfiguration
import navigate.model.enums.{DomeMode, ShutterMode}
import navigate.server.tcs.{InstrumentSpecifics, RotatorTrackConfig, SlewConfig, Target, TrackingConfig}
import navigate.stateengine.StateEngine
import NavigateEvent.NullEvent
import fs2.{Pipe, Stream}
import lucuma.core.enums.Site
import lucuma.core.math.Angle
import monocle.{Focus, Lens}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait NavigateEngine[F[_]] {
  val systems: Systems[F]
  def eventStream: Stream[F, NavigateEvent]
  def mcsPark: F[Unit]
  def mcsFollow(enable:                              Boolean): F[Unit]
  def rotStop(useBrakes:                             Boolean): F[Unit]
  def rotPark: F[Unit]
  def rotFollow(enable:                              Boolean): F[Unit]
  def rotMove(angle:                                 Angle): F[Unit]
  def rotTrackingConfig(cfg: RotatorTrackConfig): F[Unit]
  def ecsCarouselMode(
    domeMode:      DomeMode,
    shutterMode:   ShutterMode,
    slitHeight:    Double,
    domeEnable:    Boolean,
    shutterEnable: Boolean
  ): F[Unit]
  def ecsVentGatesMove(gateEast:                     Double, westGate: Double): F[Unit]
  def slew(slewConfig:                               SlewConfig): F[Unit]
  def instrumentSpecifics(instrumentSpecificsParams: InstrumentSpecifics): F[Unit]
  def oiwfsTarget(target:                            Target): F[Unit]
  def oiwfsProbeTracking(config: TrackingConfig): F[Unit]
  def oiwfsPark: F[Unit]
  def oiwfsFollow(enable: Boolean): F[Unit]
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

  private case class NavigateEngineImpl[F[_]: Concurrent: Logger](
    site:    Site,
    systems: Systems[F],
    conf:    NavigateEngineConfiguration,
    engine:  StateEngine[F, State, NavigateEvent]
  ) extends NavigateEngine[F] {
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

    override def slew(slewConfig: SlewConfig): F[Unit] = command(
      engine,
      Slew,
      systems.tcsSouth.slew(slewConfig),
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
  }

  def build[F[_]: Concurrent: Logger](
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
    slewInProgress:                Boolean,
    oiwfsInProgress:               Boolean,
    instrumentSpecificsInProgress: Boolean,
    oiwfsProbeTrackingInProgress:  Boolean,
    oiwfsParkInProgress: Boolean,
    oiwfsFollowInProgress: Boolean,
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
        slewInProgress ||
        oiwfsInProgress ||
        instrumentSpecificsInProgress
        oiwfsProbeTrackingInProgress ||
        oiwfsParkInProgress ||
        oiwfsFollowInProgress
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
    slewInProgress = false,
    oiwfsInProgress = false,
    instrumentSpecificsInProgress = false,
    oiwfsProbeTrackingInProgress = false,
    oiwfsParkInProgress = false,
    oiwfsFollowInProgress = false
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
