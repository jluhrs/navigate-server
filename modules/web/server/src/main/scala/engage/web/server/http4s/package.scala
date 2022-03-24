// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server

import cats.syntax.all._
import engage.model.ClientId

package object http4s {
  object ClientIDVar {
    def unapply(str: String): Option[ClientId] =
      Either.catchNonFatal(ClientId(java.util.UUID.fromString(str))).toOption
  }

}
