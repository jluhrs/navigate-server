// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server.http4s

import cats.effect.Async
import cats.syntax.all.*
import engage.model.security.UserDetails
import engage.server.EngageEngine
import engage.web.server.security.AuthenticationService
import engage.web.server.security.Http4sAuthentication
import engage.web.server.security.TokenRefresher
import lucuma.graphql.routes.GrackleGraphQLService
import lucuma.graphql.routes.Routes
import natchez.Trace
import org.http4s.AuthedRoutes
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.GZip
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.log4cats.Logger

class GraphQlRoutes[F[_]: Async: Logger: Trace](
  eng: EngageEngine[F]
) extends Http4sDsl[F] {

  private def commandServices(wsb: WebSocketBuilder2[F]): HttpRoutes[F] = GZip(
    Routes.forService(_ => EngageMappings(eng).map(GrackleGraphQLService[F](_).some), wsb, "engage")
  )

  def service(wsb: WebSocketBuilder2[F]): HttpRoutes[F] =
    GZip(commandServices(wsb))

}
