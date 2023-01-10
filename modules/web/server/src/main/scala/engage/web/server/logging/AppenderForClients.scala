// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server.logging

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.syntax.all._
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import engage.model.enums.ServerLogLevel
import fs2.concurrent.Topic

import java.time.Instant

/**
 * Custom appender that can take log events from logback and send them to clients via the common
 * pipe/WebSockets
 *
 * This is out of the scala/http4s loop
 */
class AppenderForClients[O: LogMessageBuilder](out: Topic[IO, O])(dispatcher: Dispatcher[IO])
    extends AppenderBase[ILoggingEvent] {
  // Remove some loggers. This is a weak form of protection where he don't send some
  // loggers to the client, e.g. security related logs
  private val blackListedLoggers = List(""".*\.security\..*""".r)

  override def append(event: ILoggingEvent): Unit = {
    // Convert to a engage model to send to clients
    val level     = event.getLevel match {
      case Level.INFO  => ServerLogLevel.INFO.some
      case Level.WARN  => ServerLogLevel.WARN.some
      case Level.ERROR => ServerLogLevel.ERROR.some
      case _           => none
    }
    val timestamp = Instant.ofEpochMilli(event.getTimeStamp)

    // Send a message to the clients if level is INFO or higher
    // We are outside the normal execution loop, thus we need to call unsafePerformSync directly
    dispatcher.unsafeRunAndForget(
      level
        .filter(_ => !blackListedLoggers.exists(_.findFirstIn(event.getLoggerName).isDefined))
        .fold(IO.pure(()))(l =>
          out.publish1(LogMessageBuilder[O].build(l, timestamp, event.getMessage)).void
        )
    )
  }
}
