// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server.http4s

import cats.effect.Async
import engage.model.security.UserDetails
import engage.server.EngageEngine
import engage.web.server.security.{ AuthenticationService, Http4sAuthentication, TokenRefresher }
import org.http4s.{ AuthedRoutes, HttpRoutes }
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.GZip
import lucuma.core.model.Observation.{ Id => ObsId }

import scala.annotation.nowarn

class EngageCommandRoutes[F[_]: Async, I](
  auth:        AuthenticationService[F],
  @nowarn eng: EngageEngine[F, I]
) extends Http4sDsl[F] {
  // Handles authentication
  private val httpAuthentication = new Http4sAuthentication(auth)

  private val commandServices: AuthedRoutes[UserDetails, F] = AuthedRoutes.of {
    case POST -> Root / "load" / ObsId(obsId) / ClientIDVar(_) as _ =>
      Ok(s"Set selected observation $obsId")

  }

  val service: HttpRoutes[F] =
    TokenRefresher(GZip(httpAuthentication.reqAuth(commandServices)), httpAuthentication)

}
