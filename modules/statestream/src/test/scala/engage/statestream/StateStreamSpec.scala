// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.statestream

import cats.data.State
import cats.effect.IO
import cats.syntax.eq._
import munit.CatsEffectSuite
import fs2.Stream

class StateStreamSpec extends CatsEffectSuite {
  import StateStreamSpec._
  test("statestream") {
    assertIOBoolean {
      val i = Stream[IO, Int](1, 2, 3)

      val p = new StateStream[IO, TestState, Int, Msg]((x: Int) =>
        State[TestState, Msg] { s =>
          val c = s.getOrElse(x, 0)
          (
            s.updated(x, c + 1),
            Msg(x, x.toString)
          )
        }
      ).compile(Map.empty[Int, Int])

      p(i).compile.toList.map(l => check(l.map(_._2)))
    }
  }

  private def check(r: List[Msg]): Boolean =
    r.forall(m => m.from.toString === m.txt)

}

object StateStreamSpec {
  final case class Msg(from: Int, txt: String)
  type TestState = Map[Int, Int]
}
