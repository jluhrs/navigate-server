// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs
import cats.Parallel
import cats.syntax.all.*
import cats.effect.Async
import mouse.boolean.given
import navigate.server.{ApplyCommandResult, ConnectionTimeout}

import scala.concurrent.duration.FiniteDuration
import navigate.epics.VerifiedEpics.*
import navigate.model.Distance
import navigate.model.enums.{DomeMode, ShutterMode}
import navigate.server.tcs.Target.*
import navigate.server.tcs.TcsEpicsSystem.{ProbeTrackingCommand, TargetCommand, TcsCommands}
import lucuma.core.math.{Angle, Parallax, ProperMotion, RadialVelocity, Wavelength}
import monocle.Getter
import TcsBaseController.{EquinoxDefault, FixedSystem, SystemDefault}

/* This class implements the common TCS commands */
class TcsBaseControllerEpics[F[_]: Async: Parallel](
  tcsEpics: TcsEpicsSystem[F],
  timeout:  FiniteDuration
) extends TcsBaseController[F] {
  override def mcsPark: F[ApplyCommandResult] =
    tcsEpics
      .startCommand(timeout)
      .mcsParkCommand
      .mark
      .post
      .verifiedRun(ConnectionTimeout)

  override def mcsFollow(enable: Boolean): F[ApplyCommandResult] =
    tcsEpics
      .startCommand(timeout)
      .mcsFollowCommand
      .setFollow(enable)
      .post
      .verifiedRun(ConnectionTimeout)

  override def rotStop(useBrakes: Boolean): F[ApplyCommandResult] =
    tcsEpics
      .startCommand(timeout)
      .rotStopCommand
      .setBrakes(useBrakes)
      .post
      .verifiedRun(ConnectionTimeout)

  override def rotPark: F[ApplyCommandResult] =
    tcsEpics
      .startCommand(timeout)
      .rotParkCommand
      .mark
      .post
      .verifiedRun(ConnectionTimeout)

  override def rotFollow(enable: Boolean): F[ApplyCommandResult] =
    tcsEpics
      .startCommand(timeout)
      .rotFollowCommand
      .setFollow(enable)
      .post
      .verifiedRun(ConnectionTimeout)

  override def rotMove(angle: Angle): F[ApplyCommandResult] =
    tcsEpics
      .startCommand(timeout)
      .rotMoveCommand
      .setAngle(angle)
      .post
      .verifiedRun(ConnectionTimeout)

  override def ecsCarouselMode(
    domeMode:      DomeMode,
    shutterMode:   ShutterMode,
    slitHeight:    Double,
    domeEnable:    Boolean,
    shutterEnable: Boolean
  ): F[ApplyCommandResult] =
    tcsEpics
      .startCommand(timeout)
      .ecsCarouselModeCmd
      .setDomeMode(domeMode)
      .ecsCarouselModeCmd
      .setShutterMode(shutterMode)
      .ecsCarouselModeCmd
      .setSlitHeight(slitHeight)
      .ecsCarouselModeCmd
      .setDomeEnable(domeEnable)
      .ecsCarouselModeCmd
      .setShutterEnable(shutterEnable)
      .post
      .verifiedRun(ConnectionTimeout)

  override def ecsVentGatesMove(gateEast: Double, gateWest: Double): F[ApplyCommandResult] =
    tcsEpics
      .startCommand(timeout)
      .ecsVenGatesMoveCmd
      .setVentGateEast(gateEast)
      .ecsVenGatesMoveCmd
      .setVentGateWest(gateWest)
      .post
      .verifiedRun(ConnectionTimeout)

  val DefaultBrightness: Double = 10.0

  protected def setTarget(
    l:      Getter[TcsCommands[F], TargetCommand[F, TcsCommands[F]]],
    target: Target
  ): TcsCommands[F] => TcsCommands[F] = target match {
    case t: AzElTarget      =>
      { (x: TcsCommands[F]) => l.get(x).objectName(t.objectName) }
        .compose[TcsCommands[F]](l.get(_).coordSystem("AzEl"))
        .compose[TcsCommands[F]](l.get(_).coord1(t.coordinates.azimuth.toAngle.toDoubleDegrees))
        .compose[TcsCommands[F]](l.get(_).coord2(t.coordinates.elevation.toAngle.toDoubleDegrees))
        .compose[TcsCommands[F]](l.get(_).brightness(DefaultBrightness))
        .compose[TcsCommands[F]](l.get(_).epoch(2000.0))
        .compose[TcsCommands[F]](l.get(_).equinox(""))
        .compose[TcsCommands[F]](l.get(_).parallax(0.0))
        .compose[TcsCommands[F]](l.get(_).radialVelocity(0.0))
        .compose[TcsCommands[F]](l.get(_).properMotion1(0.0))
        .compose[TcsCommands[F]](l.get(_).properMotion2(0.0))
        .compose[TcsCommands[F]](l.get(_).ephemerisFile(""))
    case t: SiderealTarget  =>
      { (x: TcsCommands[F]) => l.get(x).objectName(t.objectName) }
        .compose[TcsCommands[F]](l.get(_).coordSystem(SystemDefault))
        .compose[TcsCommands[F]](l.get(_).coord1(t.coordinates.ra.toAngle.toDoubleDegrees / 15.0))
        .compose[TcsCommands[F]](l.get(_).coord2(t.coordinates.dec.toAngle.toSignedDoubleDegrees))
        .compose[TcsCommands[F]](l.get(_).brightness(DefaultBrightness))
        .compose[TcsCommands[F]](l.get(_).epoch(t.epoch.epochYear))
        .compose[TcsCommands[F]](l.get(_).equinox(EquinoxDefault))
        .compose[TcsCommands[F]](
          l.get(_).parallax(t.parallax.getOrElse(Parallax.Zero).mas.value.toDouble)
        )
        .compose[TcsCommands[F]](
          l.get(_)
            .radialVelocity(
              t.radialVelocity.getOrElse(RadialVelocity.Zero).toDoubleKilometersPerSecond
            )
        )
        .compose[TcsCommands[F]](
          l.get(_)
            .properMotion1(t.properMotion.getOrElse(ProperMotion.Zero).ra.masy.value.toDouble)
        )
        .compose[TcsCommands[F]](
          l.get(_)
            .properMotion2(t.properMotion.getOrElse(ProperMotion.Zero).dec.masy.value.toDouble)
        )
        .compose[TcsCommands[F]](l.get(_).ephemerisFile(""))
    case t: EphemerisTarget =>
      { (x: TcsCommands[F]) => l.get(x).objectName(t.objectName) }
        .compose[TcsCommands[F]](l.get(_).coordSystem(""))
        .compose[TcsCommands[F]](l.get(_).coord1(0.0))
        .compose[TcsCommands[F]](l.get(_).coord2(0.0))
        .compose[TcsCommands[F]](l.get(_).brightness(DefaultBrightness))
        .compose[TcsCommands[F]](l.get(_).epoch(2000.0))
        .compose[TcsCommands[F]](l.get(_).equinox(""))
        .compose[TcsCommands[F]](l.get(_).parallax(0.0))
        .compose[TcsCommands[F]](l.get(_).radialVelocity(0.0))
        .compose[TcsCommands[F]](l.get(_).properMotion1(0.0))
        .compose[TcsCommands[F]](l.get(_).properMotion2(0.0))
        .compose[TcsCommands[F]](l.get(_).ephemerisFile(t.ephemerisFile))
  }

  protected def setSourceAWalength(w: Wavelength): TcsCommands[F] => TcsCommands[F] =
    (x: TcsCommands[F]) =>
      x.sourceAWavel.wavelength(Wavelength.decimalMicrometers.reverseGet(w).doubleValue)

  protected def setSlewOptions(so: SlewOptions): TcsCommands[F] => TcsCommands[F] =
    (x: TcsCommands[F]) =>
      x.slewOptionsCommand
        .zeroChopThrow(ZeroChopThrow.value(so.zeroChopThrow))
        .slewOptionsCommand
        .zeroSourceOffset(ZeroSourceOffset.value(so.zeroSourceOffset))
        .slewOptionsCommand
        .zeroSourceDiffTrack(ZeroSourceDiffTrack.value(so.zeroSourceDiffTrack))
        .slewOptionsCommand
        .zeroMountOffset(ZeroMountOffset.value(so.zeroMountOffset))
        .slewOptionsCommand
        .zeroMountDiffTrack(ZeroMountDiffTrack.value(so.zeroMountDiffTrack))
        .slewOptionsCommand
        .shortcircuitTargetFilter(ShortcircuitTargetFilter.value(so.shortcircuitTargetFilter))
        .slewOptionsCommand
        .shortcircuitMountFilter(ShortcircuitMountFilter.value(so.shortcircuitMountFilter))
        .slewOptionsCommand
        .resetPointing(ResetPointing.value(so.resetPointing))
        .slewOptionsCommand
        .stopGuide(StopGuide.value(so.stopGuide))
        .slewOptionsCommand
        .zeroGuideOffset(ZeroGuideOffset.value(so.zeroGuideOffset))
        .slewOptionsCommand
        .zeroInstrumentOffset(ZeroInstrumentOffset.value(so.zeroInstrumentOffset))
        .slewOptionsCommand
        .autoparkPwfs1(AutoparkPwfs1.value(so.autoparkPwfs1))
        .slewOptionsCommand
        .autoparkPwfs2(AutoparkPwfs2.value(so.autoparkPwfs2))
        .slewOptionsCommand
        .autoparkOiwfs(AutoparkOiwfs.value(so.autoparkOiwfs))
        .slewOptionsCommand
        .autoparkGems(AutoparkGems.value(so.autoparkGems))
        .slewOptionsCommand
        .autoparkAowfs(AutoparkAowfs.value(so.autoparkAowfs))

  protected def setRotatorIaa(angle: Angle): TcsCommands[F] => TcsCommands[F] =
    (x: TcsCommands[F]) => x.rotatorCommand.iaa(angle)

  protected def setFocusOffset(offset: Distance): TcsCommands[F] => TcsCommands[F] =
    (x: TcsCommands[F]) => x.focusOffsetCommand.focusOffset(offset)

  protected def setOrigin(origin: Origin): TcsCommands[F] => TcsCommands[F] =
    (x: TcsCommands[F]) => x.originCommand.originX(origin.x).originCommand.originY(origin.y)

  override def applyTcsConfig(config: TcsBaseController.TcsConfig): F[ApplyCommandResult] =
    setTarget(Getter[TcsCommands[F], TargetCommand[F, TcsCommands[F]]](_.sourceACmd),
              config.sourceATarget
    ).compose(setSourceAWalength(config.sourceATarget.wavelength))(
      tcsEpics.startCommand(timeout)
    ).post
      .verifiedRun(ConnectionTimeout)

  override def slew(config: SlewConfig): F[ApplyCommandResult] =
    setTarget(Getter[TcsCommands[F], TargetCommand[F, TcsCommands[F]]](_.sourceACmd),
              config.baseTarget
    )
      .compose(setSourceAWalength(config.baseTarget.wavelength))
      .compose(setSlewOptions(config.slewOptions))
      .compose(setRotatorIaa(config.instrumentSpecifics.iaa))
      .compose(setFocusOffset(config.instrumentSpecifics.focusOffset))
      .compose(setOrigin(config.instrumentSpecifics.origin))
      .compose(
        config.oiwfs
          .map(o =>
            setTarget(Getter[TcsCommands[F], TargetCommand[F, TcsCommands[F]]](_.oiwfsTargetCmd),
                      o.target
            )
              .compose(
                setProbeTracking(Getter[TcsCommands[F], ProbeTrackingCommand[F, TcsCommands[F]]](
                                   _.oiwfsProbeTrackingCommand
                                 ),
                                 o.tracking
                )
              )
          )
          .getOrElse(identity[TcsCommands[F]])
      )
      .compose(setRotatorTrackingConfig(config.rotatorTrackConfig))(
        tcsEpics.startCommand(timeout)
      )
      .post
      .verifiedRun(ConnectionTimeout)

  override def instrumentSpecifics(config: InstrumentSpecifics): F[ApplyCommandResult] =
    setRotatorIaa(config.iaa)
      .compose(setFocusOffset(config.focusOffset))
      .compose(setOrigin(config.origin))(
        tcsEpics.startCommand(timeout)
      )
      .post
      .verifiedRun(ConnectionTimeout)

  override def oiwfsTarget(target: Target): F[ApplyCommandResult] =
    setTarget(Getter[TcsCommands[F], TargetCommand[F, TcsCommands[F]]](_.oiwfsTargetCmd), target)(
      tcsEpics.startCommand(timeout)
    ).post
      .verifiedRun(ConnectionTimeout)

  override def rotIaa(angle: Angle): F[ApplyCommandResult] =
    setRotatorIaa(angle)(
      tcsEpics.startCommand(timeout)
    ).post
      .verifiedRun(ConnectionTimeout)

  def setProbeTracking(
    l:      Getter[TcsCommands[F], ProbeTrackingCommand[F, TcsCommands[F]]],
    config: TrackingConfig
  ): TcsCommands[F] => TcsCommands[F] = { (x: TcsCommands[F]) =>
    l.get(x).nodAchopA(config.nodAchopA)
  }
    .compose[TcsCommands[F]](l.get(_).nodAchopB(config.nodAchopB))
    .compose[TcsCommands[F]](l.get(_).nodBchopA(config.nodBchopA))
    .compose[TcsCommands[F]](l.get(_).nodBchopB(config.nodBchopB))

  override def oiwfsProbeTracking(config: TrackingConfig): F[ApplyCommandResult] =
    setProbeTracking(
      Getter[TcsCommands[F], ProbeTrackingCommand[F, TcsCommands[F]]](_.oiwfsProbeTrackingCommand),
      config
    )(
      tcsEpics.startCommand(timeout)
    ).post
      .verifiedRun(ConnectionTimeout)

  override def oiwfsPark: F[ApplyCommandResult] =
    tcsEpics
      .startCommand(timeout)
      .oiwfsProbeCommands
      .park
      .mark
      .post
      .verifiedRun(ConnectionTimeout)

  override def oiwfsFollow(enable: Boolean): F[ApplyCommandResult] =
    tcsEpics
      .startCommand(timeout)
      .oiwfsProbeCommands
      .follow
      .setFollow(enable)
      .post
      .verifiedRun(ConnectionTimeout)

  def setRotatorTrackingConfig(cfg: RotatorTrackConfig): TcsCommands[F] => TcsCommands[F] =
    (x: TcsCommands[F]) =>
      cfg.mode match {
        case RotatorTrackingMode.Fixed    =>
          x.rotMoveCommand
            .setAngle(cfg.ipa)
            .rotatorCommand
            .ipa(cfg.ipa)
            .rotatorCommand
            .system(FixedSystem)
        case RotatorTrackingMode.Tracking =>
          x.rotatorCommand
            .ipa(cfg.ipa)
            .rotatorCommand
            .system(SystemDefault)
            .rotatorCommand
            .equinox(EquinoxDefault)
      }

  override def rotTrackingConfig(cfg: RotatorTrackConfig): F[ApplyCommandResult] =
    setRotatorTrackingConfig(cfg)(tcsEpics.startCommand(timeout)).post
      .verifiedRun(ConnectionTimeout)

  override def enableGuide(config: TelescopeGuideConfig): F[ApplyCommandResult] = {
    val m1 = (x: TcsCommands[F]) =>
      config.m1Guide match {
        case M1GuideConfig.M1GuideOff        => x.m1GuideCommand.state(false)
        case M1GuideConfig.M1GuideOn(source) =>
          x.m1GuideCommand
            .state(true)
            .m1GuideConfigCommand
            .source(source.tag)
            .m1GuideConfigCommand
            .weighting("none")
            .m1GuideConfigCommand
            .frames(1)
            .m1GuideConfigCommand
            .filename("")
      }

    config.m2Guide match {
      case M2GuideConfig.M2GuideOff               =>
        m1(tcsEpics.startCommand(timeout)).m2GuideCommand
          .state(false)
          .m2GuideModeCommand
          .coma(false)
          .mountGuideCommand
          .mode(config.mountGuide)
          .mountGuideCommand
          .source("SCS")
          .post
          .verifiedRun(ConnectionTimeout)
      case M2GuideConfig.M2GuideOn(coma, sources) =>
        val requireReset: Boolean = true // Use current state
        val beams                 = List("A", "B")
        sources
          .flatMap(x => beams.map(y => (x, y)))
          .foldLeft(
            requireReset.fold(
              tcsEpics
                .startCommand(timeout)
                .m2GuideResetCommand
                .mark
                .post
                .verifiedRun(ConnectionTimeout),
              ApplyCommandResult.Completed.pure[F]
            )
          ) { case (t, (src, beam)) =>
            t.flatMap { r =>
              // Set tip-tilt guide for each source on each beam
              // TCC adds a delay between each call. Is it necessary?
              (r === ApplyCommandResult.Completed).fold(
                tcsEpics
                  .startCommand(timeout)
                  .m2GuideConfigCommand
                  .source(src.tag)
                  .m2GuideConfigCommand
                  .sampleFreq(200.0)
                  .m2GuideConfigCommand
                  .filter("raw")
                  .m2GuideConfigCommand
                  .beam(beam)
                  .m2GuideConfigCommand
                  .reset(false)
                  .post
                  .verifiedRun(ConnectionTimeout),
                r.pure[F]
              )
            }.flatMap { r =>
              (r === ApplyCommandResult.Completed).fold(
                m1(tcsEpics.startCommand(timeout)).m2GuideCommand
                  .state(true)
                  .m2GuideModeCommand
                  .coma(coma)
                  .mountGuideCommand
                  .mode(config.mountGuide)
                  .mountGuideCommand
                  .source("SCS")
                  .post
                  .verifiedRun(ConnectionTimeout),
                ApplyCommandResult.Completed.pure[F]
              )
            }
          }
    }
  }

  override def disableGuide: F[ApplyCommandResult] = tcsEpics
    .startCommand(timeout)
    .m1GuideCommand
    .state(false)
    .m2GuideCommand
    .state(false)
    .mountGuideCommand
    .mode(false)
    .post
    .verifiedRun(ConnectionTimeout)

}
