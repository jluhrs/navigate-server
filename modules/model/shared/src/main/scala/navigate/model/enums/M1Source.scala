// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

/** Enumerated type for M1 Source. */
sealed abstract class M1Source(val tag: String) extends Product with Serializable

object M1Source {
  case object Pwfs1 extends M1Source("Pwfs1")
  case object Pwfs2 extends M1Source("Pwfs2")
  case object Oiwfs extends M1Source("Oiwfs")
  case object Gaos  extends M1Source("Gaos")
  case object Hrwfs extends M1Source("Hrwfs")

  /** @group Typeclass Instances */
  given Enumerated[M1Source] =
    Enumerated.from(Oiwfs).withTag(_.tag)

}
