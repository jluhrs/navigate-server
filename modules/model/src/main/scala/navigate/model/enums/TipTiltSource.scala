// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import cats.Show
import cats.derived.*
import lucuma.core.util.Enumerated

/** Enumerated type for Tip/Tilt Source. */
sealed abstract class TipTiltSource(val tag: String) extends Product with Serializable derives Show

object TipTiltSource {
  case object Pwfs1 extends TipTiltSource("Pwfs1")
  case object Pwfs2 extends TipTiltSource("Pwfs2")
  case object Oiwfs extends TipTiltSource("Oiwfs")
  case object Gaos  extends TipTiltSource("Gaos")

  given Enumerated[TipTiltSource] =
    Enumerated.from(Oiwfs).withTag(_.tag)
}
