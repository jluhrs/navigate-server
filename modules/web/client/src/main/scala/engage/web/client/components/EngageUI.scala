// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.components

import cats.effect.Sync
import cats.syntax.all._
import diode.ModelRO
import engage.web.client.Actions.WSConnect
import engage.web.client.circuit.EngageCircuit
import engage.web.client.model.Pages.{ EngagePages, Root }
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.enum.Site

import scala.scalajs.js.timers.SetTimeoutHandle

/**
 * UI Router
 */
object EngageUI {
  private def pageTitle(site: Site): String = s"Engage - ${site.shortName}"

  def router[F[_]](site: Site)(implicit F: Sync[F]): F[Router[EngagePages]] = {

    val routerConfig = RouterConfigDsl[EngagePages].buildConfig { dsl =>
      import dsl._

      (emptyRule
        | staticRoute(root, Root) ~> renderR(r => EngageMain(site, r)))
        .notFound(redirectToPage(Root)(SetRouteVia.HistoryPush))
        // Runtime verification that all pages are routed
        .renderWith { case (_, r) => <.div(r.render()) }
        .setTitle(_ => pageTitle(site))
        .logToConsole
    }

    def navigated(
      routerLogic: RouterLogic[EngagePages, Unit],
      page:        ModelRO[EngagePages]
    ): SetTimeoutHandle =
      scalajs.js.timers.setTimeout(0)(routerLogic.ctl.set(page.value).runNow())

    for {
      r                    <- F.delay(Router.componentAndLogic(BaseUrl.fromWindowOrigin, routerConfig))
      (router, routerLogic) = r
      // subscribe to navigation changes
      _                    <- F.delay(EngageCircuit.subscribe(EngageCircuit.zoom(_.uiModel.navLocation)) { x =>
                                navigated(routerLogic, x); ()
                              })
      // Initiate the WebSocket connection
      _                    <- F.delay(EngageCircuit.dispatch(WSConnect(0)))
    } yield router
  }

}
