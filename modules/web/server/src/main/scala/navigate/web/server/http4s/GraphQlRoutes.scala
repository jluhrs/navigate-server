// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.effect.Async
import cats.syntax.all.*
import fs2.compression.Compression
import lucuma.graphql.routes.GraphQLService
import lucuma.graphql.routes.Routes
import natchez.Trace
import navigate.model.security.UserDetails
import navigate.server.NavigateEngine
import navigate.web.server.security.AuthenticationService
import navigate.web.server.security.Http4sAuthentication
import navigate.web.server.security.TokenRefresher
import org.http4s.AuthedRoutes
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.GZip
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.log4cats.Logger

class GraphQlRoutes[F[_]: Async: Logger: Trace: Compression](
  eng: NavigateEngine[F]
) extends Http4sDsl[F] {

  private def commandServices(wsb: WebSocketBuilder2[F]): HttpRoutes[F] = GZip(
    Routes.forService(_ => NavigateMappings(eng).map(GraphQLService[F](_).some), wsb, "navigate")
  )

  def service(wsb: WebSocketBuilder2[F]): HttpRoutes[F] =
    GZip(commandServices(wsb))

}
