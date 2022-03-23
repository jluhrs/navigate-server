// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client

import cats.Show
import diode.Action
import engage.model.EngageEvent
import engage.model.EngageEvent.ServerLogMessage
import engage.model.security.UserDetails
import lucuma.core.`enum`.Site
import org.scalajs.dom.WebSocket

object Actions {

  // Actions
  final case class Initialize(site: Site) extends Action

  // Actions to close and/open the login box
  case object OpenLoginBox             extends Action
  case object CloseLoginBox            extends Action
  case object OpenUserNotificationBox  extends Action
  case object CloseUserNotificationBox extends Action

  final case class LoggedIn(u: UserDetails) extends Action
  case object Logout                        extends Action
  case object VerifyLoggedStatus            extends Action

  case object SelectRoot extends Action

  final case class AppendToLog(l: ServerLogMessage) extends Action
  final case object ToggleLogArea                   extends Action

  // Actions related to web sockets
  final case class WSConnect(delay: Int)                extends Action
  case object WSClose                                   extends Action
  case object Reconnect                                 extends Action
  case object Connecting                                extends Action
  final case class Connected(ws: WebSocket, delay: Int) extends Action
  final case class ConnectionRetry(delay: Int)          extends Action
  final case class ConnectionError(s: String)           extends Action
  final case class ServerMessage(e: EngageEvent)        extends Action

  final case object FlipSoundOnOff extends Action

  implicit val show: Show[Action] = Show.show { case a =>
    s"$a"
  }
}
