// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import engage.model.enums.{ DomeMode, ShutterMode }
import engage.server.ApplyCommandResult
import lucuma.core.math.{ Angle, Coordinates, Epoch, Parallax, ProperMotion, RadialVelocity }
import squants.{ Angle => SAngle }

trait TcsBaseController[F[_]] {
  import TcsBaseController._
  def mcsPark: F[ApplyCommandResult]
  def mcsFollow(enable:          Boolean): F[ApplyCommandResult]
  def rotStop(useBrakes:         Boolean): F[ApplyCommandResult]
  def rotPark: F[ApplyCommandResult]
  def rotFollow(enable:          Boolean): F[ApplyCommandResult]
  def rotMove(angle:             SAngle): F[ApplyCommandResult]
  def ecsCarouselMode(
    domeMode:                    DomeMode,
    shutterMode:                 ShutterMode,
    slitHeight:                  Double,
    domeEnable:                  Boolean,
    shutterEnable:               Boolean
  ): F[ApplyCommandResult]
  def ecsVentGatesMove(gateEast: Double, westGate: Double): F[ApplyCommandResult]
  def applyTcsConfig(config:     TcsConfig): F[ApplyCommandResult]
}

object TcsBaseController {
  sealed trait Target {
    val objectName: String
    val brightness: Double
  }

  final case class SiderealTarget(
    override val objectName: String,
    override val brightness: Double,
    coordinates:             Coordinates,
    epoch:                   Epoch,
    equinox:                 String,
    properMotion:            Option[ProperMotion],
    radialVelocity:          Option[RadialVelocity],
    parallax:                Option[Parallax]
  ) extends Target

  final case class Azimuth(toAngle: Angle)

  final case class Elevation(toAngle: Angle)

  final case class AzElCoordinates(azimuth: Azimuth, elevation: Elevation)

  final case class AzElTarget(
    override val objectName: String,
    override val brightness: Double,
    coordinates:             AzElCoordinates
  ) extends Target

  final case class EphemerisTarget(
    override val objectName: String,
    override val brightness: Double,
    ephemerisFile:           String
  ) extends Target

  final case class TcsConfig(
    sourceATarget: Target
  )

}
