// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server

import cats.effect.std.Dispatcher
import cats.effect.{Async, Resource}
import cats.Parallel
import cats.syntax.all._
import engage.epics.EpicsService
import engage.model.config.{ControlStrategy, EngageEngineConfiguration}
import org.http4s.client.Client
import engage.server.tcs._
import lucuma.core.`enum`.Site

import scala.annotation.nowarn

final case class Systems[F[_]](
  odb:      OdbProxy[F],
  tcsSouth: TcsSouthController[F],
  tcsNorth: TcsNorthController[F]
)

object Systems {
  def build[F[_]: Async: Dispatcher: Parallel](
    @nowarn site:         Site,
    @nowarn client:       Client[F],
    conf: EngageEngineConfiguration,
    epicsSrv: EpicsService[F]
  ): Resource[F, Systems[F]] = {
    val tops =decodeTops(conf.tops)

    // These are placeholders.
    def buildOdbProxy: Resource[F, OdbProxy[F]] = Resource.eval(OdbProxy.build)

    def buildTcsSouthController: Resource[F, TcsSouthController[F]] =
      if(conf.systemControl.tcs === ControlStrategy.FullControl)
        TcsEpics.build(epicsSrv, tops).map(new TcsSouthControllerEpics(_, conf.ioTimeout))
      else
        Resource.pure(new TcsSouthControllerSim)

    def buildTcsNorthController: Resource[F, TcsNorthController[F]] =
      if(conf.systemControl.tcs === ControlStrategy.FullControl)
        TcsEpics.build(epicsSrv, tops).map(new TcsNorthControllerEpics(_, conf.ioTimeout))
      else
        Resource.pure(new TcsNorthControllerSim)

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
