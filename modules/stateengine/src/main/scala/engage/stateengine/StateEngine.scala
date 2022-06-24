// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.stateengine

import cats.effect.std.Queue
import cats.data.StateT
import cats.effect.Concurrent
import cats.syntax.all._
import fs2.Stream

trait StateEngine[F[_], S, O] {
  import StateEngine._

  def process(s0: S): Stream[F, O]
  def offer(ev:   Event[F, S, O]): F[Unit]

  def getState(ff: S => F[O]): Event[F, S, O]

  def modifyState(ff: S => (S, F[O])): Event[F, S, O]

  def setState(ff: (S, F[O])): Event[F, S, O]

  def lift(ff: F[O]): Event[F, S, O]

  def pure(o: O): Event[F, S, O]

}

object StateEngine {
  sealed trait Event[F[_], S, O] extends Product with Serializable

  private final case class GetState[F[_], S, O](ff: S => F[O])         extends Event[F, S, O]
  private final case class ModifyState[F[_], S, O](ff: S => (S, F[O])) extends Event[F, S, O]
  private final case class SetState[F[_], S, O](ff: (S, F[O]))         extends Event[F, S, O]
  private final case class Lift[F[_], S, O](ff: F[O])                  extends Event[F, S, O]
  private final case class AndThen[F[_], S, O](f0: Event[F, S, O], f1: Event[F, S, O])
      extends Event[F, S, O]
  private final case class AndThenMap[F[_], S, O](
    f0: Event[F, S, O],
    ff: Option[O] => Event[F, S, O]
  ) extends Event[F, S, O]
  private final case class Pure[F[_], S, O](o: O)                      extends Event[F, S, O]

  class StateEngineImpl[F[_]: Concurrent, S, O](inputQueue: Queue[F, Stream[F, Event[F, S, O]]])
      extends StateEngine[F, S, O] {
    override def process(s0: S): Stream[F, O] = Stream
      .fromQueueUnterminated(inputQueue)
      .parJoinUnbounded
      .evalMapAccumulate(s0)((s, i) => handleEvent(i).run(s))
      .evalMap {
        case (_, (o, Some(ss))) => inputQueue.offer(ss.map(pure)).as(o)
        case (_, (o, None))     => o.pure[F]
      }
      .flattenOption

    override def offer(ev: Event[F, S, O]): F[Unit] = inputQueue.offer(Stream.emit(ev))

    override def getState(ff: S => F[O]): Event[F, S, O] = GetState(ff)

    override def modifyState(ff: S => (S, F[O])): Event[F, S, O] = ModifyState(ff)

    override def setState(ff: (S, F[O])): Event[F, S, O] = SetState(ff)

    override def lift(ff: F[O]): Event[F, S, O] = Lift(ff)

    override def pure(o: O): Event[F, S, O] = Pure(o)

    private def handleEvent(ev: Event[F, S, O]): StateT[F, S, (Option[O], Option[Stream[F, O]])] =
      ev match {
        case GetState(ff)       => StateT((s: S) => (s, (none[O], Stream.eval(ff(s)).some)).pure[F])
        case ModifyState(ff)    =>
          StateT { (s: S) =>
            val (s2, fo) = ff(s)
            (s2, (none[O], Stream.eval(fo).some)).pure[F]
          }
        case SetState(ff)       =>
          StateT { (_: S) =>
            val (s2, fo) = ff
            (s2, (none[O], Stream.eval(fo).some)).pure[F]
          }
        case Lift(ff)           => StateT((s: S) => (s, (none[O], Stream.eval(ff).some)).pure[F])
        case Pure(o)            => StateT((s: S) => (s, (o.some, none[Stream[F, O]])).pure[F])
        case AndThenMap(f0, ff) =>
          for {
            p0 <- handleEvent(f0)
            p1 <- handleEvent(ff(p0._1))
          } yield (p0, p1) match {
            case ((None, None), _)                         => p1
            case (p0, (None, None))                        => p0
            case ((oo0, Some(ss0)), (None, Some(ss1)))     => (oo0, (ss0 ++ ss1).some)
            case ((oo0, Some(ss0)), (Some(o1), None))      => (oo0, (ss0 ++ Stream.emit(o1)).some)
            case ((oo0, Some(ss0)), (Some(o1), Some(ss1))) =>
              (oo0, (ss0 ++ Stream.emit(o1) ++ ss1).some)
            case ((oo0, None), (None, Some(ss1)))          => (oo0, ss1.some)
            case ((oo0, None), (Some(o1), None))           => (oo0, Stream.emit(o1).some)
            case ((oo0, None), (Some(o1), Some(ss1)))      => (oo0, (Stream.emit(o1) ++ ss1).some)
          }
        case AndThen(f0, f1)    =>
          for {
            p0 <- handleEvent(f0)
            p1 <- handleEvent(f1)
          } yield (p0, p1) match {
            case ((None, None), _)                         => p1
            case (p0, (None, None))                        => p0
            case ((oo0, Some(ss0)), (None, Some(ss1)))     => (oo0, (ss0 ++ ss1).some)
            case ((oo0, Some(ss0)), (Some(o1), None))      => (oo0, (ss0 ++ Stream.emit(o1)).some)
            case ((oo0, Some(ss0)), (Some(o1), Some(ss1))) =>
              (oo0, (ss0 ++ Stream.emit(o1) ++ ss1).some)
            case ((oo0, None), (None, Some(ss1)))          => (oo0, ss1.some)
            case ((oo0, None), (Some(o1), None))           => (oo0, Stream.emit(o1).some)
            case ((oo0, None), (Some(o1), Some(ss1)))      => (oo0, (Stream.emit(o1) ++ ss1).some)
          }
      }
  }

  implicit class eventOps[F[_], S, O](ev: Event[F, S, O]) extends AnyRef {
    def andThen(next: Event[F, S, O]): Event[F, S, O]               = AndThen(ev, next)
    def andThenMap(ff: Option[O] => Event[F, S, O]): Event[F, S, O] = AndThenMap(ev, ff)
  }

  def build[F[_]: Concurrent, S, O]: F[StateEngine[F, S, O]] =
    Queue.unbounded[F, Stream[F, Event[F, S, O]]].map(q => new StateEngineImpl[F, S, O](q))

}
