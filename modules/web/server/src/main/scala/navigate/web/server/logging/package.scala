// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server

import navigate.model.NavigateEvent
import navigate.model.enums.ServerLogLevel

import java.time.Instant

package object logging {
  given LogMessageBuilder[NavigateEvent] =
    (l: ServerLogLevel, t: Instant, msg: String) => NavigateEvent.ServerLogMessage(l, t, msg)
}
