// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import cats.effect.{ IO, Ref }
import munit.CatsEffectSuite

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class TcsBaseControllerEpicsSpec extends CatsEffectSuite {

  private val DefaultTimeout: FiniteDuration = FiniteDuration(1, TimeUnit.SECONDS)

  test("Mount commands") {
    for {
      st  <- Ref.of[IO, TestTcsEpicsSystem.State](TestTcsEpicsSystem.defaultState)
      out <- Ref.of[IO, List[TestTcsEpicsSystem.TestTcsEvent]](List.empty)
      sys  = TestTcsEpicsSystem(st, out)
      _   <- sys.startCommand(DefaultTimeout).mcsParkCmd.mark.post.run
      _   <- sys.startCommand(DefaultTimeout).mcsFollowCommand.setFollow(enable = true).post.run
      rs  <- st.get
      ro  <- out.get
    } yield {
      assert(rs.mountParked)
      assert(ro.contains(TestTcsEpicsSystem.TestTcsEvent.MountParkCmd))
      assert(rs.mountFollowS)
      assert(ro.contains(TestTcsEpicsSystem.TestTcsEvent.MountFollowCmd(true)))
    }
  }

}
