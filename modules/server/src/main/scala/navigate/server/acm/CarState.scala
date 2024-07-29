// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.acm

import lucuma.core.util.Enumerated

enum CarState(val tag: String) extends Product with Serializable derives Enumerated {
  case Idle   extends CarState("idle")
  case Paused extends CarState("paused")
  case Busy   extends CarState("busy")
  case Error  extends CarState("error")
}
