// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.effect.Async
import cats.syntax.all._
import lucuma.core.model.Observation.{Id => ObsId}
import navigate.model.security.UserDetails
import navigate.server.NavigateEngine
import navigate.web.server.security.AuthenticationService
import navigate.web.server.security.Http4sAuthentication
import navigate.web.server.security.TokenRefresher
import org.http4s.AuthedRoutes
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.GZip
import squants.space.AngleConversions._

class NavigateCommandRoutes[F[_]: Async](
  auth: AuthenticationService[F],
  eng:  NavigateEngine[F]
) extends Http4sDsl[F] {
  // Handles authentication
  private val httpAuthentication = new Http4sAuthentication(auth)

  private val commandServices: AuthedRoutes[UserDetails, F] = AuthedRoutes.of {
    case POST -> Root / "load" / ObsId(obsId) / ClientIDVar(_) as _ =>
      Ok(s"Set selected observation $obsId")

    case POST -> Root / "mcsPark" / ClientIDVar(_) as _ =>
      eng.mcsPark *>
        Ok(s"Park MCS")

    case POST -> Root / "mcsFollow" / BooleanVar(en) / ClientIDVar(_) as _ =>
      eng.mcsFollow(en) *>
        Ok(s"Follow MCS ($en)")

    case POST -> Root / "crcsStop" / BooleanVar(en) / ClientIDVar(_) as _ =>
      eng.rotStop(en) *>
        Ok(s"Stop CRCS ($en)")

    case POST -> Root / "crcsPark" / ClientIDVar(_) as _ =>
      eng.rotPark *>
        Ok(s"Park CRCS")

    case POST -> Root / "crcsFollow" / BooleanVar(en) / ClientIDVar(_) as _ =>
      eng.rotFollow(en) *>
        Ok(s"Follow CRCS ($en)")

    case POST -> Root / "crcsMove" / DoubleVar(angle) / ClientIDVar(_) as _ =>
      eng.rotMove(angle.degrees) *>
        Ok(s"Move CRCS ($angle)")

    case POST -> Root / "ecsCarouselMode" / DomeModeVar(domeMode) / ShutterModeVar(
          shutterMode
        ) / DoubleVar(slitHeight) / BooleanVar(domeEnable) / BooleanVar(
          shutterEnable
        ) / ClientIDVar(_) as _ =>
      eng.ecsCarouselMode(domeMode, shutterMode, slitHeight, domeEnable, shutterEnable) *>
        Ok(s"Carousel Mode ($domeMode, $shutterMode, $slitHeight, $domeEnable, $shutterEnable)")

    case POST -> Root / "ecsVentGatesMove" / DoubleVar(east) / DoubleVar(west) / ClientIDVar(
          _
        ) as _ =>
      eng.ecsVentGatesMove(east, west) *>
        Ok(s"Move Vent Gates ($east, $west)")
  }

  val service: HttpRoutes[F] =
    TokenRefresher(GZip(httpAuthentication.reqAuth(commandServices)), httpAuthentication)

}
