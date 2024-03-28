// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Parallel
import cats.effect.Async
import cats.syntax.all.*
import lucuma.core.enums.ComaOption
import lucuma.core.enums.M1Source
import lucuma.core.enums.MountGuideOption
import lucuma.core.enums.TipTiltSource
import lucuma.core.math.Angle
import lucuma.core.math.Parallax
import lucuma.core.math.ProperMotion
import lucuma.core.math.RadialVelocity
import lucuma.core.math.Wavelength
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.Enumerated
import lucuma.core.util.TimeSpan
import monocle.Getter
import mouse.boolean.given
import navigate.epics.VerifiedEpics.*
import navigate.model.Distance
import navigate.model.enums.DomeMode
import navigate.model.enums.ShutterMode
import navigate.server.ApplyCommandResult
import navigate.server.ConnectionTimeout
import navigate.server.epicsdata.BinaryOnOff
import navigate.server.epicsdata.BinaryYesNo
import navigate.server.tcs.Target.*
import navigate.server.tcs.TcsEpicsSystem.ProbeTrackingCommand
import navigate.server.tcs.TcsEpicsSystem.TargetCommand
import navigate.server.tcs.TcsEpicsSystem.TcsCommands

import scala.concurrent.duration.FiniteDuration

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

  protected def applyTcsConfig(
    config: TcsBaseController.TcsConfig
  ): VerifiedEpics[F, F, ApplyCommandResult] =
    setTarget(Getter[TcsCommands[F], TargetCommand[F, TcsCommands[F]]](_.sourceACmd),
              config.sourceATarget
    ).compose(config.sourceATarget.wavelength.map(setSourceAWalength).getOrElse(identity))
      .compose(setRotatorTrackingConfig(config.rotatorTrackConfig))
      .compose(setInstrumentSpecifics(config.instrumentSpecifics))(
        tcsEpics.startCommand(timeout)
      )
      .post

  override def tcsConfig(config: TcsBaseController.TcsConfig): F[ApplyCommandResult] =
    setTarget(Getter[TcsCommands[F], TargetCommand[F, TcsCommands[F]]](_.sourceACmd),
              config.sourceATarget
    ).compose(config.sourceATarget.wavelength.map(setSourceAWalength).getOrElse(identity))(
      tcsEpics.startCommand(timeout)
    ).post
      .verifiedRun(ConnectionTimeout)

  override def slew(
    slewOptions: SlewOptions,
    tcsConfig:   TcsBaseController.TcsConfig
  ): F[ApplyCommandResult] =
    setTarget(Getter[TcsCommands[F], TargetCommand[F, TcsCommands[F]]](_.sourceACmd),
              tcsConfig.sourceATarget
    )
      .compose(tcsConfig.sourceATarget.wavelength.map(setSourceAWalength).getOrElse(identity))
      .compose(setSlewOptions(slewOptions))
      .compose(setInstrumentSpecifics(tcsConfig.instrumentSpecifics))
      .compose(
        tcsConfig.oiwfs
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
      .compose(setRotatorTrackingConfig(tcsConfig.rotatorTrackConfig))(
        tcsEpics.startCommand(timeout)
      )
      .post
      .verifiedRun(ConnectionTimeout)

  protected def setInstrumentSpecifics(
    config: InstrumentSpecifics
  ): TcsCommands[F] => TcsCommands[F] =
    setRotatorIaa(config.iaa)
      .compose(setFocusOffset(config.focusOffset))
      .compose(setOrigin(config.origin))

  override def instrumentSpecifics(config: InstrumentSpecifics): F[ApplyCommandResult] =
    setInstrumentSpecifics(config)(
      tcsEpics.startCommand(timeout)
    ).post
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

  protected def setProbeTracking(
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
    val gains = (x: TcsCommands[F]) =>
      if (config.dayTimeMode)
        x.guiderGainsCommands.dayTimeGains
      else
        x.guiderGainsCommands.defaultGains

    val m1 = (x: TcsCommands[F]) =>
      config.m1Guide match {
        case M1GuideConfig.M1GuideOff        =>
          x.m1GuideCommand.state(false).probeGuideModeCommand.setMode(config.probeGuide)
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
            .probeGuideModeCommand
            .setMode(config.probeGuide)
      }

    config.m2Guide match {
      case M2GuideConfig.M2GuideOff               =>
        (gains >>> m1)(tcsEpics.startCommand(timeout)).m2GuideCommand
          .state(false)
          .m2GuideModeCommand
          .coma(false)
          .mountGuideCommand
          .mode(config.mountGuide === MountGuideOption.MountGuideOn)
          .mountGuideCommand
          .source("SCS")
          .probeGuideModeCommand
          .setMode(config.probeGuide)
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
                gains(
                  tcsEpics
                    .startCommand(timeout)
                ).m2GuideConfigCommand
                  .source(src.tag)
                  .m2GuideConfigCommand
                  .sampleFreq(200.0)
                  .m2GuideConfigCommand
                  .filter("raw")
                  .m2GuideConfigCommand
                  .beam(beam)
                  .m2GuideConfigCommand
                  .reset(false)
                  .probeGuideModeCommand
                  .setMode(config.probeGuide)
                  .post
                  .verifiedRun(ConnectionTimeout),
                r.pure[F]
              )
            }.flatMap { r =>
              (r === ApplyCommandResult.Completed).fold(
                (gains >>> m1)(tcsEpics.startCommand(timeout)).m2GuideCommand
                  .state(true)
                  .m2GuideModeCommand
                  .coma(coma === ComaOption.ComaOn)
                  .mountGuideCommand
                  .mode(config.mountGuide === MountGuideOption.MountGuideOn)
                  .mountGuideCommand
                  .source("SCS")
                  .probeGuideModeCommand
                  .setMode(config.probeGuide)
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

  override def oiwfsObserve(exposureTime: TimeSpan, isQL: Boolean): F[ApplyCommandResult] = tcsEpics
    .startCommand(timeout)
    .oiWfsCommands
    .observe
    .interval(exposureTime.toSeconds.toDouble)
    .oiWfsCommands
    .observe
    .output(isQL.fold("QL", ""))
    .oiWfsCommands
    .observe
    .options(isQL.fold("DHS", "NONE"))
    .oiWfsCommands
    .observe
    .numberOfExposures(-1)
    .oiWfsCommands
    .observe
    .path("")
    .oiWfsCommands
    .observe
    .fileName("")
    .oiWfsCommands
    .observe
    .label("")
    .post
    .verifiedRun(ConnectionTimeout)

  override def oiwfsStopObserve: F[ApplyCommandResult] = tcsEpics
    .startCommand(timeout)
    .oiWfsCommands
    .stop
    .mark
    .post
    .verifiedRun(ConnectionTimeout)

  private def calcM1Guide(m1: BinaryOnOff, m1src: String): M1GuideConfig =
    if (m1 === BinaryOnOff.Off) M1GuideConfig.M1GuideOff
    else
      Enumerated[M1Source]
        .fromTag(m1src.toLowerCase.capitalize)
        .map(M1GuideConfig.M1GuideOn.apply)
        .getOrElse(M1GuideConfig.M1GuideOff)

  private def calcM2Source(v: String, tt: TipTiltSource): Set[TipTiltSource] =
    if (v.contains("AUTO")) Set(tt)
    else Set.empty

  private def calcM2Guide(
    m2:   BinaryOnOff,
    m2p1: String,
    m2p2: String,
    m2oi: String,
    m2ao: String,
    coma: BinaryOnOff
  ): M2GuideConfig = {
    val src = Set.empty ++
      calcM2Source(m2p1, TipTiltSource.PWFS1) ++
      calcM2Source(m2p2, TipTiltSource.PWFS2) ++
      calcM2Source(m2oi, TipTiltSource.OIWFS) ++
      calcM2Source(m2ao, TipTiltSource.GAOS)

    if (m2 === BinaryOnOff.On && src.nonEmpty)
      M2GuideConfig.M2GuideOn(ComaOption.fromBoolean(coma === BinaryOnOff.On), src)
    else M2GuideConfig.M2GuideOff
  }

  override def getGuideState: F[GuideState] = {
    val x = for {
      fa <- tcsEpics.status.m1Guide
      fb <- tcsEpics.status.m2aoGuide
      fc <- tcsEpics.status.m2oiGuide
      fd <- tcsEpics.status.m2p1Guide
      fe <- tcsEpics.status.m2p2Guide
      ff <- tcsEpics.status.absorbTipTilt
      fg <- tcsEpics.status.comaCorrect
      fh <- tcsEpics.status.m1GuideSource
      fi <- tcsEpics.status.m2GuideState
      fj <- tcsEpics.status.pwfs1On
      fk <- tcsEpics.status.pwfs2On
      fl <- tcsEpics.status.oiwfsOn
    } yield for {
      a <- fa
      b <- fb
      c <- fc
      d <- fd
      e <- fe
      f <- ff
      g <- fg
      h <- fh
      i <- fi
      j <- fj
      k <- fk
      l <- fl
    } yield GuideState(
      MountGuideOption.fromBoolean(f =!= 0),
      calcM1Guide(a, h),
      calcM2Guide(i, d, e, c, b, g),
      j === BinaryYesNo.Yes,
      k === BinaryYesNo.Yes,
      l === BinaryYesNo.Yes
    )

    x.verifiedRun(ConnectionTimeout)
  }

}
