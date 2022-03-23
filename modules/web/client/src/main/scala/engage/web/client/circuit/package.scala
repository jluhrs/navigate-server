// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client

import cats.Eq
import cats.data.NonEmptyList
import cats.syntax.all._
import diode._
import monocle.Getter
import monocle.Lens

package object circuit {
  implicit def CircuitToOps[T <: AnyRef](c: Circuit[T]): CircuitOps[T] =
    new CircuitOps(c)

  implicit def fastEq[A: Eq]: FastEq[A] = new FastEq[A] {
    override def eqv(a: A, b: A): Boolean = a === b
  }

  implicit def fastNelEq[A: Eq]: FastEq[NonEmptyList[A]] =
    new FastEq[NonEmptyList[A]] {
      override def eqv(a: NonEmptyList[A], b: NonEmptyList[A]): Boolean =
        a === b
    }
}

package circuit {

  import monocle.Optional

  /**
   * This lets us use monocle lenses to create diode ModelRW instances
   */
  class CircuitOps[M <: AnyRef](circuit: Circuit[M]) {
    def zoomRWL[A: Eq](lens: Lens[M, A]): ModelRW[M, A] =
      circuit.zoomRW(lens.get)((m, a) => lens.replace(a)(m))(fastEq[A])

    def zoomL[A: Eq](lens: Lens[M, A]): ModelR[M, A] =
      circuit.zoom[A](lens.get)(fastEq[A])

    def zoomO[A: Eq](lens: Optional[M, A]): ModelR[M, Option[A]] =
      circuit.zoom[Option[A]](lens.getOption)(fastEq[Option[A]])

    def zoomG[A: Eq](getter: Getter[M, A]): ModelR[M, A] =
      circuit.zoom[A](getter.get)(fastEq[A])
  }

}
