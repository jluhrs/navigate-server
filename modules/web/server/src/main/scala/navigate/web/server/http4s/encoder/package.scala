// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import ch.qos.logback.classic.spi.ILoggingEvent
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.*
import lucuma.core.util.Timestamp

package object encoder {

  given Encoder[ILoggingEvent] = e =>
    Json.obj(
      "timestamp" -> Timestamp.fromInstant(e.getInstant).getOrElse(Timestamp.Min).asJson,
      "level"     -> e.getLevel.toString.asJson,
      "thread"    -> e.getThreadName.asJson,
      "message"   -> e.getFormattedMessage.asJson
    )

}
