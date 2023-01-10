// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server

import engage.model.EngageEvent
import engage.model.enums.ServerLogLevel

import java.time.Instant

package object logging {
  implicit val engageEventLogBuilder: LogMessageBuilder[EngageEvent] =
    (l: ServerLogLevel, t: Instant, msg: String) => EngageEvent.ServerLogMessage(l, t, msg)
}
