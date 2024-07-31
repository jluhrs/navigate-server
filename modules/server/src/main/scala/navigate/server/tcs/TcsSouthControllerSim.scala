// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Applicative
import cats.effect.Ref
import cats.effect.Sync
import cats.syntax.all.*
import lucuma.core.enums.MountGuideOption
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig

class TcsSouthControllerSim[F[_]: Applicative](
  guideRef:          Ref[F, GuideState],
  guidersQualityRef: Ref[F, GuidersQualityValues],
  telStateRef:       Ref[F, TelescopeState]
) extends TcsBaseControllerSim[F](guideRef, guidersQualityRef, telStateRef)
    with TcsSouthController[F] {}

object TcsSouthControllerSim {
  def build[F[_]: Sync]: F[TcsSouthControllerSim[F]] = for {
    x <- Ref.of(
           GuideState(
             MountGuideOption.MountGuideOff,
             M1GuideConfig.M1GuideOff,
             M2GuideConfig.M2GuideOff,
             false,
             false,
             false
           )
         )
    y <- Ref.of(
           GuidersQualityValues(
             GuidersQualityValues.GuiderQuality(0, false),
             GuidersQualityValues.GuiderQuality(0, false),
             GuidersQualityValues.GuiderQuality(0, false)
           )
         )
    z <- Ref.of(TelescopeState.default)
  } yield new TcsSouthControllerSim(x, y, z)
}
