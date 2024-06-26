// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Parallel
import cats.effect.Async
import cats.effect.Ref
import cats.syntax.all.*

import scala.concurrent.duration.FiniteDuration

class TcsNorthControllerEpics[F[_]: Async: Parallel](
  tcsEpics: TcsEpicsSystem[F],
  pwfs1:    WfsEpicsSystem[F],
  pwfs2:    WfsEpicsSystem[F],
  oiwfs:    WfsEpicsSystem[F],
  timeout:  FiniteDuration,
  stateRef: Ref[F, TcsBaseControllerEpics.State]
) extends TcsBaseControllerEpics[F](tcsEpics, pwfs1, pwfs2, oiwfs, timeout, stateRef)
    with TcsNorthController[F] {}

object TcsNorthControllerEpics {

  def build[F[_]: Async: Parallel](
    tcsEpics: TcsEpicsSystem[F],
    pwfs1:    WfsEpicsSystem[F],
    pwfs2:    WfsEpicsSystem[F],
    oiwfs:    WfsEpicsSystem[F],
    timeout:  FiniteDuration
  ): F[TcsNorthControllerEpics[F]] =
    Ref
      .of[F, TcsBaseControllerEpics.State](TcsBaseControllerEpics.State.default)
      .map(
        new TcsNorthControllerEpics(tcsEpics, pwfs1, pwfs2, oiwfs, timeout, _)
      )

}
