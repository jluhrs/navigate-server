// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server

import cats.{ Applicative, Functor }
import cats.effect.{ Async, Ref, Temporal }
import cats.effect.kernel.Sync
import cats.effect.std.Queue
import cats.syntax.all._
import engage.model.EngageEvent
import engage.model.config.EngageEngineConfiguration
import fs2.{ Pipe, Stream }
import lucuma.core.enum.Site

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

trait EngageEngine[F[_], T] {
  val systems: Systems[F]

  def eventStream(q: Queue[F, T]): Stream[F, EngageEvent]
}

object EngageEngine {

  def failIfNoEmitsWithin[F[_]: Async, A](
    timeout: FiniteDuration,
    msg:     String
  ): Pipe[F, A, A] = in => {
    import scala.concurrent.TimeoutException
    def now = Temporal[F].realTime

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

  private case class EngageEngineImpl[F[_]: Functor, T](
    site:    Site,
    systems: Systems[F],
    conf:    EngageEngineConfiguration
  ) extends EngageEngine[F, T] {
    override def eventStream(q: Queue[F, T]): Stream[F, EngageEvent] =
      Stream.fromQueueUnterminated(q).as(EngageEvent.NullEvent)
  }

  def build[F[_]: Applicative, T](
    site:    Site,
    systems: Systems[F],
    conf:    EngageEngineConfiguration
  ): F[EngageEngine[F, T]] = EngageEngineImpl[F, T](site, systems, conf).pure[F].widen
}
