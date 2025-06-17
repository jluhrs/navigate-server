// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import navigate.model.FocalPlaneOffset

case class TargetOffsets(
  sourceA: FocalPlaneOffset,
  pwfs1:   FocalPlaneOffset,
  pwfs2:   FocalPlaneOffset,
  oiwfs:   FocalPlaneOffset
)

object TargetOffsets {
  val default: TargetOffsets = TargetOffsets(
    sourceA = FocalPlaneOffset.Zero,
    pwfs1 = FocalPlaneOffset.Zero,
    pwfs2 = FocalPlaneOffset.Zero,
    oiwfs = FocalPlaneOffset.Zero
  )
}
