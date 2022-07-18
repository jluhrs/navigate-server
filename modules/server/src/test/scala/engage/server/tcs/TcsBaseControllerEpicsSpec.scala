// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import cats.effect.{ IO, Ref }
import engage.server.acm.CadDirective
import engage.server.epicsdata.BinaryYesNo
import munit.CatsEffectSuite
import squants.space.AngleConversions._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class TcsBaseControllerEpicsSpec extends CatsEffectSuite {

  private val DefaultTimeout: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS)

  test("Mount commands") {
    for {
      st <- Ref.of[IO, TestTcsEpicsSystem.State](TestTcsEpicsSystem.defaultState)
      sys = TestTcsEpicsSystem.build(st)
      _  <- sys.startCommand(DefaultTimeout).mcsParkCommand.mark.post.verifiedRun(DefaultTimeout)
      _  <- sys
              .startCommand(DefaultTimeout)
              .mcsFollowCommand
              .setFollow(enable = true)
              .post
              .verifiedRun(DefaultTimeout)
      rs <- st.get
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
      st <- Ref.of[IO, TestTcsEpicsSystem.State](TestTcsEpicsSystem.defaultState)
      sys = TestTcsEpicsSystem.build(st)
      _  <- sys.startCommand(DefaultTimeout).rotParkCommand.mark.post.verifiedRun(DefaultTimeout)
      _  <- sys
              .startCommand(DefaultTimeout)
              .rotFollowCommand
              .setFollow(enable = true)
              .post
              .verifiedRun(DefaultTimeout)
      _  <- sys
              .startCommand(DefaultTimeout)
              .rotStopCommand
              .setBrakes(enable = true)
              .post
              .verifiedRun(DefaultTimeout)
      _  <- sys
              .startCommand(DefaultTimeout)
              .rotMoveCommand
              .setAngle(testAngle)
              .post
              .verifiedRun(DefaultTimeout)
      rs <- st.get
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

}
