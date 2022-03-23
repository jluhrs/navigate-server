// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.circuit

import cats._
import lucuma.core.enum.Site
import monocle.Lens
import monocle.macros.Lenses
import engage.model._
import engage.model.security.UserDetails
import engage.web.client.model.EngageAppRootModel
import engage.web.client.model.SoundSelection

@Lenses
final case class WebSocketsFocus(
  user:          Option[UserDetails],
  displayNames:  Map[String, String],
  clientId:      Option[ClientId],
  site:          Option[Site],
  sound:         SoundSelection,
  serverVersion: Option[String]
)

object WebSocketsFocus {
  implicit val eq: Eq[WebSocketsFocus] =
    Eq.by(x => (x.user, x.displayNames, x.clientId, x.site, x.serverVersion))

  val webSocketFocusL: Lens[EngageAppRootModel, WebSocketsFocus] =
    Lens[EngageAppRootModel, WebSocketsFocus](m =>
      WebSocketsFocus(
        m.uiModel.user,
        m.uiModel.displayNames,
        m.clientId,
        m.site,
        m.uiModel.sound,
        m.serverVersion
      )
    )(v =>
      m =>
        m.copy(
          uiModel = m.uiModel.copy(
            user = v.user,
            displayNames = v.displayNames,
            sound = v.sound
          ),
          clientId = v.clientId,
          site = v.site,
          serverVersion = v.serverVersion
        )
    )
}
