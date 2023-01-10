// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage

import cats.Eq

import java.util.UUID

package model {
  final case class ClientId(self: UUID) extends AnyVal
}
package object model {

  implicit val clientIdEq: Eq[ClientId] = Eq.by(x => x.self)

}
