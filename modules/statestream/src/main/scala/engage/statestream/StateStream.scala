// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause
package engage.statestream

import cats.syntax.applicative._
import fs2.{Pipe, Stream}
import cats.data.State
import cats.effect.Concurrent

class StateStream[F[_]: Concurrent, S, I, O](f: I => State[S, Stream[F, O]]) {
  def getState: StateStream[F, S, I, S] = new StateStream[F, S, I, S]( (i: I) => f(i).get.map(x => Stream.eval(x.pure[F])) )
  def setState(s: S): StateStream[F, S, I, Unit]
  def modifyState(f: S => S): StateStream[F, S, I, Unit]
  def compile(s0: S): Pipe[F, I, O]
}

object StateStream {

  def compile[F[_]: Concurrent, S, I, O](f: I => State[S, Stream[F, O]])(s0: S): Pipe[F, I, O] = (is: Stream[F, I]) =>
    is.mapAccumulate(s0){ (s: S, i: I) => f(i).run(s).value }.map(_._2).parJoinUnbounded


}