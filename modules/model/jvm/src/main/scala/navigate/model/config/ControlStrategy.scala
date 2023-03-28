// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.config

import cats.syntax.all._
import lucuma.core.util.Enumerated

sealed abstract class ControlStrategy(val tag: String) extends Product with Serializable

object ControlStrategy {
  // System will be fully controlled by Navigate
  case object FullControl extends ControlStrategy("full")
  // Navigate connects to system, but only to read values
  case object ReadOnly    extends ControlStrategy("readOnly")
  // All system interactions are internally simulated
  case object Simulated   extends ControlStrategy("simulated")

  def fromString(v: String): Option[ControlStrategy] = v match {
    case "full"      => Some(FullControl)
    case "readOnly"  => Some(ReadOnly)
    case "simulated" => Some(Simulated)
    case _           => None
  }

  given Enumerated[ControlStrategy] =
    Enumerated.from(FullControl, ReadOnly, Simulated).withTag(_.tag)

  extension (v: ControlStrategy) {
    def connect: Boolean      = v =!= ControlStrategy.Simulated
    // If connected, then use real values for keywords
    def realKeywords: Boolean = connect
    def command: Boolean      = v === ControlStrategy.FullControl
  }

}
