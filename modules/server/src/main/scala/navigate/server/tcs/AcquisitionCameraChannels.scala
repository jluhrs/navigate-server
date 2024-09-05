// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.Resource
import eu.timepit.refined.types.string.NonEmptyString
import navigate.epics.Channel
import navigate.epics.EpicsService
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.given

case class AcquisitionCameraChannels[F[_]](
  telltale: TelltaleChannel[F],
  filter:   Channel[F, String]
)

object AcquisitionCameraChannels {

  val sysName: String = "AC/HR"

  def build[F[_]](
    service: EpicsService[F],
    top:     NonEmptyString
  ): Resource[F, AcquisitionCameraChannels[F]] = for {
    tt <- service.getChannel[String](top, "health.VAL").map(TelltaleChannel(sysName, _))
    fl <- service.getChannel[String](top, "clfilterName.VAL")
  } yield AcquisitionCameraChannels(
    tt,
    fl
  )
}
