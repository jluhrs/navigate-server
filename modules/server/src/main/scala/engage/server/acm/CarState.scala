// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.acm

import cats.Eq
import lucuma.core.util.Enumerated

sealed trait CarState extends Product with Serializable

object CarState {
  case object Idle   extends CarState
  case object Paused extends CarState
  case object Busy   extends CarState
  case object Error  extends CarState

  implicit val carStateEq: Eq[CarState] = Eq.fromUniversalEquals

  implicit val carStateEnum: Enumerated[CarState] = Enumerated.of(Idle, Paused, Busy, Idle)
}
