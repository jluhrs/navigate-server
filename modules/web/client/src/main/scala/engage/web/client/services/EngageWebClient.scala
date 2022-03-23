// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.services

import boopickle.Default.{ Pickle, Pickler, Unpickle }
import cats.syntax.all._
import engage.model.boopickle._
import org.scalajs.dom.XMLHttpRequest
import org.scalajs.dom.ext.Ajax
import engage.common.HttpStatusCodes
import engage.model.ClientId
import engage.model.security.UserDetails
import engage.model.security.UserLoginRequest
import scala.annotation.nowarn
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.URIUtils._
import scala.scalajs.js.typedarray.{ ArrayBuffer, TypedArrayBuffer }

/**
 * Encapsulates remote calls to the Engage Web API
 */
object EngageWebClient extends ModelBooPicklers {
  private val baseUrl = "/api/engage"

  // Decodes the binary response with BooPickle, errors are not handled
  def unpickle[A](r: XMLHttpRequest)(implicit u: Pickler[A]): A = {
    val ab = TypedArrayBuffer.wrap(r.response.asInstanceOf[ArrayBuffer])
    Unpickle[A].fromBytes(ab)
  }

  /**
   * Requests the backend to send a copy of the current state
   */
  @nowarn
  def refresh(clientId: ClientId): Future[Unit] =
    Ajax
      .get(
        url = s"$baseUrl/commands/refresh/${encodeURI(clientId.self.show)}"
      )
      .void

  /**
   * Login request
   */
  @nowarn
  def login(u: String, p: String): Future[UserDetails] =
    Ajax
      .post(
        url = s"$baseUrl/login",
        data = Pickle.intoBytes(UserLoginRequest(u, p)),
        responseType = "arraybuffer"
      )
      .map(unpickle[UserDetails])

  /**
   * Logout request
   */
  @nowarn
  def logout(): Future[String] =
    Ajax
      .post(
        url = s"$baseUrl/logout"
      )
      .map(_.responseText)

  /**
   * Ping request
   */
  @nowarn
  def ping(): Future[Int] =
    Ajax
      .get(
        url = "/ping"
      )
      .map(_.status)
      .handleError(_ => HttpStatusCodes.Unauthorized)

  /**
   * Read the site of the server
   */
  @nowarn
  def site(): Future[String] =
    Ajax
      .post(
        url = s"$baseUrl/site"
      )
      .map(_.responseText)

}
