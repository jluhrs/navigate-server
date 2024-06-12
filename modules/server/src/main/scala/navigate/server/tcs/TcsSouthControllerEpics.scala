// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Parallel
import cats.effect.Async

import scala.concurrent.duration.FiniteDuration

class TcsSouthControllerEpics[F[_]: Async: Parallel](
  tcsEpics: TcsEpicsSystem[F],
  pwfs1:    WfsEpicsSystem[F],
  pwfs2:    WfsEpicsSystem[F],
  oiwfs:    WfsEpicsSystem[F],
  timeout:  FiniteDuration
) extends TcsBaseControllerEpics[F](tcsEpics, pwfs1, pwfs2, oiwfs, timeout)
    with TcsSouthController[F] {}
