// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import lucuma.core.math.{
  Angle,
  Coordinates,
  Epoch,
  Parallax,
  ProperMotion,
  RadialVelocity,
  Wavelength
}
import lucuma.core.model.Target.Sidereal

sealed trait Target extends Product with Serializable {
  val objectName: String
  val wavelength: Wavelength
}

object Target {
  case class SiderealTarget(
    override val objectName: String,
    override val wavelength: Wavelength,
    coordinates:             Coordinates,
    epoch:                   Epoch,
    properMotion:            Option[ProperMotion],
    radialVelocity:          Option[RadialVelocity],
    parallax:                Option[Parallax]
  ) extends Target

  case class Azimuth(toAngle: Angle)

  case class Elevation(toAngle: Angle)

  case class AzElCoordinates(azimuth: Azimuth, elevation: Elevation)

  case class AzElTarget(
    override val objectName: String,
    override val wavelength: Wavelength,
    coordinates:             AzElCoordinates
  ) extends Target

  case class EphemerisTarget(
    override val objectName: String,
    override val wavelength: Wavelength,
    ephemerisFile:           String
  ) extends Target

}
