// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

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
