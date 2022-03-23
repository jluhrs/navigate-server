// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.components

import cats.syntax.all._
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.CallbackTo
import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import react.common.implicits._
import react.semanticui.collections.menu._
import react.semanticui.elements.button.Button
import react.semanticui.sizes._
import engage.web.client.Actions.Logout
import engage.web.client.Actions.OpenLoginBox
import engage.web.client.circuit.EngageCircuit
import engage.web.client.Icons._
import engage.web.client.model.ClientStatus
import engage.web.client.reusability._

final case class ControlMenu(status: ClientStatus)
    extends ReactProps[ControlMenu](ControlMenu.component)

/**
 * Menu with options
 */
object ControlMenu {
  implicit val cmReuse: Reusability[ControlMenu] = Reusability.derive

  private val soundConnect =
    EngageCircuit.connect(EngageCircuit.soundSettingReader)

  private val openLogin: Callback =
    EngageCircuit.dispatchCB(OpenLoginBox)
  private val logout: Callback    =
    EngageCircuit.dispatchCB(Logout)

  private def loginButton(enabled: Boolean) =
    Button(size = Medium, onClick = openLogin, disabled = !enabled, inverted = true)("Login")

  private def logoutButton(text: String, enabled: Boolean) =
    Button(size = Medium, onClick = logout, icon = true, disabled = !enabled, inverted = true)(
      IconSignOut,
      text
    )

  private val helpButton =
    Button(size = Medium,
           onClick =
             CallbackTo.windowOpen("http://swg.wikis-internal.gemini.edu/index.php/Engage").void,
           icon = true,
           inverted = true
    )(IconHelp)

  val component = ScalaComponent
    .builder[ControlMenu]("ControlMenu")
    .stateless
    .render_P { p =>
      val status = p.status
      <.div(
        ^.cls := "ui secondary right menu",
        status.user match {
          case Some(u) =>
            Menu(secondary = true, floated = MenuFloated.Right)(
              MenuHeader(clazz =
                EngageStyles.notInMobile |+| EngageStyles.ui |+| EngageStyles.item
              )(
                u.displayName
              ),
              MenuHeader(clazz = EngageStyles.onlyMobile |+| EngageStyles.ui |+| EngageStyles.item)(
                // Ideally we'd do this with css text-overflow but it is not
                // working properly inside a header item, let's abbreviate in code
                u.displayName
                  .split("\\s")
                  .headOption
                  .map(_.substring(0, 10) + "...")
                  .getOrElse[String]("")
              ),
              MenuItem(clazz = EngageStyles.notInMobile)(
                helpButton,
                soundConnect(x => SoundControl(x())),
                logoutButton("Logout", status.isConnected)
              ),
              MenuItem(clazz = EngageStyles.onlyMobile)(
                logoutButton("", status.isConnected)
              )
            )
          case None    =>
            MenuItem()(
              helpButton,
              soundConnect(x => SoundControl(x())),
              loginButton(status.isConnected)
            )
        }
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build

}
