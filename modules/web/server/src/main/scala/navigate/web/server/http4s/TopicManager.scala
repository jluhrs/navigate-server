// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.Applicative
import cats.effect.*
import cats.effect.std.Dispatcher
import cats.effect.syntax.all.*
import cats.syntax.all.*
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import fs2.Stream
import fs2.concurrent.Topic
import navigate.model.NavigateEvent
import navigate.server.NavigateEngine
import navigate.server.NavigateFailure
import navigate.server.tcs.GuideState
import navigate.server.tcs.GuidersQualityValues
import navigate.server.tcs.TelescopeState
import navigate.web.server.logging.SubscriptionAppender
import org.typelevel.log4cats.Logger

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

/**
 * Class for managing all Topic creation and initialization
 */
@annotation.nowarn("msg=Given search preference")
class TopicManager[F[_]: Spawn: Temporal] private (
  val navigateEvents: Topic[F, NavigateEvent],
  val loggingEvents:  Topic[F, ILoggingEvent],
  val guideState:     Topic[F, GuideState],
  val guidersQuality: Topic[F, GuidersQualityValues],
  val telescopeState: Topic[F, TelescopeState],
  val logBuffer:      Ref[F, Seq[ILoggingEvent]]
) {

  private def guideStatePoll(
    eng:   NavigateEngine[F],
    topic: Topic[F, GuideState]
  ): Stream[F, Unit] =
    Stream
      .fixedRate[F](FiniteDuration(1, TimeUnit.SECONDS))
      .evalMap(_ => eng.getGuideState)
      .evalMapAccumulate(none) { (acc, gs) =>
        (if (acc.contains(gs)) Applicative[F].unit else topic.publish1(gs).void).as(gs.some, ())
      }
      .void

  private def guiderQualityPoll(
    eng:   NavigateEngine[F],
    topic: Topic[F, GuidersQualityValues]
  ): Stream[F, Unit] =
    Stream
      .fixedRate[F](FiniteDuration(1, TimeUnit.SECONDS))
      .evalMap(_ => eng.getGuidersQuality)
      .evalMapAccumulate(none) { (acc, gs) =>
        (if (acc.contains(gs)) Applicative[F].unit else topic.publish1(gs).void).as(gs.some, ())
      }
      .void

  private def telescopeStatePoll(
    eng:   NavigateEngine[F],
    topic: Topic[F, TelescopeState]
  ): Stream[F, Unit] =
    Stream
      .fixedRate[F](FiniteDuration(1, TimeUnit.SECONDS))
      .evalMap(_ => eng.getTelescopeState)
      .evalMapAccumulate(none) { (acc, ts) =>
        (if (acc.contains(ts)) Applicative[F].unit else topic.publish1(ts).void).as(ts.some, ())
      }
      .void

  // Logger of error of last resort.
  private def logError[F[_]: Logger]: PartialFunction[Throwable, F[Unit]] = {
    case e: NavigateFailure =>
      Logger[F].error(e)(s"Navigate global error handler ${NavigateFailure.explain(e)}")
    case e: Exception       => Logger[F].error(e)("Navigate global error handler")
  }

  /**
   * Start all topics and monitoring in a single call
   */
  def startAll(
    engine: NavigateEngine[F]
  )(using Temporal[F], Logger[F]): F[Fiber[F, Throwable, Unit]] =
    for {
      // Start monitoring subscribers
      _ <- navigateEvents.subscribers
             .evalMap(l => Logger[F].debug(s"Subscribers amount: $l").whenA(l > 1))
             .compile
             .drain
             .start

      // Start all polling streams
      _ <- guideStatePoll(engine, guideState).compile.drain.start
      _ <- guiderQualityPoll(engine, guidersQuality).compile.drain.start
      _ <- telescopeStatePoll(engine, telescopeState).compile.drain.start

      // Start engine event stream and return this fiber for joining
      fiber <-
        engine.eventStream.through(navigateEvents.publish).compile.drain.onError(logError).start
    } yield fiber
}

object TopicManager {

  /**
   * Buffer log messages to be able to send old messages to new clients
   */
  def bufferLogMessages[F[_]: Concurrent](
    log: Topic[F, ILoggingEvent]
  ): Resource[F, Ref[F, Seq[ILoggingEvent]]] = {
    val maxQueueSize = 30
    for {
      buffer <- Ref.empty[F, Seq[ILoggingEvent]].toResource
      _      <-
        log
          .subscribe(1024)
          .evalMap(event => buffer.update(events => events.takeRight(maxQueueSize - 1) :+ event))
          .compile
          .drain
          .background
    } yield buffer
  }

  // We need to manually update the configuration of the logging subsystem
  // to support capturing log messages and forward them to the clients
  private def logToClients[F[_]: Sync](
    out:        Topic[F, ILoggingEvent],
    dispatcher: Dispatcher[F]
  ): F[Appender[ILoggingEvent]] = Sync[F].blocking {
    import ch.qos.logback.classic.{AsyncAppender, Logger, LoggerContext}
    import org.slf4j.LoggerFactory

    val asyncAppender = new AsyncAppender
    val appender      = new SubscriptionAppender[F](out)(using dispatcher)
    Option(LoggerFactory.getILoggerFactory)
      .collect { case lc: LoggerContext =>
        lc
      }
      .foreach { ctx =>
        asyncAppender.setContext(ctx)
        appender.setContext(ctx)
        asyncAppender.addAppender(appender)
      }

    Option(LoggerFactory.getLogger("navigate"))
      .collect { case l: Logger =>
        l
      }
      .foreach { l =>
        l.addAppender(asyncAppender)
        asyncAppender.start()
        appender.start()
      }
    asyncAppender
  }

  /**
   * Create a new TopicManager with all topics initialized
   */
  def create[F[_]: Async: Logger](dispatcher: Dispatcher[F]): Resource[F, TopicManager[F]] =
    for {
      navigateEvents <- Resource.eval(Topic[F, NavigateEvent])
      loggingEvents  <- Resource.eval(Topic[F, ILoggingEvent])
      guideState     <- Resource.eval(Topic[F, GuideState])
      guidersQuality <- Resource.eval(Topic[F, GuidersQualityValues])
      telescopeState <- Resource.eval(Topic[F, TelescopeState])

      // Setup log buffer
      logBuffer <- bufferLogMessages(loggingEvents)
      // Setup logging to clients
      _         <- Resource.eval(logToClients(loggingEvents, dispatcher))
    } yield new TopicManager(
      navigateEvents,
      loggingEvents,
      guideState,
      guidersQuality,
      telescopeState,
      logBuffer
    )
}
