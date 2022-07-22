// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import cats.effect.{ IO, Ref }
import cats.syntax.option._
import engage.model.enums.{ DomeMode, ShutterMode }
import engage.server.acm.CadDirective
import engage.server.epicsdata.{ BinaryOnOff, BinaryYesNo }
import munit.CatsEffectSuite
import squants.space.AngleConversions._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class TcsBaseControllerEpicsSpec extends CatsEffectSuite {

  private val DefaultTimeout: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS)

  test("Mount commands") {
    for {
      (st, ctr) <- createController
      _         <- ctr.mcsPark
      _         <- ctr.mcsFollow(enable = true)
      rs        <- st.get
    } yield {
      assert(rs.telescopeParkDir.connected)
      assertEquals(rs.telescopeParkDir.value.get, CadDirective.MARK)
      assert(rs.mountFollow.connected)
      assert(rs.mountFollow.value.get)
    }
  }

  test("Rotator commands") {
    val testAngle = 123.456.degrees

    for {
      (st, ctr) <- createController
      _         <- ctr.rotPark
      _         <- ctr.rotFollow(enable = true)
      _         <- ctr.rotStop(useBrakes = true)
      _         <- ctr.rotMove(testAngle)
      rs        <- st.get
    } yield {
      assert(rs.rotParkDir.connected)
      assertEquals(rs.rotParkDir.value.get, CadDirective.MARK)
      assert(rs.rotFollow.connected)
      assert(rs.rotFollow.value.get)
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
      (st, ctr) <- createController
      _         <- ctr.ecsCarouselMode(DomeMode.MinVibration,
                                       ShutterMode.Tracking,
                                       testHeight,
                                       domeEnable = true,
                                       shutterEnable = true
                   )
      _         <- ctr.ecsVentGatesMove(testVentEast, testVentWest)
      rs        <- st.get
    } yield {
      assert(rs.ecsDomeMode.connected)
      assert(rs.ecsShutterMode.connected)
      assert(rs.ecsSlitHeight.connected)
      assert(rs.ecsDomeEnable.connected)
      assert(rs.ecsShutterEnable.connected)
      assert(rs.ecsVentGateEast.connected)
      assert(rs.ecsVentGateWest.connected)
      assertEquals(rs.ecsDomeMode.value, DomeMode.MinVibration.some)
      assertEquals(rs.ecsShutterMode.value, ShutterMode.Tracking.some)
      assertEquals(rs.ecsSlitHeight.value, testHeight.some)
      assertEquals(rs.ecsDomeEnable.value, BinaryOnOff.On.some)
      assertEquals(rs.ecsShutterEnable.value, BinaryOnOff.On.some)
      assertEquals(rs.ecsVentGateEast.value, testVentEast.some)
      assertEquals(rs.ecsVentGateWest.value, testVentWest.some)
    }

  }

  def createController: IO[(Ref[IO, TestTcsEpicsSystem.State], TcsBaseControllerEpics[IO])] =
    Ref.of[IO, TestTcsEpicsSystem.State](TestTcsEpicsSystem.defaultState).map { st =>
      val sys = TestTcsEpicsSystem.build(st)
      (st, new TcsBaseControllerEpics[IO](sys, DefaultTimeout))
    }

}
