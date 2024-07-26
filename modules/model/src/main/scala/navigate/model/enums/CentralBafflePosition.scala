// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import cats.Eq
import cats.derived.*
import lucuma.core.util.Enumerated

sealed abstract class CentralBafflePosition(val tag: String) extends Product with Serializable
    derives Eq

object CentralBafflePosition {
  case object Open   extends CentralBafflePosition("Open")
  case object Closed extends CentralBafflePosition("Closed")

  given Enumerated[CentralBafflePosition] = Enumerated.from(Open, Closed).withTag(_.tag)
}
