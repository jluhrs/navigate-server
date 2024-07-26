// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import cats.Eq
import cats.derived.*
import lucuma.core.util.Enumerated

sealed abstract class DeployableBafflePosition(val tag: String) extends Product with Serializable
    derives Eq

object DeployableBafflePosition {
  case object ThermalIR extends DeployableBafflePosition("ThermalIR")
  case object NearIR    extends DeployableBafflePosition("NearIR")
  case object Visible   extends DeployableBafflePosition("Visible")
  case object Extended  extends DeployableBafflePosition("Extended")

  given Enumerated[DeployableBafflePosition] =
    Enumerated.from(ThermalIR, NearIR, Visible, Extended).withTag(_.tag)
}
