// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server

import cats.effect.{ Async, Concurrent, Ref, Temporal }
import cats.effect.kernel.Sync
import cats.syntax.all._
import engage.model.EngageEvent
import engage.model.EngageEvent.{ McsParkEnd, McsParkStart }
import engage.model.config.EngageEngineConfiguration
import engage.stateengine.StateEngine
import engage.stateengine.StateEngine._
import fs2.{ Pipe, Stream }
import lucuma.core.enum.Site

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

trait EngageEngine[F[_]] {
  val systems: Systems[F]

  def eventStream: Stream[F, EngageEvent]

  def mcsPark: F[Unit]
}

object EngageEngine {

  def failIfNoEmitsWithin[F[_]: Async, A](
    timeout: FiniteDuration,
    msg:     String
  ): Pipe[F, A, A] = in => {
    import scala.concurrent.TimeoutException
    val now = Temporal[F].realTime

    Stream.eval(now.flatMap(Ref[F].of)).flatMap { lastActivityAt =>
      in.evalTap(_ => now.flatMap(lastActivityAt.set))
        .concurrently {
          Stream.repeatEval {
            (now, lastActivityAt.get)
              .mapN(_ - _)
              .flatMap { elapsed =>
                val t = timeout - elapsed

                Sync[F]
                  .raiseError[Unit](new TimeoutException(msg))
                  .whenA(t <= 0.nanos) >> Temporal[F].sleep(t)
              }
          }
        }
    }
  }

  private case class EngageEngineImpl[F[_]: Concurrent](
    site:    Site,
    systems: Systems[F],
    conf:    EngageEngineConfiguration,
    engine:  StateEngine[F, State, Stream[F, EngageEvent]]
  ) extends EngageEngine[F] {
    override def eventStream: Stream[F, EngageEvent] =
      engine.process(startState).parJoinUnbounded

    def mcsPark: F[Unit] = engine.offer(
      engine
        .modifyState { st =>
          (
            st.copy(tcsActionInProgress = true, mcsParkInProgress = true),
            Stream.emit[F, EngageEvent](McsParkStart).pure[F]
          )
        }
        .andThen(
          engine.modifyState { st =>
            (
              st.copy(tcsActionInProgress = false, mcsParkInProgress = false),
              Stream.emit[F, EngageEvent](McsParkEnd).pure[F]
            )
          }
        )
    )

  }

  def build[F[_]: Concurrent](
    site:    Site,
    systems: Systems[F],
    conf:    EngageEngineConfiguration
  ): F[EngageEngine[F]] = StateEngine
    .build[F, State, Stream[F, EngageEvent]]
    .map(EngageEngineImpl[F](site, systems, conf, _))

  case class State(
    tcsActionInProgress: Boolean,
    mcsParkInProgress:   Boolean
  )

  val startState: State = State(false, false)
}
