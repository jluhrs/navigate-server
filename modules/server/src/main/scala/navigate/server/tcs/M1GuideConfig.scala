// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.{Show, Eq}
import cats.syntax.all.*
import cats.derived.*
import navigate.model.enums.M1Source

/** Data type for M1 guide config. */
sealed trait M1GuideConfig extends Product with Serializable derives Eq, Show {
  def uses(s: M1Source): Boolean
}

object M1GuideConfig {
  case object M1GuideOff                       extends M1GuideConfig  derives Eq, Show  {
    override def uses(s: M1Source): Boolean = false
  }
   final case class M1GuideOn(source: M1Source) extends M1GuideConfig derives Eq, Show {
    override def uses(s: M1Source): Boolean = source === s
  }

}
