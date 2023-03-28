// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.epics

import cats.Eq
import lucuma.core.util.Enumerated

sealed abstract class TestEnumerated(val tag: String) extends Product with Serializable

object TestEnumerated {

  case object VAL0 extends TestEnumerated("val0")
  case object VAL1 extends TestEnumerated("val1")
  case object VAL2 extends TestEnumerated("val1")

  given Eq[TestEnumerated] = Eq.fromUniversalEquals

  given Enumerated[TestEnumerated] =
    Enumerated.from[TestEnumerated](VAL0, VAL1, VAL2).withTag(_.tag)

}
