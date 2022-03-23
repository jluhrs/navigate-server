// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.circuit

import cats.Eq
import engage.model.security.UserDetails

final case class UserLoginFocus(user: Option[UserDetails], displayNames: Map[String, String]) {
  val displayName: Option[String] = user.flatMap(u => displayNames.get(u.username))
}

object UserLoginFocus {
  implicit val eqUserLoginFocus: Eq[UserLoginFocus] = Eq.by(u => (u.user, u.displayNames))
}
