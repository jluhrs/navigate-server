// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.handlers

import cats.syntax.all._
import diode._
import engage.common.HttpStatusCodes
import engage.web.client.Actions._
import engage.web.client.services.EngageWebClient
import engage.web.client.circuit.UserLoginFocus

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/**
 * Handles actions related to opening/closing the login box
 */
class UserLoginHandler[M](modelRW: ModelRW[M, UserLoginFocus])
    extends ActionHandler(modelRW)
    with Handlers[M, UserLoginFocus] {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case LoggedIn(u) =>
      val dn        = value.displayNames + (u.username -> u.displayName)
      // Close the login box
      val effect    = Effect(Future(CloseLoginBox))
      // Close the websocket and reconnect
      val reconnect = Effect(Future(Reconnect))
      updated(value.copy(user = u.some, displayNames = dn), reconnect + effect)

    case VerifyLoggedStatus =>
      val effect = Effect(EngageWebClient.ping().map {
        case HttpStatusCodes.Unauthorized if value.user.isDefined => Logout
        case _                                                    => NoAction
      })
      effectOnly(effect)

    case Logout =>
      val dn        = value.displayNames.removed(value.user.foldMap(_.username))
      val effect    = Effect(EngageWebClient.logout().as(NoAction))
      val reConnect = Effect(Future(Reconnect))
      // Remove the user and call logout
      updated(value.copy(user = none, displayNames = dn), effect + reConnect)
  }
}
