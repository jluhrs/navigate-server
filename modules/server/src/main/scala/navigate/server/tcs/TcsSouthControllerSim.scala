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

class TcsSouthControllerSim[F[_]: Applicative](guideRef: Ref[F, GuideState])
    extends TcsBaseControllerSim[F](guideRef)
    with TcsSouthController[F] {}

object TcsSouthControllerSim {
  def build[F[_]: Sync]: F[TcsSouthControllerSim[F]] = Ref
    .of(
      GuideState(MountGuideOption.MountGuideOff,
                 M1GuideConfig.M1GuideOff,
                 M2GuideConfig.M2GuideOff,
                 false,
                 false,
                 false
      )
    )
    .map(new TcsSouthControllerSim(_))
}
