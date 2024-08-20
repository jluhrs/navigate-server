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
import lucuma.core.enums.MountGuideOption
import lucuma.core.enums.Site
import lucuma.core.math.Angle
import lucuma.core.model.GuideConfig
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.TimeSpan
import monocle.Focus
import monocle.Lens
import monocle.syntax.all.focus
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
import navigate.server.tcs.GuidersQualityValues
import navigate.server.tcs.InstrumentSpecifics
import navigate.server.tcs.RotatorTrackConfig
import navigate.server.tcs.SlewOptions
import navigate.server.tcs.Target
import navigate.server.tcs.TcsBaseController.TcsConfig
import navigate.server.tcs.TelescopeState
import navigate.server.tcs.TrackingConfig
import navigate.stateengine.Handler
import navigate.stateengine.StateEngine
import navigate.stateengine.StateEngine.Event
import org.http4s.*
import org.http4s.EntityDecoder
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
  def scsFollow(enable:                              Boolean): F[Unit]
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
  def swapTarget(target:                             Target): F[Unit]
  def getGuideState: F[GuideState]
  def getGuidersQuality: F[GuidersQualityValues]
  def getTelescopeState: F[TelescopeState]
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
      simpleCommand(engine, McsPark, systems.tcsCommon.mcsPark, Focus[State](_.mcsParkInProgress))

    override def mcsFollow(enable: Boolean): F[Unit] =
      simpleCommand(engine,
                    McsFollow(enable),
                    systems.tcsCommon.mcsFollow(enable),
                    Focus[State](_.mcsFollowInProgress)
      )

    override def scsFollow(enable: Boolean): F[Unit] =
      simpleCommand(engine,
                    ScsFollow(enable),
                    systems.tcsCommon.scsFollow(enable),
                    Focus[State](_.scsFollowInProgress)
      )

    override def rotStop(useBrakes: Boolean): F[Unit] =
      simpleCommand(engine,
                    CrcsStop(useBrakes),
                    systems.tcsCommon.rotStop(useBrakes),
                    Focus[State](_.rotStopInProgress)
      )

    override def rotPark: F[Unit] =
      simpleCommand(engine, CrcsPark, systems.tcsCommon.rotPark, Focus[State](_.rotParkInProgress))

    override def rotFollow(enable: Boolean): F[Unit] =
      simpleCommand(engine,
                    CrcsFollow(enable),
                    systems.tcsCommon.rotFollow(enable),
                    Focus[State](_.rotFollowInProgress)
      )

    override def rotMove(angle: Angle): F[Unit] =
      simpleCommand(engine,
                    CrcsMove(angle),
                    systems.tcsCommon.rotMove(angle),
                    Focus[State](_.rotMoveInProgress)
      )

    override def ecsCarouselMode(
      domeMode:      DomeMode,
      shutterMode:   ShutterMode,
      slitHeight:    Double,
      domeEnable:    Boolean,
      shutterEnable: Boolean
    ): F[Unit] = simpleCommand(
      engine,
      EcsCarouselMode(domeMode, shutterMode, slitHeight, domeEnable, shutterEnable),
      systems.tcsCommon.ecsCarouselMode(domeMode,
                                        shutterMode,
                                        slitHeight,
                                        domeEnable,
                                        shutterEnable
      ),
      Focus[State](_.ecsDomeModeInProgress)
    )

    // TODO
    override def ecsVentGatesMove(gateEast: Double, westGate: Double): F[Unit] = Applicative[F].unit

    override def tcsConfig(config: TcsConfig): F[Unit] = simpleCommand(
      engine,
      TcsConfigure,
      systems.tcsCommon.tcsConfig(config),
      Focus[State](_.tcsConfigInProgress)
    )

    override def slew(slewOptions: SlewOptions, tcsConfig: TcsConfig): F[Unit] = simpleCommand(
      engine,
      Slew,
      systems.tcsCommon.slew(slewOptions, tcsConfig),
      Focus[State](_.slewInProgress)
    )

    override def swapTarget(target: Target): F[Unit] = simpleCommand(
      engine,
      SwapTarget,
      systems.tcsCommon.swapTarget(target),
      Focus[State](_.swapInProgress)
    )

    override def instrumentSpecifics(instrumentSpecificsParams: InstrumentSpecifics): F[Unit] =
      simpleCommand(
        engine,
        InstSpecifics,
        systems.tcsCommon.instrumentSpecifics(instrumentSpecificsParams),
        Focus[State](_.instrumentSpecificsInProgress)
      )

    override def oiwfsTarget(target: Target): F[Unit] = simpleCommand(
      engine,
      OiwfsTarget,
      systems.tcsCommon.oiwfsTarget(target),
      Focus[State](_.oiwfsInProgress)
    )

    override def oiwfsProbeTracking(config: TrackingConfig): F[Unit] = simpleCommand(
      engine,
      OiwfsProbeTracking,
      systems.tcsCommon.oiwfsProbeTracking(config),
      Focus[State](_.oiwfsProbeTrackingInProgress)
    )

    override def oiwfsPark: F[Unit] = simpleCommand(
      engine,
      OiwfsPark,
      systems.tcsCommon.oiwfsPark,
      Focus[State](_.oiwfsParkInProgress)
    )

    override def oiwfsFollow(enable: Boolean): F[Unit] = simpleCommand(
      engine,
      OiwfsFollow(enable),
      systems.tcsCommon.oiwfsFollow(enable),
      Focus[State](_.oiwfsFollowInProgress)
    )

    override def rotTrackingConfig(cfg: navigate.server.tcs.RotatorTrackConfig): F[Unit] =
      simpleCommand(
        engine,
        RotatorTrackingConfig,
        systems.tcsCommon.rotTrackingConfig(cfg),
        Focus[State](_.rotTrackingConfigInProgress)
      )

    override def enableGuide(config: TelescopeGuideConfig): F[Unit] =
      command(
        engine,
        EnableGuide,
        transformCommand(
          EnableGuide,
          Handler.get[F, State, ApplyCommandResult].flatMap { st =>
            Handler.replace(st.focus(_.guideConfig.tcsGuide).replace(config)) *>
              Handler.fromStream(
                Stream.eval(
                  systems.tcsCommon.enableGuide(config)
                )
              )
          },
          Focus[State](_.enableGuide)
        ),
        Focus[State](_.enableGuide)
      ) *> postTelescopeGuideConfig(GuideConfig(config, None))

    override def disableGuide: F[Unit] = command(
      engine,
      DisableGuide,
      transformCommand(
        DisableGuide,
        Handler.get[F, State, ApplyCommandResult].flatMap { st =>
          Handler.replace[F, State, ApplyCommandResult](
            st.focus(_.guideConfig.tcsGuide)
              .replace(
                TelescopeGuideConfig(MountGuideOption.MountGuideOff,
                                     M1GuideConfig.M1GuideOff,
                                     M2GuideConfig.M2GuideOff,
                                     None,
                                     None
                )
              )
          ) *>
            Handler.fromStream(
              Stream.eval(
                systems.tcsCommon.disableGuide
              )
            )
        },
        Focus[State](_.disableGuide)
      ),
      Focus[State](_.disableGuide)
    ) *> postTelescopeGuideConfig(GuideConfig.defaultGuideConfig)

    override def oiwfsObserve(period: TimeSpan): F[Unit] = command(
      engine,
      OiwfsObserve,
      transformCommand(
        OiwfsObserve,
        Handler.get[F, State, ApplyCommandResult].flatMap { st =>
          Handler.fromStream(
            Stream.eval(
              systems.tcsCommon.oiwfsObserve(period)
            )
          )
        },
        Focus[State](_.oiwfsObserve)
      ),
      Focus[State](_.oiwfsObserve)
    )

    override def oiwfsStopObserve: F[Unit] = simpleCommand(
      engine,
      OiwfsStopObserve,
      systems.tcsCommon.oiwfsStopObserve,
      Focus[State](_.oiwfsStopObserve)
    )

    override def getGuideState: F[GuideState] = systems.tcsCommon.getGuideState

    override def getGuidersQuality: F[GuidersQualityValues] = systems.tcsCommon.getGuideQuality

    override def getTelescopeState: F[TelescopeState] = systems.tcsCommon.getTelescopeState
  }

  def build[F[_]: Concurrent: Temporal: Logger](
    site:    Site,
    systems: Systems[F],
    conf:    NavigateEngineConfiguration
  ): F[NavigateEngine[F]] = StateEngine
    .build[F, State, NavigateEvent]
    .map(NavigateEngineImpl[F](site, systems, conf, _))

  case class WfsConfigState(
    period:          Option[TimeSpan],
    configuredForQl: Option[Boolean]
  )

  case class State(
    mcsParkInProgress:             Boolean,
    mcsFollowInProgress:           Boolean,
    scsFollowInProgress:           Boolean,
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
    oiwfsStopObserve:              Boolean,
    guideConfig:                   GuideConfig,
    swapInProgress:                Boolean
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
        oiwfsStopObserve ||
        swapInProgress
  }

  val startState: State = State(
    mcsParkInProgress = false,
    mcsFollowInProgress = false,
    scsFollowInProgress = false,
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
    oiwfsStopObserve = false,
    guideConfig = GuideConfig.defaultGuideConfig,
    swapInProgress = false
  )

  /**
   * This is used for simple commands, just an F that executes the command when evaluated, producing
   * a result. The method takes care of setting/releasing guard flags in the global state and
   * surrounding the evaluation with log messages. An important distinction with the new command
   * method is that here, the command code does not have access to the global state.
   *
   * @param engine
   *   The state machine.
   * @param cmdType:
   *   The command type, used for logs.
   * @param cmd:
   *   The actual command, wrapped in effect F.
   * @param f:
   *   Lens to the command guard flag in the global state.
   * @tparam F:
   *   Type of effect that wraps the command execution.
   * @return
   *   Effect that, when evaluated, will schedule the execution of the command in the state machine.
   */
  private def simpleCommand[F[_]: MonadThrow: Logger](
    engine:  StateEngine[F, State, NavigateEvent],
    cmdType: NavigateCommand,
    cmd:     F[ApplyCommandResult],
    f:       Lens[State, Boolean]
  ): F[Unit] = engine.offer(
    engine.getState.flatMap { st =>
      if (!st.tcsActionInProgress) {
        engine
          .modifyState(f.replace(true))
          .as(CommandStart(cmdType).some) <*
          Handler
            .fromStream[F, State, Event[F, State, NavigateEvent]](
              Stream.eval[F, Event[F, State, NavigateEvent]](
                Logger[F].info(s"Start command ${cmdType.name}") *>
                  cmd.attempt
                    .map(cmdResultToNavigateEvent(cmdType, _))
                    .flatMap(x =>
                      Logger[F]
                        .info(s"Command ${cmdType.name} ended with result $x")
                        .as(Event(engine.modifyState(f.replace(false)).as(x.some)))
                    )
              )
            )
      } else {
        engine.lift(
          Logger[F]
            .warn(s"Cannot execute command ${cmdType.name} because a TCS command is in progress.")
            .as(NullEvent)
        ) *> engine.void
      }
    }
  )

  /**
   * Similar to simple command, but here the command is a Handler, executed in the state machine.
   * This gives the command access to the global state, and allows it to have several stages.
   * @param engine
   *   The state machine.
   * @param cmdType:
   *   The command type, used for logs.
   * @param cmd:
   *   The actual command, wrapped in a Handle. The command is responsible for generating the Stream
   *   for later scheduling, although there is a helper method for that: transformCommand
   * @param f:
   *   Lens to the command guard flag in the global state.
   * @tparam F:
   *   Type of effect that wraps the command execution.
   * @return
   *   Effect that, when evaluated, will schedule the execution of the command in the state machine.
   */
  private def command[F[_]: MonadThrow: Logger](
    engine:  StateEngine[F, State, NavigateEvent],
    cmdType: NavigateCommand,
    cmd:     Handler[F, State, Event[F, State, NavigateEvent], Unit],
    f:       Lens[State, Boolean]
  ): F[Unit] = engine.offer(
    engine.getState.flatMap { st =>
      if (!st.tcsActionInProgress) {
        engine
          .modifyState(f.replace(true))
          .as(CommandStart(cmdType).some) <* cmd
      } else {
        engine.lift(
          Logger[F]
            .warn(s"Cannot execute command ${cmdType.name} because a TCS command is in progress.")
            .as(NullEvent)
        ) *> engine.void
      }
    }
  )

  private def cmdResultToNavigateEvent(
    cmdType: NavigateCommand,
    result:  Either[Throwable, ApplyCommandResult]
  ): NavigateEvent =
    result match {
      case Right(ApplyCommandResult.Paused)    => CommandPaused(cmdType)
      case Right(ApplyCommandResult.Completed) => CommandSuccess(cmdType)
      case Left(e)                             =>
        CommandFailure(cmdType, s"${cmdType.name} command failed with error: ${e.getMessage}")
    }

  private def transformCommand[F[_]: MonadThrow: Logger](
    cmdType: NavigateCommand,
    cmd:     Handler[F, State, ApplyCommandResult, Unit],
    f:       Lens[State, Boolean]
  ): Handler[F, State, Event[F, State, NavigateEvent], Unit] =
    Handler(cmd.run.map { ret =>
      Handler.RetVal(
        ret.v,
        ret.s.map { ss =>
          Stream.eval(
            Logger[F].info(s"Start command ${cmdType.name}")
          ) *>
            ss.attempt.map(cmdResultToNavigateEvent(cmdType, _)).flatMap { x =>
              Stream.eval(
                Logger[F]
                  .info(s"Command ${cmdType.name} ended with result $x")
                  .as(
                    Event(
                      Handler
                        .modify[F, State, Event[F, State, NavigateEvent]](f.replace(false))
                        .as(x.some)
                    )
                  )
              )
            }
        }
      )
    })

}
