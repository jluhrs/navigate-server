// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import cats.Eq
import lucuma.core.util.Enumerated

abstract class ShutterMode(val tag: String) extends Product with Serializable

object ShutterMode {
  case object FullyOpen extends ShutterMode("FullyOpen")
  case object Tracking  extends ShutterMode("Tracking")

  given Eq[ShutterMode] = Eq.instance {
    case (FullyOpen, FullyOpen) => true
    case (Tracking, Tracking)   => true
    case _                      => false
  }

  given Enumerated[ShutterMode] =
    Enumerated.from(FullyOpen, Tracking).withTag(_.tag)
}
