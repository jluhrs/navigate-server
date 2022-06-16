// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.statestream

import cats.data.State
import cats.{ Applicative, Functor }

object StateStreamOps {

  implicit def stateStreamFunctor[F[_], S, I, O]: Functor[StateStream[F, S, I, *]] =
    new Functor[StateStream[F, S, I, *]] {
      override def map[A, B](fa: StateStream[F, S, I, A])(f: A => B): StateStream[F, S, I, B] =
        new StateStream[F, S, I, B](i => fa.h(i).map(f))
    }

  implicit def stateStreamApplicative[F[_], S, I, O]: Applicative[StateStream[F, S, I, *]] =
    new Applicative[StateStream[F, S, I, *]] {
      override def pure[A](x: A): StateStream[F, S, I, A] = new StateStream[F, S, I, A]((_: I) =>
        State.pure[S, A](x)
      )

      override def ap[A, B](ff: StateStream[F, S, I, A => B])(
        fa:                     StateStream[F, S, I, A]
      ): StateStream[F, S, I, B] = ???
    }

}
