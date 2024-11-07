// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Parallel
import cats.effect.Async
import cats.effect.Ref
import cats.effect.Temporal
import cats.syntax.all.*
import lucuma.core.enums.ComaOption
import lucuma.core.enums.Instrument
import lucuma.core.enums.LightSinkName
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
import monocle.Focus.focus
import monocle.Getter
import monocle.syntax.all.*
import mouse.boolean.given
import navigate.epics.VerifiedEpics
import navigate.epics.VerifiedEpics.*
import navigate.model.Distance
import navigate.model.enums.AoFoldPosition
import navigate.model.enums.CentralBafflePosition
import navigate.model.enums.DeployableBafflePosition
import navigate.model.enums.DomeMode
import navigate.model.enums.HrwfsPickupPosition
import navigate.model.enums.LightSource
import navigate.model.enums.ShutterMode
import navigate.server
import navigate.server.ApplyCommandResult
import navigate.server.ConnectionTimeout
import navigate.server.epicsdata.BinaryOnOff
import navigate.server.epicsdata.BinaryYesNo
import navigate.server.tcs.AcquisitionCameraEpicsSystem.*
import navigate.server.tcs.ParkStatus.NotParked
import navigate.server.tcs.Target.*
import navigate.server.tcs.TcsEpicsSystem.ProbeTrackingCommand
import navigate.server.tcs.TcsEpicsSystem.TargetCommand
import navigate.server.tcs.TcsEpicsSystem.TcsCommands

import scala.concurrent.duration.*

import TcsBaseController.{EquinoxDefault, FixedSystem, SwapConfig, SystemDefault, TcsConfig}

/* This class implements the common TCS commands */
abstract class TcsBaseControllerEpics[F[_]: Async: Parallel: Temporal](
  sys:      EpicsSystems[F],
  timeout:  FiniteDuration,
  stateRef: Ref[F, TcsBaseControllerEpics.State]
) extends TcsBaseController[F] {
  override def mcsPark: F[ApplyCommandResult] =
    sys.tcsEpics
      .startCommand(timeout)
      .mcsParkCommand
      .mark
      .post
      .verifiedRun(ConnectionTimeout)

  override def mcsFollow(enable: Boolean): F[ApplyCommandResult] =
    sys.tcsEpics
      .startCommand(timeout)
      .mcsFollowCommand
      .setFollow(enable)
      .post
      .verifiedRun(ConnectionTimeout)

  override def rotStop(useBrakes: Boolean): F[ApplyCommandResult] =
    sys.tcsEpics
      .startCommand(timeout)
      .rotStopCommand
      .setBrakes(useBrakes)
      .post
      .verifiedRun(ConnectionTimeout)

  override def rotPark: F[ApplyCommandResult] =
    sys.tcsEpics
      .startCommand(timeout)
      .rotParkCommand
      .mark
      .post
      .verifiedRun(ConnectionTimeout)

  override def rotFollow(enable: Boolean): F[ApplyCommandResult] =
    sys.tcsEpics
      .startCommand(timeout)
      .rotFollowCommand
      .setFollow(enable)
      .post
      .verifiedRun(ConnectionTimeout)

  override def rotMove(angle: Angle): F[ApplyCommandResult] =
    sys.tcsEpics
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
    sys.tcsEpics
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
    sys.tcsEpics
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
  ): TcsCommands[F] => TcsCommands[F] =
    setTarget(Getter[TcsCommands[F], TargetCommand[F, TcsCommands[F]]](_.sourceACmd),
              config.sourceATarget
    ).compose(config.sourceATarget.wavelength.map(setSourceAWalength).getOrElse(identity))
      .compose(setRotatorTrackingConfig(config.rotatorTrackConfig))
      .compose(setInstrumentSpecifics(config.instrumentSpecifics))
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
          .getOrElse(
            setProbeTracking(
              Getter[TcsCommands[F], ProbeTrackingCommand[F, TcsCommands[F]]](
                _.oiwfsProbeTrackingCommand
              ),
              TrackingConfig.noTracking
            )
          )
      )

  // Added a 1.5 s wait between selecting the OIWFS and setting targets, to copy TCC
  override def tcsConfig(config: TcsBaseController.TcsConfig): F[ApplyCommandResult] =
    disableGuide *>
      (
        selectOiwfs(config) *>
          VerifiedEpics.liftF(Temporal[F].sleep(OiwfsSelectionDelay)) *>
          applyTcsConfig(config)(
            sys.tcsEpics.startCommand(timeout)
          ).post
      ).verifiedRun(ConnectionTimeout)

  override def slew(
    slewOptions: SlewOptions,
    tcsConfig:   TcsBaseController.TcsConfig
  ): F[ApplyCommandResult] =
    disableGuide *>
      (
        selectOiwfs(tcsConfig) *>
          VerifiedEpics.liftF(Temporal[F].sleep(OiwfsSelectionDelay)) *>
          setSlewOptions(slewOptions)
            .compose(applyTcsConfig(tcsConfig))(
              sys.tcsEpics.startCommand(timeout)
            )
            .post
      ).verifiedRun(ConnectionTimeout)

  protected def setInstrumentSpecifics(
    config: InstrumentSpecifics
  ): TcsCommands[F] => TcsCommands[F] =
    setRotatorIaa(config.iaa)
      .compose(setFocusOffset(config.focusOffset))
      .compose(setOrigin(config.origin))

  override def instrumentSpecifics(config: InstrumentSpecifics): F[ApplyCommandResult] =
    setInstrumentSpecifics(config)(
      sys.tcsEpics.startCommand(timeout)
    ).post
      .verifiedRun(ConnectionTimeout)

  override def oiwfsTarget(target: Target): F[ApplyCommandResult] =
    setTarget(Getter[TcsCommands[F], TargetCommand[F, TcsCommands[F]]](_.oiwfsTargetCmd), target)(
      sys.tcsEpics.startCommand(timeout)
    ).post
      .verifiedRun(ConnectionTimeout)

  override def rotIaa(angle: Angle): F[ApplyCommandResult] =
    setRotatorIaa(angle)(
      sys.tcsEpics.startCommand(timeout)
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
      sys.tcsEpics.startCommand(timeout)
    ).post
      .verifiedRun(ConnectionTimeout)

  override def oiwfsPark: F[ApplyCommandResult] =
    sys.tcsEpics
      .startCommand(timeout)
      .oiwfsProbeCommands
      .park
      .mark
      .post
      .verifiedRun(ConnectionTimeout)

  override def oiwfsFollow(enable: Boolean): F[ApplyCommandResult] =
    sys.tcsEpics
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
    setRotatorTrackingConfig(cfg)(sys.tcsEpics.startCommand(timeout)).post
      .verifiedRun(ConnectionTimeout)

  override def enableGuide(config: TelescopeGuideConfig): F[ApplyCommandResult] = {
    val gains =
      if (config.dayTimeMode.exists(_ === true))
        dayTimeGains
      else
        defaultGains

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
        (gains *> m1(sys.tcsEpics.startCommand(timeout)).m2GuideCommand
          .state(false)
          .m2GuideModeCommand
          .coma(false)
          .mountGuideCommand
          .mode(config.mountGuide === MountGuideOption.MountGuideOn)
          .mountGuideCommand
          .source("SCS")
          .probeGuideModeCommand
          .setMode(config.probeGuide)
          .post)
          .verifiedRun(ConnectionTimeout)
      case M2GuideConfig.M2GuideOn(coma, sources) =>
        val requireReset: Boolean = true // Use current state
        val beams                 = List("A", "B")
        sources
          .flatMap(x => beams.map(y => (x, y)))
          .foldLeft(
            requireReset.fold(
              sys.tcsEpics
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
                sys.tcsEpics
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
                  .probeGuideModeCommand
                  .setMode(config.probeGuide)
                  .post
                  .verifiedRun(ConnectionTimeout),
                r.pure[F]
              )
            }.flatMap { r =>
              (r === ApplyCommandResult.Completed).fold(
                (gains *> m1(sys.tcsEpics.startCommand(timeout)).m2GuideCommand
                  .state(true)
                  .m2GuideModeCommand
                  .coma(coma === ComaOption.ComaOn)
                  .mountGuideCommand
                  .mode(config.mountGuide === MountGuideOption.MountGuideOn)
                  .mountGuideCommand
                  .source("SCS")
                  .probeGuideModeCommand
                  .setMode(config.probeGuide)
                  .post).verifiedRun(ConnectionTimeout) <*
                  stateRef.get.flatMap { s =>
                    s.oiwfs.period
                      .flatMap(p =>
                        guideUsesOiwfs(config.m1Guide, config.m2Guide)
                          .option(setupOiwfsObserve(p, false))
                      )
                      .getOrElse(
                        ApplyCommandResult.Completed.pure[F]
                      )
                  },
                r.pure[F]
              )
            }
          }
    }
  }

  override def disableGuide: F[ApplyCommandResult] = sys.tcsEpics
    .startCommand(timeout)
    .m1GuideCommand
    .state(false)
    .m2GuideCommand
    .state(false)
    .mountGuideCommand
    .mode(false)
    .probeGuideModeCommand
    .setMode(None)
    .post
    .verifiedRun(ConnectionTimeout) <*
    stateRef.get.flatMap { s =>
      s.oiwfs.period
        .map(setupOiwfsObserve(_, true))
        .getOrElse(
          ApplyCommandResult.Completed.pure[F]
        )
    }

  def darkFileName(prefix: String, exposureTime: TimeSpan): String =
    if (exposureTime > TimeSpan.unsafeFromMicroseconds(1000000))
      s"${prefix}${math.round(1000.0 / exposureTime.toSeconds.toDouble)}mHz.fits"
    else
      s"${prefix}${math.round(1.0 / exposureTime.toSeconds.toDouble)}Hz.fits"

  val oiPrefix: String = "data/"

  def setupOiwfsObserve(exposureTime: TimeSpan, isQL: Boolean): F[ApplyCommandResult] =
    stateRef.get.flatMap { st =>
      val expTimeChange = st.oiwfs.period.forall(_ =!= exposureTime).option(exposureTime)
      val qlChange      = st.oiwfs.configuredForQl.forall(_ =!= isQL).option(isQL)

      val setSigProc  = expTimeChange
        .map(t =>
          sys.oiwfs.startSignalProcCommand(timeout).filename(darkFileName(oiPrefix, t)).post
        )
        .getOrElse(VerifiedEpics.pureF[F, F, ApplyCommandResult](ApplyCommandResult.Completed)) *>
        qlChange
          .map(
            _.fold(
              sys.oiwfs.startClosedLoopCommand(timeout).zernikes2m2(0),
              sys.oiwfs.startClosedLoopCommand(timeout).zernikes2m2(1)
            ).post
          )
          .getOrElse(VerifiedEpics.pureF[F, F, ApplyCommandResult](ApplyCommandResult.Completed))
      val setInterval = (c: TcsCommands[F]) =>
        expTimeChange.fold(c)(t => c.oiWfsCommands.observe.interval(t.toSeconds.toDouble))
      val setQl       = (c: TcsCommands[F]) =>
        qlChange.fold(c)(i =>
          c.oiWfsCommands.observe
            .output(i.fold("QL", ""))
            .oiWfsCommands
            .observe
            .options(i.fold("DHS", "NONE"))
        )

      val setupAndStart = setSigProc *>
        (setInterval >>> setQl)(sys.tcsEpics.startCommand(timeout)).oiWfsCommands.observe
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

      (for {
        oiActive <- sys.tcsEpics.status.oiwfsOn.map(_.map(_ === BinaryYesNo.Yes))
        ret      <- VerifiedEpics.ifF[F, F, ApplyCommandResult](
                      oiActive.map(_ && qlChange.isEmpty && expTimeChange.isEmpty)
                    ) {
                      VerifiedEpics.pureF[F, F, ApplyCommandResult](ApplyCommandResult.Completed)
                    } {
                      VerifiedEpics.ifF(oiActive) {
                        sys.tcsEpics
                          .startCommand(timeout)
                          .oiWfsCommands
                          .stop
                          .mark
                          .post
                      } {
                        VerifiedEpics.pureF[F, F, ApplyCommandResult](ApplyCommandResult.Completed)
                      } *>
                        setupAndStart
                    }
      } yield ret).verifiedRun(ConnectionTimeout) <*
        stateRef.update(
          _.focus(_.oiwfs.period)
            .replace(exposureTime.some)
            .focus(_.oiwfs.configuredForQl)
            .replace(isQL.some)
        )
    }

  def oiwfsObserve(exposureTime: TimeSpan): F[ApplyCommandResult] = getGuideState.flatMap { g =>
    setupOiwfsObserve(exposureTime, !guideUsesOiwfs(g.m1Guide, g.m2Guide))
  }

  override def oiwfsStopObserve: F[ApplyCommandResult] = sys.tcsEpics
    .startCommand(timeout)
    .oiWfsCommands
    .stop
    .mark
    .post
    .verifiedRun(ConnectionTimeout) <*
    stateRef.update(
      _.focus(_.oiwfs.period)
        .replace(None)
        .focus(_.oiwfs.configuredForQl)
        .replace(None)
    )

  // Time to wait after selecting the OIWFS in the AG Sequencer, to let the values propagate to TCS.
  private val OiwfsSelectionDelay: Duration = 1500.milliseconds

  private def selectOiwfs(tcsConfig: TcsConfig): VerifiedEpics[F, F, ApplyCommandResult] =
    sys.tcsEpics
      .startCommand(timeout)
      .oiwfsSelectCommand
      .oiwfsName(TcsBaseControllerEpics.encodeOiwfsSelect(tcsConfig.oiwfs, tcsConfig.instrument))
      .oiwfsSelectCommand
      .output("WFS")
      .post

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
      fa <- sys.tcsEpics.status.m1Guide
      fb <- sys.tcsEpics.status.m2aoGuide
      fc <- sys.tcsEpics.status.m2oiGuide
      fd <- sys.tcsEpics.status.m2p1Guide
      fe <- sys.tcsEpics.status.m2p2Guide
      ff <- sys.tcsEpics.status.absorbTipTilt
      fg <- sys.tcsEpics.status.comaCorrect
      fh <- sys.tcsEpics.status.m1GuideSource
      fi <- sys.tcsEpics.status.m2GuideState
      fj <- sys.tcsEpics.status.pwfs1On
      fk <- sys.tcsEpics.status.pwfs2On
      fl <- sys.tcsEpics.status.oiwfsOn
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

  def dayTimeGains: VerifiedEpics[F, F, ApplyCommandResult] =
    sys.pwfs1
      .startGainCommand(timeout)
      .gains
      .setTipGain(0.0)
      .gains
      .setTiltGain(0.0)
      .gains
      .setFocusGain(0.0)
      .post *>
      sys.pwfs2
        .startGainCommand(timeout)
        .gains
        .setTipGain(0.0)
        .gains
        .setTiltGain(0.0)
        .gains
        .setFocusGain(0.0)
        .post *>
      sys.oiwfs
        .startGainCommand(timeout)
        .gains
        .setTipGain(0.0)
        .gains
        .setTiltGain(0.0)
        .gains
        .setFocusGain(0.0)
        .post

  def defaultGains: VerifiedEpics[F, F, ApplyCommandResult] =
    sys.pwfs1
      .startGainCommand(timeout)
      .resetGain
      .post *>
      sys.pwfs2
        .startGainCommand(timeout)
        .resetGain
        .post *>
      sys.oiwfs
        .startGainCommand(timeout)
        .resetGain
        .post

  private def guideUsesOiwfs(m1Guide: M1GuideConfig, m2Guide: M2GuideConfig): Boolean =
    m1Guide.uses(M1Source.OIWFS) || m2Guide.uses(TipTiltSource.OIWFS)

  override def getGuideQuality: F[GuidersQualityValues] = (
    for {
      p1_fF <- sys.pwfs1.getQualityStatus.flux
      p1_cF <- sys.pwfs1.getQualityStatus.centroidDetected
      p2_fF <- sys.pwfs2.getQualityStatus.flux
      p2_cF <- sys.pwfs2.getQualityStatus.centroidDetected
      oi_fF <- sys.oiwfs.getQualityStatus.flux
      oi_cF <- sys.oiwfs.getQualityStatus.centroidDetected
    } yield for {
      p1_f <- p1_fF
      p1_c <- p1_cF
      p2_f <- p2_fF
      p2_c <- p2_cF
      oi_f <- oi_fF
      oi_c <- oi_cF
    } yield GuidersQualityValues(
      GuidersQualityValues.GuiderQuality(p1_f, p1_c),
      GuidersQualityValues.GuiderQuality(p2_f, p2_c),
      GuidersQualityValues.GuiderQuality(oi_f, oi_c)
    )
  ).verifiedRun(ConnectionTimeout)

  override def baffles(
    central:    CentralBafflePosition,
    deployable: DeployableBafflePosition
  ): F[ApplyCommandResult] =
    sys.tcsEpics
      .startCommand(timeout)
      .bafflesCommand
      .central(central)
      .bafflesCommand
      .deployable(deployable)
      .post
      .verifiedRun(ConnectionTimeout)

  override def getTelescopeState: F[TelescopeState] = (
    for {
      mcsfF  <- sys.mcs.getFollowingState
      scsfF  <- sys.scs.getFollowingState
      crcsfF <- sys.crcs.getFollowingState
      p1fF   <- sys.ags.status.p1Follow
      p1pF   <- sys.ags.status.p1Parked
      p2fF   <- sys.ags.status.p2Follow
      p2pF   <- sys.ags.status.p2Parked
      oifF   <- sys.ags.status.oiFollow
      oipF   <- sys.ags.status.oiParked
    } yield for {
      mcsf  <- mcsfF
      scsf  <- scsfF
      crcsf <- crcsfF
      p1f   <- p1fF
      p1p   <- p1pF
      p2f   <- p2fF
      p2p   <- p2pF
      oif   <- oifF
      oip   <- oipF
    } yield TelescopeState(
      mount = MechSystemState(NotParked, mcsf),
      scs = MechSystemState(NotParked, scsf),
      crcs = MechSystemState(NotParked, crcsf),
      pwfs1 = MechSystemState(p1p, p1f),
      pwfs2 = MechSystemState(p2p, p2f),
      oiwfs = MechSystemState(oip, oif)
    )
  ).verifiedRun(ConnectionTimeout)

  override def scsFollow(enable: Boolean): F[ApplyCommandResult] =
    sys.tcsEpics
      .startCommand(timeout)
      .m2FollowCommand
      .setFollow(enable)
      .post
      .verifiedRun(ConnectionTimeout)

  override def swapTarget(swapConfig: SwapConfig): F[ApplyCommandResult] =
    disableGuide *>
      sys.hrwfs.status.filter.verifiedRun(ConnectionTimeout).flatMap { x =>
        (
          applyTcsConfig(
            swapConfig.toTcsConfig
              .focus(_.sourceATarget)
              .andThen(Target.wavelength)
              .replace(x.toWavelength.some)
          ) >>>
            setLightPath(LightSource.Sky, LightSinkName.Ac, 1)
        )(
          sys.tcsEpics.startCommand(timeout)
        ).post
          .verifiedRun(ConnectionTimeout)
      }

  override def restoreTarget(config: TcsConfig): F[ApplyCommandResult] =
    disableGuide *>
      getInstrumentPorts.flatMap { ps =>
        getPort(ps, config.instrument.toLightSink)
          .map(p =>
            (
              selectOiwfs(config) *>
                VerifiedEpics.liftF(Temporal[F].sleep(OiwfsSelectionDelay)) *>
                (applyTcsConfig(config) >>> setLightPath(LightSource.Sky,
                                                         config.instrument.toLightSink,
                                                         p
                ))(
                  sys.tcsEpics.startCommand(timeout)
                ).post
            ).verifiedRun(ConnectionTimeout)
          )
          .getOrElse(ApplyCommandResult.Completed.pure[F])
      }

  private def setLightPath(
    from: LightSource,
    to:   LightSinkName,
    port: Int
  ): TcsCommands[F] => TcsCommands[F] = (x: TcsCommands[F]) => {
    val aoFold      = (s: TcsCommands[F]) =>
      (from === LightSource.AO)
        .fold(s.aoFoldCommands.move.setPosition(AoFoldPosition.In), s.aoFoldCommands.park.mark)
    val hrwfsPickup = (s: TcsCommands[F]) =>
      (to === LightSinkName.Hr || to === LightSinkName.Ac).fold(
        s.hrwfsCommands.move.setPosition(HrwfsPickupPosition.In),
        s.hrwfsCommands.park.mark
      )
    val scienceFold = (s: TcsCommands[F]) =>
      (port === 1).fold(
        s.scienceFoldCommands.park.mark,
        s.scienceFoldCommands.move.setPosition(
          ScienceFold.Position(from, to, port)
        )
      )

    (aoFold >>> hrwfsPickup >>> scienceFold)(x)
  }

  override def lightPath(from: LightSource, to: LightSinkName): F[ApplyCommandResult] =
    getInstrumentPorts.flatMap { ports =>
      getPort(ports, to)
        .map { p =>
          setLightPath(from, to, p)(sys.tcsEpics.startCommand(timeout)).post
            .verifiedRun(ConnectionTimeout)
        }
        .getOrElse(ApplyCommandResult.Completed.pure[F])
    }

  def getPort(instrumentPorts: InstrumentPorts, lightSinkName: LightSinkName): Option[Int] = {
    val p = lightSinkName match {
      case LightSinkName.Gmos | LightSinkName.Gmos_Ifu                             => instrumentPorts.gmosPort
      case LightSinkName.Niri_f6 | LightSinkName.Niri_f14 | LightSinkName.Niri_f32 =>
        instrumentPorts.niriPort
      case LightSinkName.Ac                                                        => 1
      case LightSinkName.Hr                                                        => 1
      case LightSinkName.Nifs                                                      => instrumentPorts.nifsPort
      case LightSinkName.Gnirs                                                     => instrumentPorts.gnirsPort
      case LightSinkName.Visitor                                                   => 0
      case LightSinkName.F2                                                        => instrumentPorts.flamingos2Port
      case LightSinkName.Gsaoi                                                     => instrumentPorts.gsaoiPort
      case LightSinkName.Gpi                                                       => instrumentPorts.gpiPort
      case LightSinkName.Ghost                                                     => instrumentPorts.ghostPort
      case _                                                                       => 0
    }

    (p > 0).option(p)
  }

  private val hrwfsStream: String = "hrwfsScience"

  override def hrwfsObserve(exposureTime: TimeSpan): F[ApplyCommandResult] =
    (sys.hrwfs
      .startCommand(timeout)
      .setExposureTime(exposureTime.toSeconds.toDouble)
      .setNumberOfFrames(-1)
      .setQuicklookStream(hrwfsStream)
      .setDhsOption(2)
      .post *>
      sys.hrwfs
        .startCommand(timeout)
        .setDhsLabel("NONE")
        .post).verifiedRun(ConnectionTimeout)

  override def hrwfsStopObserve: F[ApplyCommandResult] =
    sys.hrwfs
      .startCommand(timeout)
      .stop
      .post
      .verifiedRun(ConnectionTimeout)

  override def m1Park: F[ApplyCommandResult] =
    sys.tcsEpics.startCommand(timeout).m1Commands.park.post.verifiedRun(ConnectionTimeout)

  override def m1Unpark: F[ApplyCommandResult] =
    sys.tcsEpics.startCommand(timeout).m1Commands.unpark.post.verifiedRun(ConnectionTimeout)

  override def m1UpdateOn: F[ApplyCommandResult] = sys.tcsEpics
    .startCommand(timeout)
    .m1Commands
    .ao(true)
    .m1Commands
    .figureUpdates(true)
    .post
    .verifiedRun(ConnectionTimeout)

  override def m1UpdateOff: F[ApplyCommandResult] =
    sys.tcsEpics.startCommand(timeout).m1Commands.ao(false).post.verifiedRun(ConnectionTimeout)

  override def m1ZeroFigure: F[ApplyCommandResult] =
    sys.tcsEpics.startCommand(timeout).m1Commands.zero("FIGURE").post.verifiedRun(ConnectionTimeout)

  override def m1LoadAoFigure: F[ApplyCommandResult] = sys.tcsEpics
    .startCommand(timeout)
    .m1Commands
    .loadModel("AO")
    .post
    .verifiedRun(ConnectionTimeout)

  override def m1LoadNonAoFigure: F[ApplyCommandResult] = sys.tcsEpics
    .startCommand(timeout)
    .m1Commands
    .loadModel("non-AO")
    .post
    .verifiedRun(ConnectionTimeout)
}

object TcsBaseControllerEpics {

  def encodeOiwfsSelect(oiGuideConfig: Option[GuiderConfig], instrument: Instrument): String =
    oiGuideConfig
      .flatMap { _ =>
        instrument match
          case Instrument.GmosNorth | Instrument.GmosSouth => "GMOS".some
          case Instrument.Nifs                             => "NIFS".some
          case Instrument.Gnirs                            => "GNIRS".some
          case Instrument.Niri                             => "NIRI".some
          case Instrument.Flamingos2                       => "F2".some
          case _                                           => None
      }
      .getOrElse("None")

  case class WfsConfigState(
    period:          Option[TimeSpan],
    configuredForQl: Option[Boolean]
  )

  case class State(
    pwfs1: WfsConfigState,
    pwfs2: WfsConfigState,
    oiwfs: WfsConfigState
  )

  object State {
    val default: State = State(
      WfsConfigState(None, None),
      WfsConfigState(None, None),
      WfsConfigState(None, None)
    )
  }

}
