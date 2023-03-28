// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs
import cats.Parallel
import cats.effect.Async
import navigate.server.{ApplyCommandResult, ConnectionTimeout}

import scala.concurrent.duration.FiniteDuration
import navigate.epics.VerifiedEpics._
import navigate.model.enums.{DomeMode, ShutterMode}
import navigate.server.tcs.Target._
import navigate.server.tcs.TcsEpicsSystem.{TargetCommand, TcsCommands}
import lucuma.core.math.{Parallax, ProperMotion, RadialVelocity, Wavelength}
import monocle.Getter
import squants.Angle

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
        .compose[TcsCommands[F]](l.get(_).coordSystem("FK5/J2000"))
        .compose[TcsCommands[F]](l.get(_).coord1(t.coordinates.ra.toAngle.toDoubleDegrees))
        .compose[TcsCommands[F]](l.get(_).coord2(t.coordinates.dec.toAngle.toSignedDoubleDegrees))
        .compose[TcsCommands[F]](l.get(_).brightness(DefaultBrightness))
        .compose[TcsCommands[F]](l.get(_).epoch(t.epoch.epochYear))
        .compose[TcsCommands[F]](l.get(_).equinox("J2000"))
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
    ).compose(setSourceAWalength(config.baseTarget.wavelength))
      .compose(setSlewOptions(config.slewOptions))(
        tcsEpics.startCommand(timeout)
      )
      .post
      .verifiedRun(ConnectionTimeout)
}
