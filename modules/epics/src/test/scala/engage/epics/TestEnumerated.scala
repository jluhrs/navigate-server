// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.Eq
import lucuma.core.util.Enumerated

sealed trait TestEnumerated extends Product with Serializable

object TestEnumerated {

  case object VAL0 extends TestEnumerated
  case object VAL1 extends TestEnumerated
  case object VAL2 extends TestEnumerated

  implicit val testEq: Eq[TestEnumerated] = Eq.fromUniversalEquals

  implicit val testEnumerated: Enumerated[TestEnumerated] =
    Enumerated.from[TestEnumerated](VAL0, VAL1, VAL2).withTag(_ => "TestEnumerated")

}
