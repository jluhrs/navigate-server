// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause
package engage.statestream

import fs2.{ Pipe, Stream }
import cats.data.State

class StateStream[F[_], S, I, O](val h: I => State[S, O]) {
  def getState: StateStream[F, S, I, S]               = new StateStream[F, S, I, S]((i: I) => h(i).get)
  def setState(s: S): StateStream[F, S, I, O]         = new StateStream[F, S, I, O]((i: I) =>
    h(i).modify(_ => s)
  )
  def modifyState(f: S => S): StateStream[F, S, I, O] = new StateStream[F, S, I, O]((i: I) =>
    h(i).modify(f)
  )
  def compile(s0: S): Pipe[F, I, (S, O)]              = (is: Stream[F, I]) =>
    is.mapAccumulate(s0)((s: S, i: I) => h(i).run(s).value)
}

object StateStream {

//  def compile[F[_]: Concurrent, S, I, O](f: I => State[S, Stream[F, O]])(s0: S): Pipe[F, I, O] = (is: Stream[F, I]) =>
//    is.mapAccumulate(s0){ (s: S, i: I) => f(i).run(s).value }.map(_._2).parJoinUnbounded

}
