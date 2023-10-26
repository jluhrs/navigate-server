// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import lucuma.core.util.Enumerated

sealed abstract class RotatorTrackingMode(val tag: String) extends Product with Serializable

object RotatorTrackingMode {
  
  case object Tracking extends RotatorTrackingMode("Tracking")
  case object Fixed extends RotatorTrackingMode("Fixed")
  
  given Enumerated[RotatorTrackingMode] = Enumerated.from(Tracking, Fixed).withTag(_.tag)

}