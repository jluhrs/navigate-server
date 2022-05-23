package engage.server.acm

import cats.Eq
import lucuma.core.util.Enumerated

sealed trait CadDirective extends Product with Serializable

object CadDirective {
  case object MARK   extends CadDirective
  case object CLEAR  extends CadDirective
  case object PRESET extends CadDirective
  case object START  extends CadDirective
  case object STOP   extends CadDirective

  implicit val cadDirectiveEq: Eq[CadDirective] = Eq.fromUniversalEquals

  implicit val cadDirectiveEnum: Enumerated[CadDirective] =
    Enumerated.of(MARK, CLEAR, PRESET, START, STOP)
}
