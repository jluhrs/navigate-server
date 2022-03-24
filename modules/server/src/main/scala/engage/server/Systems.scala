// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server

import cats.{ Applicative, Monad }
import cats.syntax.all._
import engage.model.config.EngageEngineConfiguration
import org.http4s.client.Client
import engage.server.tcs.{ TcsNorthController, TcsSouthController }
import lucuma.core.`enum`.Site

import scala.annotation.nowarn

final case class Systems[F[_]](
  odb:      OdbProxy[F],
  tcsSouth: TcsSouthController[F],
  tcsNorth: TcsNorthController[F]
)

object Systems {
  def build[F[_]: Monad](
    site:         Site,
    client:       Client[F],
    @nowarn conf: EngageEngineConfiguration
  ): F[Systems[F]] = for {
    odb  <- buildOdbProxy(site, client)
    tcsS <- buildTcsSouthController
    tcsN <- buildTcsNorthController
  } yield Systems[F](odb, tcsS, tcsN)

  // These are placeholders.
  def buildOdbProxy[F[_]: Applicative](
    @nowarn site:   Site,
    @nowarn client: Client[F]
  ): F[OdbProxy[F]] = OdbProxy.build

  def buildTcsSouthController[F[_]: Applicative]: F[TcsSouthController[F]] =
    new TcsSouthController[F] {}.pure

  def buildTcsNorthController[F[_]: Applicative]: F[TcsNorthController[F]] =
    new TcsNorthController[F] {}.pure

}
