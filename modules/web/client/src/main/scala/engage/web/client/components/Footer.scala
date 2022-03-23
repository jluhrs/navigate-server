// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.components

import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import lucuma.core.enum.Site
import react.clipboard.CopyToClipboard
import react.common._
import react.semanticui.collections.menu._
import react.semanticui.modules.popup.Popup
import react.semanticui.modules.popup.PopupPosition
import react.semanticui.sizes._
import engage.web.client.OcsBuildInfo
import engage.web.client.circuit.EngageCircuit
import engage.web.client.model.Pages._
import engage.web.client.reusability._
import engage.web.client.Actions.SelectRoot

final case class Footer(router: RouterCtl[EngagePages], site: Site)
    extends ReactProps[Footer](Footer.component)

/**
 * Component for the bar at the top of the page
 */
object Footer {
  type Props = Footer

  implicit val propsReuse: Reusability[Props] = Reusability.by(_.site)

  private val userConnect = EngageCircuit.connect(EngageCircuit.statusReader)

  private def goHome(p: Props)(e: ReactEvent): Callback =
    e.preventDefaultCB *>
      p.router.dispatchAndSetUrlCB(SelectRoot)

  private val component = ScalaComponent
    .builder[Props]
    .stateless
    .render_P(p =>
      Menu(
        clazz = EngageStyles.Footer,
        inverted = true
      )(
        MenuItem(
          as = "a",
          header = true,
          clazz = EngageStyles.notInMobile,
          onClickE = goHome(p) _
        )(s"Engage - ${p.site.shortName}"),
        Popup(
          position = PopupPosition.TopCenter,
          size = Tiny,
          trigger = MenuItem(as = <.a, header = true, clazz = EngageStyles.notInMobile)(
            CopyToClipboard(text = OcsBuildInfo.version)(
              OcsBuildInfo.version
            )
          )
        )(
          "Copy to clipboard"
        ),
        userConnect(x => FooterStatus(x()))
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build

}
