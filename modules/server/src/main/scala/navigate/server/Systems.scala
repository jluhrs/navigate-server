// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server

import cats.Parallel
import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.enums.Site
import navigate.epics.EpicsService
import navigate.model.config.ControlStrategy
import navigate.model.config.NavigateEngineConfiguration
import navigate.server.tcs.*
import org.http4s.client.Client

import scala.annotation.nowarn

case class Systems[F[_]](
  odb:      OdbProxy[F],
  client:   Client[F],
  tcsSouth: TcsSouthController[F],
  tcsNorth: TcsNorthController[F]
)

object Systems {
  def build[F[_]: Async: Dispatcher: Parallel](
    @nowarn site: Site,
    client:       Client[F],
    conf:         NavigateEngineConfiguration,
    epicsSrv:     EpicsService[F]
  ): Resource[F, Systems[F]] = {
    val tops = decodeTops(conf.tops)

    // These are placeholders.
    def buildOdbProxy: Resource[F, OdbProxy[F]] = Resource.eval(OdbProxy.build)

    def buildTcsSouthController: Resource[F, TcsSouthController[F]] =
      if (conf.systemControl.tcs === ControlStrategy.FullControl)
        for {
          tcs <- TcsEpicsSystem.build(epicsSrv, tops)
          p1  <- WfsEpicsSystem.build(epicsSrv,
                                      "PWFS1",
                                      readTop(tops, NonEmptyString.unsafeFrom("pwfs1"))
                 )
          p2  <- WfsEpicsSystem.build(epicsSrv,
                                      "PWFS2",
                                      readTop(tops, NonEmptyString.unsafeFrom("pwfs2"))
                 )
          oi  <- WfsEpicsSystem.build(epicsSrv,
                                      "OIWFS",
                                      readTop(tops, NonEmptyString.unsafeFrom("oiwfs")),
                                      NonEmptyString.unsafeFrom("dc:initSigInit.MARK")
                 )
        } yield new TcsSouthControllerEpics(tcs, p1, p2, oi, conf.ioTimeout)
      else
        Resource.eval(TcsSouthControllerSim.build)

    def buildTcsNorthController: Resource[F, TcsNorthController[F]] =
      if (conf.systemControl.tcs === ControlStrategy.FullControl)
        for {
          tcs <- TcsEpicsSystem.build(epicsSrv, tops)
          p1  <- WfsEpicsSystem.build(epicsSrv,
                                      "PWFS1",
                                      readTop(tops, NonEmptyString.unsafeFrom("pwfs1"))
                 )
          p2  <- WfsEpicsSystem.build(epicsSrv,
                                      "PWFS2",
                                      readTop(tops, NonEmptyString.unsafeFrom("pwfs2"))
                 )
          oi  <- WfsEpicsSystem.build(epicsSrv,
                                      "OIWFS",
                                      readTop(tops, NonEmptyString.unsafeFrom("oiwfs")),
                                      NonEmptyString.unsafeFrom("dc:initSigInit.MARK")
                 )
        } yield new TcsNorthControllerEpics(tcs, p1, p2, oi, conf.ioTimeout)
      else
        Resource.eval(TcsNorthControllerSim.build)

    for {
      odb  <- buildOdbProxy
      tcsS <- buildTcsSouthController
      tcsN <- buildTcsNorthController
    } yield Systems[F](odb, client, tcsS, tcsN)
  }

  private def decodeTops(s: String): Map[String, String] =
    s.split("=|,")
      .grouped(2)
      .collect { case Array(k, v) =>
        k.trim -> v.trim
      }
      .toMap

}
