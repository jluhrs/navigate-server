// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import navigate.model.enums.{DomeMode, ShutterMode}
import navigate.server.ApplyCommandResult
import lucuma.core.math.{Angle, Coordinates, Epoch, Parallax, ProperMotion, RadialVelocity}

trait TcsBaseController[F[_]] {
  import TcsBaseController._
  def mcsPark: F[ApplyCommandResult]
  def mcsFollow(enable:           Boolean): F[ApplyCommandResult]
  def rotStop(useBrakes:          Boolean): F[ApplyCommandResult]
  def rotPark: F[ApplyCommandResult]
  def rotFollow(enable:           Boolean): F[ApplyCommandResult]
  def rotMove(angle:              Angle): F[ApplyCommandResult]
  def ecsCarouselMode(
    domeMode:      DomeMode,
    shutterMode:   ShutterMode,
    slitHeight:    Double,
    domeEnable:    Boolean,
    shutterEnable: Boolean
  ): F[ApplyCommandResult]
  def ecsVentGatesMove(gateEast:  Double, westGate: Double): F[ApplyCommandResult]
  def applyTcsConfig(config:      TcsConfig): F[ApplyCommandResult]
  def slew(config:                SlewConfig): F[ApplyCommandResult]
  def instrumentSpecifics(config: InstrumentSpecifics): F[ApplyCommandResult]
  def oiwfsTarget(target:         Target): F[ApplyCommandResult]
  def rotIaa(angle:               Angle): F[ApplyCommandResult]
}

object TcsBaseController {

  final case class TcsConfig(
    sourceATarget: Target
  )

}
