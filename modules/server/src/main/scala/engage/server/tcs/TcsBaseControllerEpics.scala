// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs
import cats.Parallel
import cats.effect.Async
import engage.server.{ ApplyCommandResult, ConnectionTimeout }

import scala.concurrent.duration.FiniteDuration
import engage.epics.VerifiedEpics._

/* This class implements the common TCS commands */
class TcsBaseControllerEpics[F[_]: Async: Parallel](
  tcsEpics: TcsEpicsSystem[F],
  timeout:  FiniteDuration
) extends TcsBaseController[F] {
  override def mcsPark: F[ApplyCommandResult] =
    tcsEpics
      .startCommand(timeout)
      .mcsParkCmd
      .mark
      .post
      .verifiedRun(ConnectionTimeout)

  override def mcsFollow(enable: Boolean): F[ApplyCommandResult] =
    tcsEpics
      .startCommand(timeout)
      .mcsFollowCommand
      .setFollow(enable)
      .post
      .verifiedRun(ConnectionTimeout)
}
