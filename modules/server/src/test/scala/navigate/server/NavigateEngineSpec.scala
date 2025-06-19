// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

import cats.Applicative
import cats.effect.Async
import cats.effect.IO
import cats.effect.MonadCancelThrow
import cats.effect.Resource
import cats.syntax.all.*
import fs2.Stream
import lucuma.core.enums.ComaOption
import lucuma.core.enums.M1Source
import lucuma.core.enums.MountGuideOption
import lucuma.core.enums.Site
import lucuma.core.enums.TipTiltSource
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig.M2GuideOn
import lucuma.core.model.TelescopeGuideConfig
import munit.CatsEffectSuite
import navigate.model.config.NavigateEngineConfiguration
import navigate.server.tcs.TcsNorthControllerSim
import navigate.server.tcs.TcsSouthControllerSim
import org.http4s.Response
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class NavigateEngineSpec extends CatsEffectSuite {

  private given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("navigate-engine")

  test("NavigateEngine must memorize requested guide configuration.") {

    val guideCfg = TelescopeGuideConfig(
      mountGuide = MountGuideOption.MountGuideOn,
      m1Guide = M1GuideConfig.M1GuideOn(M1Source.OIWFS),
      m2Guide = M2GuideOn(ComaOption.ComaOn, Set(TipTiltSource.OIWFS)),
      dayTimeMode = Some(false),
      probeGuide = none
    )

    for {
      eng <- NavigateEngineSpec.buildEngine[IO]
      _   <- eng.enableGuide(guideCfg)
      _   <- eng.eventStream.take(2).compile.drain
      r   <- eng.getGuideDemand
    } yield assertEquals(r.tcsGuide, guideCfg)
  }
}

object NavigateEngineSpec {

  private def buildClient[F[_]: MonadCancelThrow](body: String): Client[F] = Client.apply[F] { _ =>
    Resource.liftK(Applicative[F].pure(Response[F](body = Stream.emits(body.getBytes("UTF-8")))))
  }

  def buildEngine[F[_]: {Async, Logger}]: F[NavigateEngine[F]] = for {
    tcsNorth <- TcsNorthControllerSim.build[F]
    tcsSouth <- TcsSouthControllerSim.build[F]
    ret      <- NavigateEngine.build[F](
                  Site.GS,
                  Systems(
                    OdbProxy.dummy,
                    buildClient("dummy"),
                    tcsSouth,
                    tcsSouth,
                    tcsNorth
                  ),
                  NavigateEngineConfiguration.default
                )
  } yield ret

}
