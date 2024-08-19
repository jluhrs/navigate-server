// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Parallel
import cats.effect.Async
import cats.effect.Ref
import cats.syntax.all.*

import scala.concurrent.duration.FiniteDuration

class TcsSouthControllerEpics[F[_]: Async: Parallel](
  sys:      EpicsSystems[F],
  timeout:  FiniteDuration,
  stateRef: Ref[F, TcsBaseControllerEpics.State]
) extends TcsBaseControllerEpics[F](
      sys,
      timeout,
      stateRef
    )
    with TcsSouthController[F] {}

object TcsSouthControllerEpics {

  def build[F[_]: Async: Parallel](
    sys:     EpicsSystems[F],
    timeout: FiniteDuration
  ): F[TcsSouthControllerEpics[F]] =
    Ref
      .of[F, TcsBaseControllerEpics.State](TcsBaseControllerEpics.State.default)
      .map(
        new TcsSouthControllerEpics(sys, timeout, _)
      )

}
