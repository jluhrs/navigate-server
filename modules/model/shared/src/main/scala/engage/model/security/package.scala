// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.model

import cats.Eq
import io.circe.Decoder
import io.circe.Encoder

package security {
  // Shared classes used for authentication
  case class UserLoginRequest(username: String, password: String) derives Decoder

  object UserLoginRequest {
    given Eq[UserLoginRequest] = Eq.by(x => (x.username, x.password))
  }

  case class UserDetails(username: String, displayName: String) derives Encoder.AsObject

  object UserDetails {
    // Some useful type aliases for user elements
    type UID         = String
    type DisplayName = String
    type Groups      = List[String]
    type Thumbnail   = Array[Byte]

    given Eq[UserDetails] = Eq.by(x => (x.username, x.displayName))
  }

}
