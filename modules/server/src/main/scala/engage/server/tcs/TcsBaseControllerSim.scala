// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import cats.Applicative
import engage.model.enums.{ DomeMode, ShutterMode }
import engage.server.ApplyCommandResult
import squants.Angle

class TcsBaseControllerSim[F[_]: Applicative] extends TcsBaseController[F] {
  override def mcsPark: F[ApplyCommandResult] = Applicative[F].pure(ApplyCommandResult.Completed)

  override def mcsFollow(enable: Boolean): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def rotStop(useBrakes: Boolean): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def rotPark: F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def rotFollow(enable: Boolean): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def rotMove(angle: Angle): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def ecsCarouselMode(
    domeMode:      DomeMode,
    shutterMode:   ShutterMode,
    slitHeight:    Double,
    domeEnable:    Boolean,
    shutterEnable: Boolean
  ): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def ecsVentGatesMove(gateEast: Double, westGate: Double): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def applyTcsConfig(config: TcsBaseController.TcsConfig): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)
}
