// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import cats.Parallel
import cats.effect.Async

import scala.concurrent.duration.FiniteDuration

class TcsNorthControllerEpics[F[_]: Async: Parallel](
  tcsEpics: TcsEpicsSystem[F],
  timeout:  FiniteDuration
) extends TcsBaseControllerEpics[F](tcsEpics, timeout)
    with TcsNorthController[F] {}
