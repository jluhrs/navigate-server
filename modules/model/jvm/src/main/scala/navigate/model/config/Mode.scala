// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.config

import lucuma.core.util.Enumerated

/**
 * Operating mode of the navigate, development or production
 */
sealed abstract class Mode(val tag: String) extends Product with Serializable

object Mode {
  case object Production  extends Mode("production")
  case object Development extends Mode("development")

  given Enumerated[Mode] =
    Enumerated.from(Production, Development).withTag(_.tag)

}
