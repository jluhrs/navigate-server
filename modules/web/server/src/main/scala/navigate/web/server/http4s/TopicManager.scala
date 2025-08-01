// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.Eq
import cats.effect.*
import cats.effect.std.Dispatcher
import cats.effect.syntax.all.*
import cats.syntax.all.*
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.Topic
import navigate.model.AcquisitionAdjustment
import navigate.model.FocalPlaneOffset
import navigate.model.NavigateEvent
import navigate.model.PointingCorrections
import navigate.server.NavigateEngine
import navigate.server.NavigateFailure
import navigate.server.tcs.GuideState
import navigate.server.tcs.GuidersQualityValues
import navigate.server.tcs.TargetOffsets
import navigate.server.tcs.TelescopeState
import navigate.web.server.http4s.TopicManager.PollState
import navigate.web.server.logging.SubscriptionAppender
import org.typelevel.log4cats.Logger

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

class TopicManager[F[_]] private (
  val navigateEvents:        Topic[F, NavigateEvent],
  val loggingEvents:         Topic[F, ILoggingEvent],
  val guideState:            Topic[F, GuideState],
  val guidersQuality:        Topic[F, GuidersQualityValues],
  val telescopeState:        Topic[F, TelescopeState],
  val acquisitionAdjustment: Topic[F, AcquisitionAdjustment],
  val targetAdjustment:      Topic[F, TargetOffsets],
  val originAdjustment:      Topic[F, FocalPlaneOffset],
  val pointingAdjustment:    Topic[F, PointingCorrections],
  val logBuffer:             Ref[F, Seq[ILoggingEvent]]
) {

  val ReconnectCycles = 30
  val RepeatCycles    = 10

  private def calcReconnect(v: Int) = Math.max(0, (v * 3 / 2 + (Math.random * v).toInt) / 2)

  private def genericPoll[A](
    fetchData: => F[A],
    topic:     Topic[F, A],
    start:     Int,
    reconnect: Int = ReconnectCycles,
    force:     Int = RepeatCycles
  )(using Temporal[F], Logger[F], Eq[A]): Pipe[F, Unit, Unit] =
    _.evalMapAccumulate[F, PollState[A], Unit](PollState.Retry(Math.max(0, start))) {
      case (acc, _) =>
        (acc match {
          case PollState.Started(last, n) =>
            fetchData.attempt.flatMap(
              _.fold[F[PollState[A]]](
                e =>
                  Logger[F]
                    .warn(s"Error on state poll: ${e.getMessage}")
                    .as(PollState.Retry(calcReconnect(reconnect))),
                a =>
                  if (n === 0 || (last: A) =!= a) topic.publish1(a).as(PollState.Started(a, force))
                  else PollState.Started(last, n - 1).pure[F]
              )
            )
          case PollState.Retry(0)         =>
            fetchData.attempt.flatMap(
              _.fold[F[PollState[A]]](
                e =>
                  Logger[F]
                    .warn(s"Error on state poll: ${e.getMessage}")
                    .as(PollState.Retry(calcReconnect(reconnect))),
                a => topic.publish1(a).as(PollState.Started(a, force))
              )
            )
          case PollState.Retry(countdown) => PollState.Retry(countdown - 1).pure[F]
        }).map((_, ()))
    }.void

  private def guideStatePoll(
    eng:   NavigateEngine[F],
    topic: Topic[F, GuideState],
    start: Int
  )(using Temporal[F], Logger[F]): Pipe[F, Unit, Unit] =
    genericPoll(eng.getGuideState, topic, start)

  private def guiderQualityPoll(
    eng:   NavigateEngine[F],
    topic: Topic[F, GuidersQualityValues],
    start: Int
  )(using Temporal[F], Logger[F]): Pipe[F, Unit, Unit] =
    genericPoll(eng.getGuidersQuality, topic, start)

  private def telescopeStatePoll(
    eng:   NavigateEngine[F],
    topic: Topic[F, TelescopeState],
    start: Int
  )(using Temporal[F], Logger[F]): Pipe[F, Unit, Unit] =
    genericPoll(eng.getTelescopeState, topic, start)

  private def targetAdjStatePoll(
    eng:   NavigateEngine[F],
    topic: Topic[F, TargetOffsets],
    start: Int
  )(using Temporal[F], Logger[F]): Pipe[F, Unit, Unit] =
    genericPoll(eng.getTargetAdjustments, topic, start)

  private def originAdjStatePoll(
    eng:   NavigateEngine[F],
    topic: Topic[F, FocalPlaneOffset],
    start: Int
  )(using Temporal[F], Logger[F]): Pipe[F, Unit, Unit] =
    genericPoll(eng.getOriginOffset, topic, start)

  private def pointingAdjStatePoll(
    eng:   NavigateEngine[F],
    topic: Topic[F, PointingCorrections],
    start: Int
  )(using Temporal[F], Logger[F]): Pipe[F, Unit, Unit] =
    genericPoll(eng.getPointingOffset, topic, start)

  // Logger of error of last resort.
  private def logError(using Logger[F]): PartialFunction[Throwable, F[Unit]] = {
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
    Stream
      .emits(
        List(
          navigateEvents.subscribers
            .evalMap(l => Logger[F].debug(s"Subscribers amount: $l").whenA(l > 1)),
          Stream
            .fixedDelay[F](FiniteDuration(1, TimeUnit.SECONDS))
            .broadcastThrough(
              guideStatePoll(engine, guideState, 1),
              guiderQualityPoll(engine, guidersQuality, 2),
              telescopeStatePoll(engine, telescopeState, 3),
              targetAdjStatePoll(engine, targetAdjustment, 4),
              originAdjStatePoll(engine, originAdjustment, 5),
              pointingAdjStatePoll(engine, pointingAdjustment, 6)
            ),
          engine.eventStream.through(navigateEvents.publish)
        )
      )
      .parJoinUnbounded
      .compile
      .drain
      .onError(logError)
      .start

}

object TopicManager {

  /**
   * Buffer log messages to be able to send old messages to new clients
   */
  private def bufferLogMessages[F[_]: Concurrent](
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
  def create[F[_]: Async](dispatcher: Dispatcher[F]): Resource[F, TopicManager[F]] =
    for {
      navigateEvents        <- Resource.eval(Topic[F, NavigateEvent])
      loggingEvents         <- Resource.eval(Topic[F, ILoggingEvent])
      guideState            <- Resource.eval(Topic[F, GuideState])
      guidersQuality        <- Resource.eval(Topic[F, GuidersQualityValues])
      telescopeState        <- Resource.eval(Topic[F, TelescopeState])
      acquisitionAdjustment <- Resource.eval(Topic[F, AcquisitionAdjustment])
      targetAdjustment      <- Resource.eval(Topic[F, TargetOffsets])
      originAdjustment      <- Resource.eval(Topic[F, FocalPlaneOffset])
      pointingAdjustment    <- Resource.eval(Topic[F, PointingCorrections])

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
      acquisitionAdjustment,
      targetAdjustment,
      originAdjustment,
      pointingAdjustment,
      logBuffer
    )

  sealed trait PollState[+T]

  object PollState {
    case class Started[T](last: T, countdown: Int) extends PollState[T]
    case class Retry(countdown: Int)               extends PollState[Nothing]
  }

}
