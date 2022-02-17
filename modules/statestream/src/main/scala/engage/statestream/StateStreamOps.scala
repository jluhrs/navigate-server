package engage.statestream

import cats.Functor
import fs2.{Pipe, Stream}

object StateStreamOps {
  implicit def stateStreamFunctor[F[_]: Functor, S, I, O]: Functor[StateStream[F, S, I, *]] = new Functor[StateStream[F, S, I, *]] {
    override def map[A, B](fa: StateStream[F, S, I, A])(f: A => B): StateStream[F, S, I, B] = new StateStream[F, S, I, B] {
      override def getState: StateStream[F, S, I, S] = fa.getState

      override def setState(s: S): StateStream[F, S, I, Unit] = fa.setState(s)

      override def modifyState(f: S => S): StateStream[F, S, I, Unit] = fa.modifyState(f)

      override def compile(s0: S): Pipe[F, I, B] = { a: Stream[F, I] =>
        fa.compile(s0)(a).map(f)
      }
    }
  }

}
