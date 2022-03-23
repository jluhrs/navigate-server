// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client

import diode.data.PotState
import japgolly.scalajs.react.ReactCats._
import japgolly.scalajs.react.Reusability
import lucuma.core.util.Enumerated
import react.common._
import react.semanticui.SemanticColor
import react.semanticui.SemanticSize
import engage.model.enum.ServerLogLevel
import engage.model.security.UserDetails
import engage.web.client.model.ClientStatus
import engage.web.client.model.GlobalLog
import engage.web.client.model.WebSocketConnection
import shapeless.tag.@@
import squants.Time

package object reusability {
  implicit def enumeratedReuse[A <: AnyRef: Enumerated]: Reusability[A] =
    Reusability.byRef
  implicit def taggedInt[A]: Reusability[Int @@ A]                      =
    Reusability.by(x => x: Int)
  implicit val timeReuse: Reusability[Time]                             = Reusability.by(_.toMilliseconds.toLong)
  implicit val colorReuse: Reusability[SemanticColor]                   = Reusability.by(_.toJs)
  implicit val cssReuse: Reusability[Css]                               = Reusability.by(_.htmlClass)
  implicit val clientStatusReuse: Reusability[ClientStatus]             = Reusability.byEq
  implicit val potStateReuse: Reusability[PotState]                     = Reusability.byRef
  implicit val webSCeuse: Reusability[WebSocketConnection]              =
    Reusability.by(_.ws.state)
  implicit val userDetailsReuse: Reusability[UserDetails]               = Reusability.byEq
  implicit val globalLogReuse: Reusability[GlobalLog]                   = Reusability.byEq
  implicit val sllbMap: Reusability[Map[ServerLogLevel, Boolean]]       =
    Reusability.map
  implicit val reuse: Reusability[SemanticSize]                         = Reusability.byRef[SemanticSize]
}
