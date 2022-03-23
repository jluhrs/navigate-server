// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.components

import cats.syntax.all._
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import react.common.implicits._
import react.semanticui.elements.header.Header
import engage.web.client.circuit.EngageCircuit
import engage.web.client.model.ClientStatus
import engage.web.client.reusability._

final case class FooterStatus(status: ClientStatus)
    extends ReactProps[FooterStatus](FooterStatus.component)

/**
 * Chooses to display either the guide config or a connection status info
 */
object FooterStatus {

  type Props = FooterStatus

  implicit val propsReuse: Reusability[Props] = Reusability.derive[Props]
  private val wsConnect                       = EngageCircuit.connect(_.ws)

  private val component = ScalaComponent
    .builder[Props]
    .stateless
    .render_P(p =>
      React.Fragment(
        Header(sub = true, clazz = EngageStyles.item |+| EngageStyles.notInMobile)(
          wsConnect(x => ConnectionState(x())).unless(p.status.isConnected)
        ),
        ControlMenu(p.status)
      )
    )
    .configure(Reusability.shouldComponentUpdate)
    .build

}
