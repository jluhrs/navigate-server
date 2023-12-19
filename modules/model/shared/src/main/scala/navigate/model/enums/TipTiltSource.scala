// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import cats.Show
import cats.derived.*
import lucuma.core.util.Enumerated

/** Enumerated type for Tip/Tilt Source. */
sealed abstract class TipTiltSource(val tag: String) extends Product with Serializable derives Show

object TipTiltSource {
// Commented values will be restored when implementing support for each guider
  case object PWFS1 extends TipTiltSource("PWFS1")
  case object PWFS2 extends TipTiltSource("PWFS2")
  case object OIWFS extends TipTiltSource("OIWFS")
  case object GAOS  extends TipTiltSource("GAOS")

  given Enumerated[TipTiltSource] =
    Enumerated.from(OIWFS).withTag(_.tag)
}
