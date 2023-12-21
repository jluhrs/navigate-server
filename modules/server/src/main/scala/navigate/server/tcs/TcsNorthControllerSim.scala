// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Applicative
import cats.effect.Ref
import cats.effect.Sync
import cats.syntax.all.*

class TcsNorthControllerSim[F[_]: Applicative](guideRef: Ref[F, GuideState])
    extends TcsBaseControllerSim[F](guideRef)
    with TcsNorthController[F] {}

object TcsNorthControllerSim {
  def build[F[_]: Sync]: F[TcsNorthControllerSim[F]] = Ref
    .of(GuideState(false, M1GuideConfig.M1GuideOff, M2GuideConfig.M2GuideOff))
    .map(new TcsNorthControllerSim(_))
}
