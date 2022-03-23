// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.model

import scala.scalajs.js.timers._
import cats._
import cats.syntax.all._
import lucuma.core.enum.Site
import monocle.Lens
import monocle.macros.Lenses
import engage.model.ClientId
import engage.web.client.circuit.UserLoginFocus

/**
 * Root of the UI Model of the application
 */
@Lenses
final case class EngageAppRootModel(
  ws:            WebSocketConnection,
  site:          Option[Site],
  clientId:      Option[ClientId],
  uiModel:       EngageUIModel,
  serverVersion: Option[String],
  pingInterval:  Option[SetTimeoutHandle]
)

object EngageAppRootModel {

  val Initial: EngageAppRootModel = EngageAppRootModel(
    WebSocketConnection.Empty,
    none,
    none,
    EngageUIModel.Initial,
    none,
    none
  )

  val logDisplayL: Lens[EngageAppRootModel, SectionVisibilityState] =
    EngageAppRootModel.uiModel.andThen(EngageUIModel.globalLog).andThen(GlobalLog.display)

  val userLoginFocus: Lens[EngageAppRootModel, UserLoginFocus] =
    EngageAppRootModel.uiModel.andThen(EngageUIModel.userLoginFocus)

  val soundSettingL: Lens[EngageAppRootModel, SoundSelection] =
    EngageAppRootModel.uiModel.andThen(EngageUIModel.sound)

  implicit val eq: Eq[EngageAppRootModel] =
    Eq.by(x => (x.ws, x.site, x.clientId, x.uiModel, x.serverVersion))
}
