// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.statestream

import cats.effect.IO
import cats.syntax.all._
import munit.CatsEffectSuite

class StateEngineSpec extends CatsEffectSuite {

  test("StateEngine should process commands") {
    assertIO(
      for {
        eng <- StateEngine.build[IO, Int, String]
        _   <- eng.modifyState(x => (x + 1, x.toString).pure[IO])
        _   <- eng.getState(x => x.toString.pure[IO])
        _   <- eng.setState((5, "X").pure[IO])
        _   <- eng.getState(x => x.toString.pure[IO])
        out <- eng.process(0).take(4).compile.toList
      } yield out,
      List("0", "1", "X", "5")
    )
  }

}
