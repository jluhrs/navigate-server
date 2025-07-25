// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.Ref
import cats.effect.Sync
import cats.syntax.all.*
import lucuma.core.enums.MountGuideOption
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig

class TcsNorthControllerSim[F[_]: Sync](
  guideRef:    Ref[F, GuideState],
  telStateRef: Ref[F, TelescopeState]
) extends TcsBaseControllerSim[F](guideRef, telStateRef)
    with TcsNorthController[F] {

  override def getInstrumentPorts: F[InstrumentPorts] =
    InstrumentPorts(
      flamingos2Port = 0,
      ghostPort = 0,
      gmosPort = 5,
      gnirsPort = 3,
      gpiPort = 0,
      gsaoiPort = 0,
      igrins2Port = 0,
      nifsPort = 1,
      niriPort = 0
    ).pure[F]

}

object TcsNorthControllerSim {
  def build[F[_]: Sync]: F[TcsNorthControllerSim[F]] = for {
    x <- Ref.of(
           GuideState(
             MountGuideOption.MountGuideOff,
             M1GuideConfig.M1GuideOff,
             M2GuideConfig.M2GuideOff,
             false,
             false,
             false,
             false
           )
         )
    y <- Ref.of(TelescopeState.default)
  } yield new TcsNorthControllerSim(x, y)
}
