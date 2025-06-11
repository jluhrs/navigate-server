// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model

import lucuma.core.math.Angle
import lucuma.core.math.Offset

sealed trait HandsetAdjustment

object HandsetAdjustment {
  case class HorizontalAdjustment(deltaAz: Angle, deltaEl: Angle)  extends HandsetAdjustment
  case class FocalPlaneAdjustment(value: FocalPlaneOffset)         extends HandsetAdjustment
  case class InstrumentAdjustment(value: Offset)                   extends HandsetAdjustment
  case class EquatorialAdjustment(deltaRA: Angle, deltaDec: Angle) extends HandsetAdjustment
}
