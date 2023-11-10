// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.{Show, Eq}
import cats.syntax.all.*
import cats.derived.*
import navigate.model.enums.TipTiltSource

/** Data type for M2 guide config. */
sealed trait M2GuideConfig extends Product with Serializable derives Eq, Show {
  def uses(s: TipTiltSource): Boolean
}

object M2GuideConfig {
  case object M2GuideOff extends M2GuideConfig derives Eq, Show {
    override def uses(s: TipTiltSource): Boolean = false
  }
  case class M2GuideOn(coma: Boolean, sources: Set[TipTiltSource]) extends M2GuideConfig derives Eq, Show {
    override def uses(s: TipTiltSource): Boolean = sources.contains(s)
  }
}
