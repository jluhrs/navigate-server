// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.Ref
import cats.effect.Sync
import cats.syntax.all.*
import lucuma.core.enums.MountGuideOption
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import navigate.model.AcMechsState
import navigate.model.AcWindow
import navigate.model.enums.AcFilter
import navigate.model.enums.AcLens
import navigate.model.enums.AcNdFilter
import navigate.server.ApplyCommandResult
import navigate.server.tcs.TcsBaseController.AcCommands

class TcsSouthControllerSim[F[_]: Sync](
  guideRef:    Ref[F, GuideState],
  telStateRef: Ref[F, TelescopeState]
) extends TcsBaseControllerSim[F](guideRef, telStateRef)
    with TcsSouthController[F] {
  override def getInstrumentPorts: F[InstrumentPorts] =
    InstrumentPorts(
      flamingos2Port = 1,
      ghostPort = 0,
      gmosPort = 3,
      gnirsPort = 0,
      gpiPort = 0,
      gsaoiPort = 5,
      igrins2Port = 0,
      nifsPort = 0,
      niriPort = 0
    ).pure[F]

  override val acCommands: AcCommands[F] = new AcCommands[F] {
    override def lens(l: AcLens): F[ApplyCommandResult] = ApplyCommandResult.Completed.pure[F]

    override def ndFilter(ndFilter: AcNdFilter): F[ApplyCommandResult] =
      ApplyCommandResult.Completed.pure[F]

    override def filter(filter: AcFilter): F[ApplyCommandResult] =
      ApplyCommandResult.Completed.pure[F]

    override def windowSize(size: AcWindow): F[ApplyCommandResult] =
      ApplyCommandResult.Completed.pure[F]

    override def getState: F[AcMechsState] =
      AcMechsState(AcLens.Ac.some, AcNdFilter.Nd1.some, AcFilter.Neutral.some).pure[F]
  }

}

object TcsSouthControllerSim {
  def build[F[_]: Sync]: F[TcsSouthControllerSim[F]] = for {
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
  } yield new TcsSouthControllerSim(x, y)
}
