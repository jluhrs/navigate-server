// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Applicative
import cats.effect.Resource
import cats.syntax.all.*
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.math.Wavelength
import navigate.epics.EpicsService
import navigate.epics.VerifiedEpics.VerifiedEpics
import navigate.epics.VerifiedEpics.readChannel
import navigate.model.enums.AcFilter
import navigate.server.acm.Decoder
import navigate.server.tcs.AcquisitionCameraEpicsSystem.AcquisitionCameraStatus

trait AcquisitionCameraEpicsSystem[F[_]] {
  val status: AcquisitionCameraStatus[F]
}

object AcquisitionCameraEpicsSystem {

  val acFilterDec: Decoder[String, AcFilter] = new Decoder[String, AcFilter] {
    override def decode(b: String): AcFilter = b match {
      case "neutral" => AcFilter.Neutral
      case "U-red1"  => AcFilter.U_Red1
      case "B-blue"  => AcFilter.B_Blue
      case "V-green" => AcFilter.V_Green
      case "R-red2"  => AcFilter.R_Red2
      case "I-red3"  => AcFilter.I_Red3
      case _         => AcFilter.Neutral
    }
  }

  extension (flt: AcFilter) {
    def toWavelength: Wavelength = {
      val NanoToPico: Int = 1000
      flt match
        case AcFilter.Neutral => Wavelength.unsafeFromIntPicometers(500 * NanoToPico)
        case AcFilter.U_Red1  => Wavelength.unsafeFromIntPicometers(375 * NanoToPico)
        case AcFilter.B_Blue  => Wavelength.unsafeFromIntPicometers(425 * NanoToPico)
        case AcFilter.V_Green => Wavelength.unsafeFromIntPicometers(530 * NanoToPico)
        case AcFilter.R_Red2  => Wavelength.unsafeFromIntPicometers(640 * NanoToPico)
        case AcFilter.I_Red3  => Wavelength.unsafeFromIntPicometers(760 * NanoToPico)
    }
  }

  trait AcquisitionCameraStatus[F[_]] {
    def filter: VerifiedEpics[F, F, AcFilter]
  }

  private[tcs] def buildSystem[F[_]: Applicative](
    chs: AcquisitionCameraChannels[F]
  ): AcquisitionCameraEpicsSystem[F] =
    new AcquisitionCameraEpicsSystem[F] {
      override val status: AcquisitionCameraStatus[F] = new AcquisitionCameraStatus[F] {
        override def filter: VerifiedEpics[F, F, AcFilter] =
          readChannel(chs.telltale, chs.filter).map(_.map(acFilterDec.decode))
      }
    }

  def build[F[_]: Applicative](
    service: EpicsService[F],
    top:     NonEmptyString
  ): Resource[F, AcquisitionCameraEpicsSystem[F]] =
    AcquisitionCameraChannels.build(service, top).map(buildSystem)

}
