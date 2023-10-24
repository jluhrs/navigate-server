// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.{IO, Ref}
import cats.effect.syntax.all.*
import cats.syntax.all.*
import mouse.boolean.given
import navigate.model.enums.{DomeMode, ShutterMode}
import navigate.model.Distance
import navigate.server.acm.CadDirective
import navigate.server.epicsdata.{BinaryOnOff, BinaryYesNo}
import navigate.server.tcs.Target.SiderealTarget
import navigate.server.tcs.TcsBaseController.TcsConfig
import lucuma.core.math.{Angle, Coordinates, Epoch, Wavelength}
import lucuma.core.util.Enumerated
import munit.CatsEffectSuite
import navigate.server.epicsdata
import squants.space.AngleConversions.*

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class TcsBaseControllerEpicsSpec extends CatsEffectSuite {

  private val DefaultTimeout: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS)

  private val Tolerance: Double = 1e-6
  private def compareDouble(a: Double, b: Double): Boolean = Math.abs(a - b) < Tolerance

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
      assertEquals(rs.mountFollow.value.get, BinaryOnOff.On.tag)
    }
  }

  test("Rotator commands") {
    val testAngle = Angle.fromDoubleDegrees(123.456)

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
      assertEquals(Enumerated[BinaryOnOff].unsafeFromTag(rs.rotFollow.value.get), BinaryOnOff.On)
      assert(rs.rotStopBrake.connected)
      assertEquals(Enumerated[BinaryYesNo].unsafeFromTag(rs.rotStopBrake.value.get),
                   BinaryYesNo.Yes
      )
      assert(rs.rotMoveAngle.connected)
      assert(compareDouble(rs.rotMoveAngle.value.get.toDouble, testAngle.toDoubleDegrees))
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
      assertEquals(rs.enclosure.ecsDomeMode.value.map(Enumerated[DomeMode].unsafeFromTag),
                   DomeMode.MinVibration.some
      )
      assertEquals(rs.enclosure.ecsShutterMode.value.map(Enumerated[ShutterMode].unsafeFromTag),
                   ShutterMode.Tracking.some
      )
      assert(rs.enclosure.ecsSlitHeight.value.exists(x => compareDouble(x.toDouble, testHeight)))
      assertEquals(rs.enclosure.ecsDomeEnable.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.enclosure.ecsShutterEnable.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.On.some
      )
      assert(
        rs.enclosure.ecsVentGateEast.value.exists(x => compareDouble(x.toDouble, testVentEast))
      )
      assert(
        rs.enclosure.ecsVentGateWest.value.exists(x => compareDouble(x.toDouble, testVentWest))
      )
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

    val oiwfsTarget = SiderealTarget(
      objectName = "oiwfsDummy",
      wavelength = Wavelength.unsafeFromIntPicometers(600 * 1000),
      coordinates = Coordinates.unsafeFromRadians(-0.123, 0.321),
      epoch = Epoch.J2000,
      properMotion = none,
      radialVelocity = none,
      parallax = none
    )

    val oiwfsTracking = TrackingConfig(true, false, false, true)

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

    val instrumentSpecifics: InstrumentSpecifics = InstrumentSpecifics(
      iaa = Angle.fromDoubleDegrees(123.45),
      focusOffset = Distance.fromLongMicrometers(2344),
      agName = "gmos",
      origin = Origin(Distance.fromLongMicrometers(4567), Distance.fromLongMicrometers(-8901))
    )

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.slew(
                    SlewConfig(slewOptions,
                               target,
                               instrumentSpecifics,
                               GuiderConfig(oiwfsTarget, oiwfsTracking).some
                    )
                  )
      rs       <- st.get
    } yield {
      // Base Target
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
      assert(
        rs.sourceA.coord1.value.exists(x =>
          compareDouble(x.toDouble, target.coordinates.ra.toHourAngle.toDoubleHours)
        )
      )
      assert(
        rs.sourceA.coord2.value.exists(x =>
          compareDouble(x.toDouble, target.coordinates.dec.toAngle.toDoubleDegrees)
        )
      )
      assert(rs.sourceA.properMotion1.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.sourceA.properMotion2.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.sourceA.epoch.value.exists(x => compareDouble(x.toDouble, target.epoch.epochYear)))
      assert(rs.sourceA.parallax.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.sourceA.radialVelocity.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assertEquals(rs.sourceA.coordSystem.value, "FK5".some)
      assertEquals(rs.sourceA.ephemerisFile.value, "".some)

      // OIWFS Target
      assert(rs.oiwfs.objectName.connected)
      assert(rs.oiwfs.brightness.connected)
      assert(rs.oiwfs.coord1.connected)
      assert(rs.oiwfs.coord2.connected)
      assert(rs.oiwfs.properMotion1.connected)
      assert(rs.oiwfs.properMotion2.connected)
      assert(rs.oiwfs.epoch.connected)
      assert(rs.oiwfs.equinox.connected)
      assert(rs.oiwfs.parallax.connected)
      assert(rs.oiwfs.radialVelocity.connected)
      assert(rs.oiwfs.coordSystem.connected)
      assert(rs.oiwfs.ephemerisFile.connected)
      assertEquals(rs.oiwfs.objectName.value, oiwfsTarget.objectName.some)
      assert(
        rs.oiwfs.coord1.value.exists(x =>
          compareDouble(x.toDouble, oiwfsTarget.coordinates.ra.toHourAngle.toDoubleHours)
        )
      )
      assert(
        rs.oiwfs.coord2.value.exists(x =>
          compareDouble(x.toDouble, oiwfsTarget.coordinates.dec.toAngle.toDoubleDegrees)
        )
      )
      assert(rs.oiwfs.properMotion1.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.oiwfs.properMotion2.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(
        rs.oiwfs.epoch.value.exists(x => compareDouble(x.toDouble, oiwfsTarget.epoch.epochYear))
      )
      assert(rs.oiwfs.parallax.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.oiwfs.radialVelocity.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assertEquals(rs.oiwfs.coordSystem.value, "FK5".some)
      assertEquals(rs.oiwfs.ephemerisFile.value, "".some)

      // OIWFS probe tracking
      assert(rs.oiwfsTracking.nodAchopA.connected)
      assert(rs.oiwfsTracking.nodAchopB.connected)
      assert(rs.oiwfsTracking.nodBchopA.connected)
      assert(rs.oiwfsTracking.nodBchopB.connected)
      assertEquals(rs.oiwfsTracking.nodAchopA.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   oiwfsTracking.nodAchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodAchopB.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   oiwfsTracking.nodAchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodBchopA.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   oiwfsTracking.nodBchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodBchopB.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   oiwfsTracking.nodBchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )

      // Slew Options
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
      assertEquals(rs.slew.zeroChopThrow.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.zeroSourceOffset.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.zeroSourceDiffTrack.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.zeroMountOffset.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.zeroMountDiffTrack.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(
        rs.slew.shortcircuitTargetFilter.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
        BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.shortcircuitMountFilter.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.resetPointing.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.stopGuide.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.zeroGuideOffset.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.zeroInstrumentOffset.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.autoparkPwfs1.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.autoparkPwfs2.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.autoparkOiwfs.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.autoparkGems.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.autoparkAowfs.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   BinaryOnOff.Off.some
      )

      // Instrument Specifics
      assert(
        rs.rotator.iaa.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.iaa.toDoubleDegrees)
        )
      )
      assert(
        rs.focusOffset.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.focusOffset.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.xa.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.x.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.xb.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.x.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.xc.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.x.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.ya.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.y.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.yb.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.y.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.yc.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.y.toMillimeters.value.toDouble)
        )
      )
    }
  }

  test("InstrumentSpecifics command") {
    val instrumentSpecifics: InstrumentSpecifics = InstrumentSpecifics(
      iaa = Angle.fromDoubleDegrees(123.45),
      focusOffset = Distance.fromLongMicrometers(2344),
      agName = "gmos",
      origin = Origin(Distance.fromLongMicrometers(4567), Distance.fromLongMicrometers(-8901))
    )

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.instrumentSpecifics(instrumentSpecifics)
      rs       <- st.get
    } yield {
      assert(
        rs.rotator.iaa.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.iaa.toDoubleDegrees)
        )
      )
      assert(
        rs.focusOffset.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.focusOffset.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.xa.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.x.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.xb.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.x.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.xc.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.x.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.ya.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.y.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.yb.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.y.toMillimeters.value.toDouble)
        )
      )
      assert(
        rs.origin.yc.value.exists(x =>
          compareDouble(x.toDouble, instrumentSpecifics.origin.y.toMillimeters.value.toDouble)
        )
      )
    }
  }

  test("oiwfsTarget command") {
    val oiwfsTarget = SiderealTarget(
      objectName = "oiwfsDummy",
      wavelength = Wavelength.unsafeFromIntPicometers(600 * 1000),
      coordinates = Coordinates.unsafeFromRadians(-0.123, 0.321),
      epoch = Epoch.J2000,
      properMotion = none,
      radialVelocity = none,
      parallax = none
    )

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.oiwfsTarget(oiwfsTarget)
      rs       <- st.get
    } yield {
      assert(rs.oiwfs.objectName.connected)
      assert(rs.oiwfs.brightness.connected)
      assert(rs.oiwfs.coord1.connected)
      assert(rs.oiwfs.coord2.connected)
      assert(rs.oiwfs.properMotion1.connected)
      assert(rs.oiwfs.properMotion2.connected)
      assert(rs.oiwfs.epoch.connected)
      assert(rs.oiwfs.equinox.connected)
      assert(rs.oiwfs.parallax.connected)
      assert(rs.oiwfs.radialVelocity.connected)
      assert(rs.oiwfs.coordSystem.connected)
      assert(rs.oiwfs.ephemerisFile.connected)
      assertEquals(rs.oiwfs.objectName.value, oiwfsTarget.objectName.some)
      assert(
        rs.oiwfs.coord1.value.exists(x =>
          compareDouble(x.toDouble, oiwfsTarget.coordinates.ra.toHourAngle.toDoubleHours)
        )
      )
      assert(
        rs.oiwfs.coord2.value.exists(x =>
          compareDouble(x.toDouble, oiwfsTarget.coordinates.dec.toAngle.toDoubleDegrees)
        )
      )
      assert(rs.oiwfs.properMotion1.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.oiwfs.properMotion2.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(
        rs.oiwfs.epoch.value.exists(x => compareDouble(x.toDouble, oiwfsTarget.epoch.epochYear))
      )
      assert(rs.oiwfs.parallax.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.oiwfs.radialVelocity.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assertEquals(rs.oiwfs.coordSystem.value, "FK5".some)
      assertEquals(rs.oiwfs.ephemerisFile.value, "".some)
    }
  }

  test("oiwfs probe tracking command") {
    val trackingConfig = TrackingConfig(true, false, false, true)

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.oiwfsProbeTracking(trackingConfig)
      rs       <- st.get
    } yield {
      assert(rs.oiwfsTracking.nodAchopA.connected)
      assert(rs.oiwfsTracking.nodAchopB.connected)
      assert(rs.oiwfsTracking.nodBchopA.connected)
      assert(rs.oiwfsTracking.nodBchopB.connected)
      assertEquals(rs.oiwfsTracking.nodAchopA.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   trackingConfig.nodAchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodAchopB.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   trackingConfig.nodAchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodBchopA.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   trackingConfig.nodBchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodBchopB.value.map(Enumerated[BinaryOnOff].unsafeFromTag),
                   trackingConfig.nodBchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
    }

  }

  def createController: IO[(Ref[IO, TestTcsEpicsSystem.State], TcsBaseControllerEpics[IO])] =
    Ref.of[IO, TestTcsEpicsSystem.State](TestTcsEpicsSystem.defaultState).map { st =>
      val sys = TestTcsEpicsSystem.build(st)
      (st, new TcsBaseControllerEpics[IO](sys, DefaultTimeout))
    }

}
