// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

import cats.Parallel
import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import lucuma.core.enums.Site
import navigate.epics.EpicsService
import navigate.model.config.ControlStrategy
import navigate.model.config.NavigateEngineConfiguration
import navigate.server.tcs.*
import org.http4s.client.Client

import scala.annotation.nowarn

case class Systems[F[_]](
  odb:      OdbProxy[F],
  tcsSouth: TcsSouthController[F],
  tcsNorth: TcsNorthController[F]
)

object Systems {
  def build[F[_]: Async: Dispatcher: Parallel](
    @nowarn site:   Site,
    @nowarn client: Client[F],
    conf:           NavigateEngineConfiguration,
    epicsSrv:       EpicsService[F]
  ): Resource[F, Systems[F]] = {
    val tops = decodeTops(conf.tops)

    // These are placeholders.
    def buildOdbProxy: Resource[F, OdbProxy[F]] = Resource.eval(OdbProxy.build)

    def buildTcsSouthController: Resource[F, TcsSouthController[F]] =
      if (conf.systemControl.tcs === ControlStrategy.FullControl)
        TcsEpicsSystem.build(epicsSrv, tops).map(new TcsSouthControllerEpics(_, conf.ioTimeout))
      else
        Resource.eval(TcsSouthControllerSim.build)

    def buildTcsNorthController: Resource[F, TcsNorthController[F]] =
      if (conf.systemControl.tcs === ControlStrategy.FullControl)
        TcsEpicsSystem.build(epicsSrv, tops).map(new TcsNorthControllerEpics(_, conf.ioTimeout))
      else
        Resource.eval(TcsNorthControllerSim.build)

    for {
      odb  <- buildOdbProxy
      tcsS <- buildTcsSouthController
      tcsN <- buildTcsNorthController
    } yield Systems[F](odb, tcsS, tcsN)
  }

  private def decodeTops(s: String): Map[String, String] =
    s.split("=|,")
      .grouped(2)
      .collect { case Array(k, v) =>
        k.trim -> v.trim
      }
      .toMap

}
