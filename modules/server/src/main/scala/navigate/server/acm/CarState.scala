// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.acm

import cats.Eq
import lucuma.core.util.Enumerated

sealed abstract class CarState(val tag: String) extends Product with Serializable

object CarState {
  case object Idle   extends CarState("idle")
  case object Paused extends CarState("paused")
  case object Busy   extends CarState("busy")
  case object Error  extends CarState("error")

  given Eq[CarState] = Eq.fromUniversalEquals

  given Enumerated[CarState] =
    Enumerated.from(Idle, Paused, Busy, Idle).withTag(_.tag)
}
