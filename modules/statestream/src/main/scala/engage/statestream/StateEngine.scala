// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.statestream

import cats.{ Applicative, Monad }
import cats.effect.std.Queue
import cats.data.StateT
import cats.effect.Concurrent
import cats.syntax.all._
import fs2.Stream

trait StateEngine[F[_], S, O] {
  def process(s0:     S): Stream[F, O]
  def getState(ff:    S => F[O]): F[Unit]
  def modifyState(ff: S => F[(S, O)]): F[Unit]
  def setState(ff:    F[(S, O)]): F[Unit]
}

object StateEngine {
  sealed trait Event[F[_], S, O] extends Product with Serializable

  final case class GetState[F[_], S, O](ff: S => F[O])         extends Event[F, S, O]
  final case class ModifyState[F[_], S, O](ff: S => F[(S, O)]) extends Event[F, S, O]
  final case class SetState[F[_], S, O](ff: F[(S, O)])         extends Event[F, S, O]

  class StateEngineImpl[F[_]: Monad, S, O](inputQueue: Queue[F, Event[F, S, O]])
      extends StateEngine[F, S, O] {
    override def process(s0: S): Stream[F, O] = Stream
      .fromQueueUnterminated(inputQueue)
      .evalMapAccumulate(s0)((s, i) => handleEvent(i).run(s))
      .map(_._2)

    override def getState(ff: S => F[O]): F[Unit] = inputQueue.offer(GetState(ff))

    override def modifyState(ff: S => F[(S, O)]): F[Unit] = inputQueue.offer(ModifyState(ff))

    override def setState(ff: F[(S, O)]): F[Unit] = inputQueue.offer(SetState(ff))
  }

  private def handleEvent[F[_]: Applicative, S, O](ev: Event[F, S, O]): StateT[F, S, O] = ev match {
    case GetState(ff)    => StateT((s: S) => ff(s).map((s, _)))
    case ModifyState(ff) => StateT(ff)
    case SetState(ff)    => StateT((_: S) => ff)
  }

  def build[F[_]: Concurrent, S, O]: F[StateEngine[F, S, O]] =
    Queue.unbounded[F, Event[F, S, O]].map(q => new StateEngineImpl[F, S, O](q))

}
