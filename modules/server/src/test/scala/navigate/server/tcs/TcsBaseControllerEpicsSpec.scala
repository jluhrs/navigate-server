// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all.*
import lucuma.core.math.Angle
import lucuma.core.math.Coordinates
import lucuma.core.math.Epoch
import lucuma.core.math.Wavelength
import lucuma.core.util.Enumerated
import lucuma.core.util.TimeSpan
import monocle.syntax.all.*
import mouse.boolean.given
import munit.CatsEffectSuite
import navigate.epics.TestChannel
import navigate.model.Distance
import navigate.model.enums.DomeMode
import navigate.model.enums.M1Source
import navigate.model.enums.ShutterMode
import navigate.model.enums.TipTiltSource
import navigate.server.acm.CadDirective
import navigate.server.epicsdata
import navigate.server.epicsdata.BinaryOnOff
import navigate.server.epicsdata.BinaryYesNo

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import Target.SiderealTarget
import TcsBaseController.*
import M2GuideConfig.M2GuideOn
import TestTcsEpicsSystem.GuideConfigState

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
      assertEquals(rs.enclosure.ecsDomeMode.value.flatMap(Enumerated[DomeMode].fromTag),
                   DomeMode.MinVibration.some
      )
      assertEquals(rs.enclosure.ecsShutterMode.value.flatMap(Enumerated[ShutterMode].fromTag),
                   ShutterMode.Tracking.some
      )
      assert(rs.enclosure.ecsSlitHeight.value.exists(x => compareDouble(x.toDouble, testHeight)))
      assertEquals(rs.enclosure.ecsDomeEnable.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.enclosure.ecsShutterEnable.value.flatMap(Enumerated[BinaryOnOff].fromTag),
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
      wavelength = Wavelength.fromIntPicometers(400 * 1000),
      coordinates = Coordinates.unsafeFromRadians(-0.321, 0.123),
      epoch = Epoch.J2000,
      properMotion = none,
      radialVelocity = none,
      parallax = none
    )

    val oiwfsTarget = SiderealTarget(
      objectName = "oiwfsDummy",
      wavelength = Wavelength.fromIntPicometers(600 * 1000),
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
                    slewOptions,
                    TcsConfig(
                      target,
                      instrumentSpecifics,
                      GuiderConfig(oiwfsTarget, oiwfsTracking).some,
                      RotatorTrackConfig(Angle.Angle90, RotatorTrackingMode.Tracking)
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
      assertEquals(rs.sourceA.coordSystem.value, SystemDefault.some)
      assertEquals(rs.sourceA.ephemerisFile.value, "".some)

      // OIWFS Target
      assert(rs.oiwfsTarget.objectName.connected)
      assert(rs.oiwfsTarget.brightness.connected)
      assert(rs.oiwfsTarget.coord1.connected)
      assert(rs.oiwfsTarget.coord2.connected)
      assert(rs.oiwfsTarget.properMotion1.connected)
      assert(rs.oiwfsTarget.properMotion2.connected)
      assert(rs.oiwfsTarget.epoch.connected)
      assert(rs.oiwfsTarget.equinox.connected)
      assert(rs.oiwfsTarget.parallax.connected)
      assert(rs.oiwfsTarget.radialVelocity.connected)
      assert(rs.oiwfsTarget.coordSystem.connected)
      assert(rs.oiwfsTarget.ephemerisFile.connected)
      assertEquals(rs.oiwfsTarget.objectName.value, oiwfsTarget.objectName.some)
      assert(
        rs.oiwfsTarget.coord1.value.exists(x =>
          compareDouble(x.toDouble, oiwfsTarget.coordinates.ra.toHourAngle.toDoubleHours)
        )
      )
      assert(
        rs.oiwfsTarget.coord2.value.exists(x =>
          compareDouble(x.toDouble, oiwfsTarget.coordinates.dec.toAngle.toDoubleDegrees)
        )
      )
      assert(rs.oiwfsTarget.properMotion1.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.oiwfsTarget.properMotion2.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(
        rs.oiwfsTarget.epoch.value.exists(x =>
          compareDouble(x.toDouble, oiwfsTarget.epoch.epochYear)
        )
      )
      assert(rs.oiwfsTarget.parallax.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.oiwfsTarget.radialVelocity.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assertEquals(rs.oiwfsTarget.coordSystem.value, SystemDefault.some)
      assertEquals(rs.oiwfsTarget.ephemerisFile.value, "".some)

      // OIWFS probe tracking
      assert(rs.oiwfsTracking.nodAchopA.connected)
      assert(rs.oiwfsTracking.nodAchopB.connected)
      assert(rs.oiwfsTracking.nodBchopA.connected)
      assert(rs.oiwfsTracking.nodBchopB.connected)
      assertEquals(rs.oiwfsTracking.nodAchopA.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   oiwfsTracking.nodAchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodAchopB.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   oiwfsTracking.nodAchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodBchopA.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   oiwfsTracking.nodBchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodBchopB.value.flatMap(Enumerated[BinaryOnOff].fromTag),
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
      assertEquals(rs.slew.zeroChopThrow.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.zeroSourceOffset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.zeroSourceDiffTrack.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.zeroMountOffset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.zeroMountDiffTrack.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(
        rs.slew.shortcircuitTargetFilter.value.flatMap(Enumerated[BinaryOnOff].fromTag),
        BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.shortcircuitMountFilter.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.resetPointing.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.stopGuide.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.zeroGuideOffset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.zeroInstrumentOffset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.autoparkPwfs1.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.autoparkPwfs2.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.autoparkOiwfs.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(rs.slew.autoparkGems.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(rs.slew.autoparkAowfs.value.flatMap(Enumerated[BinaryOnOff].fromTag),
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

      // Rotator configuration
      assert(rs.rotator.ipa.connected)
      assert(rs.rotator.system.connected)
      assert(rs.rotator.equinox.connected)
      assert(
        rs.rotator.ipa.value.exists(x => compareDouble(x.toDouble, Angle.Angle90.toDoubleDegrees))
      )
      assert(rs.rotator.system.value.exists(_ === SystemDefault))
      assert(rs.rotator.equinox.value.exists(_ === EquinoxDefault))
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
      wavelength = Wavelength.fromIntPicometers(600 * 1000),
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
      assert(rs.oiwfsTarget.objectName.connected)
      assert(rs.oiwfsTarget.brightness.connected)
      assert(rs.oiwfsTarget.coord1.connected)
      assert(rs.oiwfsTarget.coord2.connected)
      assert(rs.oiwfsTarget.properMotion1.connected)
      assert(rs.oiwfsTarget.properMotion2.connected)
      assert(rs.oiwfsTarget.epoch.connected)
      assert(rs.oiwfsTarget.equinox.connected)
      assert(rs.oiwfsTarget.parallax.connected)
      assert(rs.oiwfsTarget.radialVelocity.connected)
      assert(rs.oiwfsTarget.coordSystem.connected)
      assert(rs.oiwfsTarget.ephemerisFile.connected)
      assertEquals(rs.oiwfsTarget.objectName.value, oiwfsTarget.objectName.some)
      assert(
        rs.oiwfsTarget.coord1.value.exists(x =>
          compareDouble(x.toDouble, oiwfsTarget.coordinates.ra.toHourAngle.toDoubleHours)
        )
      )
      assert(
        rs.oiwfsTarget.coord2.value.exists(x =>
          compareDouble(x.toDouble, oiwfsTarget.coordinates.dec.toAngle.toDoubleDegrees)
        )
      )
      assert(rs.oiwfsTarget.properMotion1.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.oiwfsTarget.properMotion2.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(
        rs.oiwfsTarget.epoch.value.exists(x =>
          compareDouble(x.toDouble, oiwfsTarget.epoch.epochYear)
        )
      )
      assert(rs.oiwfsTarget.parallax.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assert(rs.oiwfsTarget.radialVelocity.value.exists(x => compareDouble(x.toDouble, 0.0)))
      assertEquals(rs.oiwfsTarget.coordSystem.value, SystemDefault.some)
      assertEquals(rs.oiwfsTarget.ephemerisFile.value, "".some)
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
      assertEquals(rs.oiwfsTracking.nodAchopA.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   trackingConfig.nodAchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodAchopB.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   trackingConfig.nodAchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodBchopA.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   trackingConfig.nodBchopA.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
      assertEquals(rs.oiwfsTracking.nodBchopB.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   trackingConfig.nodBchopB.fold(BinaryOnOff.On, BinaryOnOff.Off).some
      )
    }

  }

  test("oiwfs probe park command") {
    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.oiwfsPark
      rs       <- st.get
    } yield {
      assert(rs.oiwfsProbe.parkDir.connected)
      assertEquals(rs.oiwfsProbe.parkDir.value, CadDirective.MARK.some)
    }
  }

  test("oiwfs probe follow command") {
    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.oiwfsFollow(true)
      r1       <- st.get
      _        <- ctr.oiwfsFollow(false)
      r2       <- st.get
    } yield {
      assert(r1.oiwfsProbe.follow.connected)
      assertEquals(r1.oiwfsProbe.follow.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r2.oiwfsProbe.follow.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
    }
  }

  test("Enable and disable guiding default gains") {
    val guideCfg = TelescopeGuideConfig(
      mountGuide = true,
      m1Guide = M1GuideConfig.M1GuideOn(M1Source.Oiwfs),
      m2Guide = M2GuideOn(true, Set(TipTiltSource.Oiwfs)),
      dayTimeMode = false
    )

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.enableGuide(guideCfg)
      r1       <- st.get
      _        <- ctr.disableGuide
      r2       <- st.get
    } yield {
      assert(r1.m1Guide.connected)
      assert(r1.m1GuideConfig.source.connected)
      assert(r1.m1GuideConfig.frames.connected)
      assert(r1.m1GuideConfig.weighting.connected)
      assert(r1.m1GuideConfig.filename.connected)
      assert(r1.m2Guide.connected)
      assert(r1.m2GuideConfig.source.connected)
      assert(r1.m2GuideConfig.beam.connected)
      assert(r1.m2GuideConfig.filter.connected)
      assert(r1.m2GuideConfig.samplefreq.connected)
      assert(r1.m2GuideConfig.reset.connected)
      assert(r1.m2GuideMode.connected)
      assert(r1.m2GuideReset.connected)
      assert(r1.mountGuide.mode.connected)
      assert(r1.mountGuide.source.connected)
      assert(r1.guiderGains.p1TipGain.connected)
      assert(r1.guiderGains.p1TiltGain.connected)
      assert(r1.guiderGains.p1FocusGain.connected)
      assert(r1.guiderGains.p2TipGain.connected)
      assert(r1.guiderGains.p2TiltGain.connected)
      assert(r1.guiderGains.p2FocusGain.connected)
      assert(r1.guiderGains.oiTipGain.connected)
      assert(r1.guiderGains.oiTiltGain.connected)
      assert(r1.guiderGains.oiFocusGain.connected)

      assertEquals(r1.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m1GuideConfig.source.value.flatMap(Enumerated[M1Source].fromTag),
                   M1Source.Oiwfs.some
      )
      assertEquals(r1.m1GuideConfig.frames.value.flatMap(_.toIntOption), 1.some)
      assertEquals(r1.m1GuideConfig.weighting.value, "none".some)
      assertEquals(r1.m1GuideConfig.filename.value, "".some)
      assertEquals(r1.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m2GuideConfig.source.value.flatMap(Enumerated[TipTiltSource].fromTag),
                   TipTiltSource.Oiwfs.some
      )
      assertEquals(r1.m2GuideConfig.beam.value, "B".some)
      assertEquals(r1.m2GuideConfig.filter.value, "raw".some)
      assertEquals(r1.m2GuideConfig.samplefreq.value.flatMap(_.toDoubleOption), 200.0.some)
      assertEquals(r1.m2GuideConfig.reset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(r1.m2GuideMode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r1.m2GuideReset.value, CadDirective.MARK.some)
      assertEquals(r1.mountGuide.mode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r1.mountGuide.source.value, "SCS".some)

      assertEquals(r2.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.Off.some)
      assertEquals(r2.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.Off.some)
      assertEquals(r2.mountGuide.mode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(r1.guiderGains.p1TipGain.value, "0.03".some)
      assertEquals(r1.guiderGains.p1TiltGain.value, "0.03".some)
      assertEquals(r1.guiderGains.p1FocusGain.value, "2.0E-5".some)

      assertEquals(r1.guiderGains.p2TipGain.value, "0.05".some)
      assertEquals(r1.guiderGains.p2TiltGain.value, "0.05".some)
      assertEquals(r1.guiderGains.p2FocusGain.value, "1.0E-4".some)

      assertEquals(r1.guiderGains.oiTipGain.value, "0.08".some)
      assertEquals(r1.guiderGains.oiTiltGain.value, "0.08".some)
      assertEquals(r1.guiderGains.oiFocusGain.value, "1.5E-4".some)
    }
  }

  test("Enable and disable guiding day mode") {
    val guideCfg = TelescopeGuideConfig(
      mountGuide = true,
      m1Guide = M1GuideConfig.M1GuideOn(M1Source.Oiwfs),
      m2Guide = M2GuideOn(true, Set(TipTiltSource.Oiwfs)),
      dayTimeMode = true
    )

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.enableGuide(guideCfg)
      r1       <- st.get
      _        <- ctr.disableGuide
      r2       <- st.get
    } yield {
      assert(r1.m1Guide.connected)
      assert(r1.m1GuideConfig.source.connected)
      assert(r1.m1GuideConfig.frames.connected)
      assert(r1.m1GuideConfig.weighting.connected)
      assert(r1.m1GuideConfig.filename.connected)
      assert(r1.m2Guide.connected)
      assert(r1.m2GuideConfig.source.connected)
      assert(r1.m2GuideConfig.beam.connected)
      assert(r1.m2GuideConfig.filter.connected)
      assert(r1.m2GuideConfig.samplefreq.connected)
      assert(r1.m2GuideConfig.reset.connected)
      assert(r1.m2GuideMode.connected)
      assert(r1.m2GuideReset.connected)
      assert(r1.mountGuide.mode.connected)
      assert(r1.mountGuide.source.connected)
      assert(r1.guiderGains.p1TipGain.connected)
      assert(r1.guiderGains.p1TiltGain.connected)
      assert(r1.guiderGains.p1FocusGain.connected)
      assert(r1.guiderGains.p2TipGain.connected)
      assert(r1.guiderGains.p2TiltGain.connected)
      assert(r1.guiderGains.p2FocusGain.connected)
      assert(r1.guiderGains.oiTipGain.connected)
      assert(r1.guiderGains.oiTiltGain.connected)
      assert(r1.guiderGains.oiFocusGain.connected)

      assertEquals(r1.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m1GuideConfig.source.value.flatMap(Enumerated[M1Source].fromTag),
                   M1Source.Oiwfs.some
      )
      assertEquals(r1.m1GuideConfig.frames.value.flatMap(_.toIntOption), 1.some)
      assertEquals(r1.m1GuideConfig.weighting.value, "none".some)
      assertEquals(r1.m1GuideConfig.filename.value, "".some)
      assertEquals(r1.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m2GuideConfig.source.value.flatMap(Enumerated[TipTiltSource].fromTag),
                   TipTiltSource.Oiwfs.some
      )
      assertEquals(r1.m2GuideConfig.beam.value, "B".some)
      assertEquals(r1.m2GuideConfig.filter.value, "raw".some)
      assertEquals(r1.m2GuideConfig.samplefreq.value.flatMap(_.toDoubleOption), 200.0.some)
      assertEquals(r1.m2GuideConfig.reset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(r1.m2GuideMode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r1.m2GuideReset.value, CadDirective.MARK.some)
      assertEquals(r1.mountGuide.mode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r1.mountGuide.source.value, "SCS".some)

      assertEquals(r2.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.Off.some)
      assertEquals(r2.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.Off.some)
      assertEquals(r2.mountGuide.mode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(r1.guiderGains.p1TipGain.value, "0.0".some)
      assertEquals(r1.guiderGains.p1TiltGain.value, "0.0".some)
      assertEquals(r1.guiderGains.p1FocusGain.value, "0.0".some)

      assertEquals(r1.guiderGains.p2TipGain.value, "0.0".some)
      assertEquals(r1.guiderGains.p2TiltGain.value, "0.0".some)
      assertEquals(r1.guiderGains.p2FocusGain.value, "0.0".some)

      assertEquals(r1.guiderGains.oiTipGain.value, "0.0".some)
      assertEquals(r1.guiderGains.oiTiltGain.value, "0.0".some)
      assertEquals(r1.guiderGains.oiFocusGain.value, "0.0".some)
    }
  }

  test("Enable and disable guiding") {
    val guideCfg = TelescopeGuideConfig(
      mountGuide = true,
      m1Guide = M1GuideConfig.M1GuideOn(M1Source.Oiwfs),
      m2Guide = M2GuideOn(true, Set(TipTiltSource.Oiwfs)),
      false
    )

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.enableGuide(guideCfg)
      r1       <- st.get
      _        <- ctr.disableGuide
      r2       <- st.get
    } yield {
      assert(r1.m1Guide.connected)
      assert(r1.m1GuideConfig.source.connected)
      assert(r1.m1GuideConfig.frames.connected)
      assert(r1.m1GuideConfig.weighting.connected)
      assert(r1.m1GuideConfig.filename.connected)
      assert(r1.m2Guide.connected)
      assert(r1.m2GuideConfig.source.connected)
      assert(r1.m2GuideConfig.beam.connected)
      assert(r1.m2GuideConfig.filter.connected)
      assert(r1.m2GuideConfig.samplefreq.connected)
      assert(r1.m2GuideConfig.reset.connected)
      assert(r1.m2GuideMode.connected)
      assert(r1.m2GuideReset.connected)
      assert(r1.mountGuide.mode.connected)
      assert(r1.mountGuide.source.connected)

      assertEquals(r1.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m1GuideConfig.source.value.flatMap(Enumerated[M1Source].fromTag),
                   M1Source.Oiwfs.some
      )
      assertEquals(r1.m1GuideConfig.frames.value.flatMap(_.toIntOption), 1.some)
      assertEquals(r1.m1GuideConfig.weighting.value, "none".some)
      assertEquals(r1.m1GuideConfig.filename.value, "".some)
      assertEquals(r1.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.On.some)
      assertEquals(r1.m2GuideConfig.source.value.flatMap(Enumerated[TipTiltSource].fromTag),
                   TipTiltSource.Oiwfs.some
      )
      assertEquals(r1.m2GuideConfig.beam.value, "B".some)
      assertEquals(r1.m2GuideConfig.filter.value, "raw".some)
      assertEquals(r1.m2GuideConfig.samplefreq.value.flatMap(_.toDoubleOption), 200.0.some)
      assertEquals(r1.m2GuideConfig.reset.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
      assertEquals(r1.m2GuideMode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r1.m2GuideReset.value, CadDirective.MARK.some)
      assertEquals(r1.mountGuide.mode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.On.some
      )
      assertEquals(r1.mountGuide.source.value, "SCS".some)

      assertEquals(r2.m1Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.Off.some)
      assertEquals(r2.m2Guide.value.flatMap(Enumerated[BinaryOnOff].fromTag), BinaryOnOff.Off.some)
      assertEquals(r2.mountGuide.mode.value.flatMap(Enumerated[BinaryOnOff].fromTag),
                   BinaryOnOff.Off.some
      )
    }
  }

  test("Start OIWFS exposures") {
    val testVal = TimeSpan.unsafeFromMicroseconds(12345)

    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.oiwfsObserve(testVal, true)
      rs       <- st.get
    } yield {
      assert(rs.oiWfs.observe.path.connected)
      assert(rs.oiWfs.observe.label.connected)
      assert(rs.oiWfs.observe.output.connected)
      assert(rs.oiWfs.observe.options.connected)
      assert(rs.oiWfs.observe.fileName.connected)
      assert(rs.oiWfs.observe.interval.connected)
      assert(rs.oiWfs.observe.numberOfExposures.connected)
      assertEquals(rs.oiWfs.observe.interval.value.flatMap(_.toDoubleOption),
                   testVal.toSeconds.toDouble.some
      )
      assertEquals(rs.oiWfs.observe.numberOfExposures.value.flatMap(_.toIntOption), -1.some)
    }
  }

  test("Stop OIWFS exposures") {
    for {
      x        <- createController
      (st, ctr) = x
      _        <- ctr.oiwfsStopObserve
      rs       <- st.get
    } yield {
      assert(rs.oiWfs.stop.connected)
      assertEquals(rs.oiWfs.stop.value, CadDirective.MARK.some)
    }
  }

  test("Read guide state") {
    val testValue1 = GuideConfigState(
      pwfs1Integrating = TestChannel.State.of(BinaryYesNo.No),
      pwfs2Integrating = TestChannel.State.of(BinaryYesNo.No),
      oiwfsIntegrating = TestChannel.State.of(BinaryYesNo.Yes),
      m2State = TestChannel.State.of(BinaryOnOff.On),
      absorbTipTilt = TestChannel.State.of(1),
      m2ComaCorrection = TestChannel.State.of(BinaryOnOff.On),
      m1State = TestChannel.State.of(BinaryOnOff.On),
      m1Source = TestChannel.State.of("OIWFS"),
      p1ProbeGuide = TestChannel.State.of(0.0),
      p2ProbeGuide = TestChannel.State.of(0.0),
      oiProbeGuide = TestChannel.State.of(0.0),
      p1ProbeGuided = TestChannel.State.of(0.0),
      p2ProbeGuided = TestChannel.State.of(0.0),
      oiProbeGuided = TestChannel.State.of(0.0),
      mountP1Weight = TestChannel.State.of(0.0),
      mountP2Weight = TestChannel.State.of(0.0),
      m2P1Guide = TestChannel.State.of("OFF"),
      m2P2Guide = TestChannel.State.of("OFF"),
      m2OiGuide = TestChannel.State.of("RAW A-AUTO B-OFF C-OFF"),
      m2AoGuide = TestChannel.State.of("OFF")
    )

    val testGuide = GuideState(
      true,
      M1GuideConfig.M1GuideOn(M1Source.Oiwfs),
      M2GuideConfig.M2GuideOn(true, Set(TipTiltSource.Oiwfs))
    )

    for {
      x        <- createController
      (st, ctr) = x
      _        <- st.update(_.focus(_.guideStatus).replace(testValue1))
      g        <- ctr.getGuideState
      r1       <- st.get
    } yield {
      assert(r1.guideStatus.m2State.connected)
      assert(r1.guideStatus.absorbTipTilt.connected)
      assert(r1.guideStatus.m2ComaCorrection.connected)
      assert(r1.guideStatus.m1State.connected)
      assert(r1.guideStatus.m1Source.connected)
      assert(r1.guideStatus.m2OiGuide.connected)
      assertEquals(g, testGuide)
    }
  }

  def createController: IO[(Ref[IO, TestTcsEpicsSystem.State], TcsBaseControllerEpics[IO])] =
    Ref.of[IO, TestTcsEpicsSystem.State](TestTcsEpicsSystem.defaultState).map { st =>
      val sys = TestTcsEpicsSystem.build(st)
      (st, new TcsBaseControllerEpics[IO](sys, DefaultTimeout))
    }

}
