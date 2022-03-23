// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.handlers

import cats.implicits._
import diode._
import org.scalajs.dom.window

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.matching.Regex
import engage.model.EngageEvent._
import engage.web.client.Actions._
import engage.web.client.circuit._
import engage.web.client.model.SoundSelection
import engage.web.client.services.EngageWebClient

/**
 * Handles messages received over the WS channel
 */
class ServerMessagesHandler[M](modelRW: ModelRW[M, WebSocketsFocus])
    extends ActionHandler(modelRW)
    with Handlers[M, WebSocketsFocus] {

  def loggedIn: Boolean           = value.sound === SoundSelection.SoundOn
  def ifLoggedIn[A]: A => Boolean = (_: A) => loggedIn

  val connectionOpenMessage: PartialFunction[Any, ActionResult[M]] = {
    case ServerMessage(ConnectionOpenEvent(u, c, v)) =>
      // After connected to the Websocket request a refresh
      val refreshRequestE = Effect(EngageWebClient.refresh(c).as(NoAction))
      val openEffect      =
        if (value.serverVersion.exists(_ =!= v)) {
          Effect(Future(window.location.reload(true)).as(NoAction))
        } else {
          refreshRequestE
        }
      val displayNames    =
        (u.map(u =>
          if (value.displayNames.contains(u.username))
            value.displayNames
          else value.displayNames + (u.username -> u.displayName)
        )).getOrElse(value.displayNames)
      updated(
        value.copy(user = u,
                   displayNames = displayNames,
                   clientId = c.some,
                   serverVersion = v.some
        ),
        openEffect
      )
  }

  val MsgRegex: Regex  = "Application exception: (.*)".r
  val InstRegex: Regex = "Sequence execution failed with error: (.*)".r

  val defaultMessage: PartialFunction[Any, ActionResult[M]] = { case ServerMessage(_) =>
    // Ignore unknown events
    noChange
  }

  override def handle: PartialFunction[Any, ActionResult[M]] =
    List(
      connectionOpenMessage,
      defaultMessage
    ).combineAll
}
