// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.model.config

import cats.syntax.all._
import lucuma.core.util.Enumerated

sealed trait ControlStrategy extends Product with Serializable

object ControlStrategy {
  // System will be fully controlled by Engage
  case object FullControl extends ControlStrategy
  // Engage connects to system, but only to read values
  case object ReadOnly    extends ControlStrategy
  // All system interactions are internally simulated
  case object Simulated   extends ControlStrategy

  def fromString(v: String): Option[ControlStrategy] = v match {
    case "full"      => Some(FullControl)
    case "readOnly"  => Some(ReadOnly)
    case "simulated" => Some(Simulated)
    case _           => None
  }

  implicit val ControlStrategyEnumerated: Enumerated[ControlStrategy] =
    Enumerated.of(FullControl, ReadOnly, Simulated)

  implicit class ControlStrategyOps(v: ControlStrategy) {
    val connect: Boolean      = v =!= ControlStrategy.Simulated
    // If connected, then use real values for keywords
    val realKeywords: Boolean = connect
    val command: Boolean      = v === ControlStrategy.FullControl
  }

}
