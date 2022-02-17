package engage.statestream

import cats.data.State
import cats.effect.IO
import munit.CatsEffectSuite
import fs2.Stream

class StateStreamSpec extends CatsEffectSuite {
import StateStreamSpec._
  test("statestream") {
    assertIOBoolean {
      val i = Stream[IO, Int](1, 2, 3)

      val p = StateStream.compile[IO, TestState, Int, Msg]{ x =>
        State { s =>
          val c = s.getOrElse(x, 0)
          (
            s.updated(x, c+1),
            Stream.emits[IO, Msg](List.range(0, x).map(Msg(x, _)))
          )
        }
      }(Map.empty[Int, Int])

      p(i).compile.toList.map(check)
    }
  }

  private def check(r: List[Msg]): Boolean = {
    val g = r.groupBy(_.from)
    g.get(1).exists(_.length == 1) &&
      g.get(2).exists(_.length == 2) &&
      g.get(3).exists(_.length == 3)
  }

}

object StateStreamSpec {
  final case class Msg(from: Int, seq: Int)
  type TestState = Map[Int, Int]
}

