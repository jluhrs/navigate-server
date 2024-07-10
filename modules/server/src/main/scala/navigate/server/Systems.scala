// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

import cats.Parallel
import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import lucuma.core.enums.Site
import lucuma.refined.*
import mouse.boolean.*
import navigate.epics.EpicsService
import navigate.model.config.ControlStrategy
import navigate.model.config.NavigateEngineConfiguration
import navigate.server.tcs.*
import org.http4s.client.Client

case class Systems[F[_]](
  odb:       OdbProxy[F],
  client:    Client[F],
  tcsCommon: TcsBaseController[F],
  tcsSouth:  TcsSouthController[F],
  tcsNorth:  TcsNorthController[F]
)

object Systems {
  def build[F[_]: Async: Dispatcher: Parallel](
    site:     Site,
    client:   Client[F],
    conf:     NavigateEngineConfiguration,
    epicsSrv: EpicsService[F]
  ): Resource[F, Systems[F]] = {
    val tops = decodeTops(conf.tops)

    // These are placeholders.
    def buildOdbProxy: Resource[F, OdbProxy[F]] = Resource.eval(OdbProxy.build)

    def buildTcsSouthController: Resource[F, TcsSouthController[F]] =
      if (conf.systemControl.tcs === ControlStrategy.FullControl)
        for {
          tcs <- TcsEpicsSystem.build(epicsSrv, tops)
          p1  <- WfsEpicsSystem.build(
                   epicsSrv,
                   "PWFS1",
                   readTop(tops, "pwfs1".refined),
                   "dc:fgDiag6P1.VALH".refined,
                   "dc:fgDiag1P1.VALB".refined
                 )
          p2  <- WfsEpicsSystem.build(
                   epicsSrv,
                   "PWFS2",
                   readTop(tops, "pwfs2".refined),
                   "dc:fgDiag1P2.VALQ".refined,
                   "dc:fgDiag1P2.VALB".refined
                 )
          oi  <- WfsEpicsSystem.build(
                   epicsSrv,
                   "OIWFS",
                   readTop(tops, "oiwfs".refined),
                   "dc:fgDiag1P2.VALQ".refined,
                   "dc:fgDiag1P2.VALB".refined,
                   "dc:initSigInit.MARK".refined
                 )
          r   <- Resource.eval(TcsSouthControllerEpics.build(tcs, p1, p2, oi, conf.ioTimeout))
        } yield r
      else
        Resource.eval(TcsSouthControllerSim.build)

    def buildTcsNorthController: Resource[F, TcsNorthController[F]] =
      if (conf.systemControl.tcs === ControlStrategy.FullControl)
        for {
          tcs <- TcsEpicsSystem.build(epicsSrv, tops)
          p1  <- WfsEpicsSystem.build(epicsSrv, "PWFS1", readTop(tops, "pwfs1".refined))
          p2  <- WfsEpicsSystem.build(epicsSrv, "PWFS2", readTop(tops, "pwfs2".refined))
          oi  <- WfsEpicsSystem.build(epicsSrv,
                                      "OIWFS",
                                      "dc:fgDiag1Oi.VALQ".refined,
                                      "dc:fgDiag1Oi.VALB".refined,
                                      readTop(tops, "oiwfs".refined),
                                      "dc:initSigInit.MARK".refined
                 )
          r   <- Resource.eval(TcsNorthControllerEpics.build(tcs, p1, p2, oi, conf.ioTimeout))
        } yield r
      else
        Resource.eval(TcsNorthControllerSim.build)

    for {
      odb  <- buildOdbProxy
      tcsS <- buildTcsSouthController
      tcsN <- buildTcsNorthController
    } yield Systems[F](odb, client, (site === Site.GS).fold(tcsS, tcsN), tcsS, tcsN)
  }

  private def decodeTops(s: String): Map[String, String] =
    s.split("=|,")
      .grouped(2)
      .collect { case Array(k, v) =>
        k.trim -> v.trim
      }
      .toMap

}
