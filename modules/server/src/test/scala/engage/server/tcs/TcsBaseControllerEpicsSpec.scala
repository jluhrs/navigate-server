// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import cats.effect.{IO, Ref}
import cats.effect.syntax.all._
import cats.syntax.all._
import engage.model.enums.{DomeMode, ShutterMode}
import engage.server.acm.CadDirective
import engage.server.epicsdata.{BinaryOnOff, BinaryYesNo}
import engage.server.tcs.Target.SiderealTarget
import engage.server.tcs.TcsBaseController.TcsConfig
import lucuma.core.math.{Coordinates, Epoch, Wavelength}
import munit.CatsEffectSuite
import squants.space.AngleConversions._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class TcsBaseControllerEpicsSpec extends CatsEffectSuite {

  private val DefaultTimeout: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS)

  test("Mount commands") {
    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.mcsPark
      _        <- ctr.mcsFollow(enable = true)
      rs       <- st.get
    } yield {
      assert(rs.telescopeParkDir.connected)
      assertEquals(rs.telescopeParkDir.value.get, CadDirective.MARK)
      assert(rs.mountFollow.connected)
      assertEquals(rs.mountFollow.value.get, BinaryOnOff.On)
    }
  }

  test("Rotator commands") {
    val testAngle = 123.456.degrees

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.rotPark
      _        <- ctr.rotFollow(enable = true)
      _        <- ctr.rotStop(useBrakes = true)
      _        <- ctr.rotMove(testAngle)
      rs       <- st.get
    } yield {
      assert(rs.rotParkDir.connected)
      assertEquals(rs.rotParkDir.value.get, CadDirective.MARK)
      assert(rs.rotFollow.connected)
      assertEquals(rs.rotFollow.value.get, BinaryOnOff.On)
      assert(rs.rotStopBrake.connected)
      assertEquals(rs.rotStopBrake.value.get, BinaryYesNo.Yes)
      assert(rs.rotMoveAngle.connected)
      assertEquals(rs.rotMoveAngle.value.get, testAngle.toDegrees)
    }

  }

  test("Enclosure commands") {
    val testHeight   = 123.456
    val testVentEast = 0.3
    val testVentWest = 0.2

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.ecsCarouselMode(DomeMode.MinVibration,
                                      ShutterMode.Tracking,
                                      testHeight,
                                      domeEnable = true,
                                      shutterEnable = true
                  )
      _        <- ctr.ecsVentGatesMove(testVentEast, testVentWest)
      rs       <- st.get
    } yield {
      assert(rs.enclosure.ecsDomeMode.connected)
      assert(rs.enclosure.ecsShutterMode.connected)
      assert(rs.enclosure.ecsSlitHeight.connected)
      assert(rs.enclosure.ecsDomeEnable.connected)
      assert(rs.enclosure.ecsShutterEnable.connected)
      assert(rs.enclosure.ecsVentGateEast.connected)
      assert(rs.enclosure.ecsVentGateWest.connected)
      assertEquals(rs.enclosure.ecsDomeMode.value, DomeMode.MinVibration.some)
      assertEquals(rs.enclosure.ecsShutterMode.value, ShutterMode.Tracking.some)
      assertEquals(rs.enclosure.ecsSlitHeight.value, testHeight.some)
      assertEquals(rs.enclosure.ecsDomeEnable.value, BinaryOnOff.On.some)
      assertEquals(rs.enclosure.ecsShutterEnable.value, BinaryOnOff.On.some)
      assertEquals(rs.enclosure.ecsVentGateEast.value, testVentEast.some)
      assertEquals(rs.enclosure.ecsVentGateWest.value, testVentWest.some)
    }
  }

  test("Slew command") {
    val target = SiderealTarget(
      objectName = "dummy",
      wavelength = Wavelength.unsafeFromIntPicometers(400 * 1000),
      coordinates = Coordinates.unsafeFromRadians(-0.321, 0.123),
      epoch = Epoch.J2000,
      properMotion = none,
      radialVelocity = none,
      parallax = none
    )

    val slewOptions = SlewOptions(
      ZeroChopThrow(true),
      ZeroSourceOffset(false),
      ZeroSourceDiffTrack(true),
      ZeroMountOffset(false),
      ZeroMountDiffTrack(true),
      ShortcircuitTargetFilter(false),
      ShortcircuitMountFilter(true),
      ResetPointing(false),
      StopGuide(true),
      ZeroGuideOffset(false),
      ZeroInstrumentOffset(true),
      AutoparkPwfs1(false),
      AutoparkPwfs2(true),
      AutoparkOiwfs(false),
      AutoparkGems(true),
      AutoparkAowfs(false)
    )

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.slew(SlewConfig(slewOptions, target))
      rs       <- st.get
    } yield {
      assert(rs.sourceA.objectName.connected)
      assert(rs.sourceA.brightness.connected)
      assert(rs.sourceA.coord1.connected)
      assert(rs.sourceA.coord2.connected)
      assert(rs.sourceA.properMotion1.connected)
      assert(rs.sourceA.properMotion2.connected)
      assert(rs.sourceA.epoch.connected)
      assert(rs.sourceA.equinox.connected)
      assert(rs.sourceA.parallax.connected)
      assert(rs.sourceA.radialVelocity.connected)
      assert(rs.sourceA.coordSystem.connected)
      assert(rs.sourceA.ephemerisFile.connected)
      assertEquals(rs.sourceA.objectName.value, target.objectName.some)
      assertEquals(rs.sourceA.coord1.value, target.coordinates.ra.toAngle.toDoubleDegrees.some)
      assertEquals(rs.sourceA.coord2.value, target.coordinates.dec.toAngle.toDoubleDegrees.some)
      assertEquals(rs.sourceA.properMotion1.value, 0.0.some)
      assertEquals(rs.sourceA.properMotion2.value, 0.0.some)
      assertEquals(rs.sourceA.epoch.value, target.epoch.toString().some)
      assertEquals(rs.sourceA.parallax.value, 0.0.some)
      assertEquals(rs.sourceA.radialVelocity.value, 0.0.some)
      assertEquals(rs.sourceA.coordSystem.value, "FK5/J2000".some)
      assertEquals(rs.sourceA.ephemerisFile.value, "".some)
      assert(rs.slew.zeroChopThrow.connected)
      assert(rs.slew.zeroSourceOffset.connected)
      assert(rs.slew.zeroSourceDiffTrack.connected)
      assert(rs.slew.zeroMountOffset.connected)
      assert(rs.slew.zeroMountDiffTrack.connected)
      assert(rs.slew.shortcircuitTargetFilter.connected)
      assert(rs.slew.shortcircuitMountFilter.connected)
      assert(rs.slew.resetPointing.connected)
      assert(rs.slew.stopGuide.connected)
      assert(rs.slew.zeroGuideOffset.connected)
      assert(rs.slew.zeroInstrumentOffset.connected)
      assert(rs.slew.autoparkPwfs1.connected)
      assert(rs.slew.autoparkPwfs2.connected)
      assert(rs.slew.autoparkOiwfs.connected)
      assert(rs.slew.autoparkGems.connected)
      assert(rs.slew.autoparkAowfs.connected)
      assertEquals(rs.slew.zeroChopThrow.value, BinaryOnOff.On.some)
      assertEquals(rs.slew.zeroSourceOffset.value, BinaryOnOff.Off.some)
      assertEquals(rs.slew.zeroSourceDiffTrack.value, BinaryOnOff.On.some)
      assertEquals(rs.slew.zeroMountOffset.value, BinaryOnOff.Off.some)
      assertEquals(rs.slew.zeroMountDiffTrack.value, BinaryOnOff.On.some)
      assertEquals(rs.slew.shortcircuitTargetFilter.value, BinaryOnOff.Off.some)
      assertEquals(rs.slew.shortcircuitMountFilter.value, BinaryOnOff.On.some)
      assertEquals(rs.slew.resetPointing.value, BinaryOnOff.Off.some)
      assertEquals(rs.slew.stopGuide.value, BinaryOnOff.On.some)
      assertEquals(rs.slew.zeroGuideOffset.value, BinaryOnOff.Off.some)
      assertEquals(rs.slew.zeroInstrumentOffset.value, BinaryOnOff.On.some)
      assertEquals(rs.slew.autoparkPwfs1.value, BinaryOnOff.Off.some)
      assertEquals(rs.slew.autoparkPwfs2.value, BinaryOnOff.On.some)
      assertEquals(rs.slew.autoparkOiwfs.value, BinaryOnOff.Off.some)
      assertEquals(rs.slew.autoparkGems.value, BinaryOnOff.On.some)
      assertEquals(rs.slew.autoparkAowfs.value, BinaryOnOff.Off.some)
    }
  }

  def createController: IO[(Ref[IO, TestTcsEpicsSystem.State], TcsBaseControllerEpics[IO])] =
    Ref.of[IO, TestTcsEpicsSystem.State](TestTcsEpicsSystem.defaultState).map { st =>
      val sys = TestTcsEpicsSystem.build(st)
      (st, new TcsBaseControllerEpics[IO](sys, DefaultTimeout))
    }

}
