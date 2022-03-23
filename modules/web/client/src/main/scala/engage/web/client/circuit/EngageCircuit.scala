// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.circuit

import scala.scalajs.LinkingInfo

import cats.syntax.all._
import diode._
import diode.react.ReactConnector
import japgolly.scalajs.react.Callback
import engage.model.EngageEvent._
import engage.web.client.Actions.AppendToLog
import engage.web.client.Actions.CloseLoginBox
import engage.web.client.Actions.OpenLoginBox
import engage.web.client.Actions.ServerMessage
import engage.web.client.Actions._
import engage.web.client.Actions.show
import engage.web.client.handlers._
import engage.web.client.model._
import typings.loglevel.mod.{ ^ => logger }

/**
 * Diode processor to log some of the action to aid in debugging
 */
final class LoggingProcessor[M <: AnyRef] extends ActionProcessor[M] {
  override def process(
    dispatch:     Dispatcher,
    action:       Any,
    next:         Any => ActionResult[M],
    currentModel: M
  ): ActionResult[M] = {
    // log some of the actions
    action match {
      case AppendToLog(_)                     =>
      case ServerMessage(_: ServerLogMessage) =>
      case VerifyLoggedStatus                 =>
      case a: Action                          =>
        if (LinkingInfo.developmentMode) logger.info(s"Action: ${a.show}")
      case _                                  =>
    }
    // call the next processor
    next(action)
  }
}

/**
 * Contains the Diode circuit to manipulate the page
 */
object EngageCircuit extends Circuit[EngageAppRootModel] with ReactConnector[EngageAppRootModel] {
  addProcessor(new LoggingProcessor[EngageAppRootModel]())

  // Model read-writers
  val webSocketFocusRW: ModelRW[EngageAppRootModel, WebSocketsFocus] =
    this.zoomRWL(WebSocketsFocus.webSocketFocusL)

  // Reader to indicate the allowed interactions
  val statusReader: ModelRW[EngageAppRootModel, ClientStatus] =
    this.zoomRWL(ClientStatus.clientStatusFocusL)

  // Reader to read/write the sound setting
  val soundSettingReader: ModelR[EngageAppRootModel, SoundSelection] =
    this.zoomL(EngageAppRootModel.soundSettingL)

  val logDisplayedReader: ModelR[EngageAppRootModel, SectionVisibilityState] =
    this.zoomL(EngageAppRootModel.logDisplayL)

  val userLoginRW: ModelRW[EngageAppRootModel, UserLoginFocus] =
    this.zoomRWL(EngageAppRootModel.userLoginFocus)

  private val wsHandler             = new WebSocketHandler(zoomTo(_.ws))
  private val serverMessagesHandler = new ServerMessagesHandler(webSocketFocusRW)
  private val loginBoxHandler       =
    new ModalBoxHandler(OpenLoginBox, CloseLoginBox, zoomTo(_.uiModel.loginBox))
  private val userLoginHandler      = new UserLoginHandler(userLoginRW)
  private val globalLogHandler      = new GlobalLogHandler(zoomTo(_.uiModel.globalLog))
  private val siteHandler           = new SiteHandler(zoomTo(_.site))
  private val soundHandler          = new SoundOnOffHandler(zoomTo(_.uiModel.sound))

  def dispatchCB[A <: Action](a: A): Callback = Callback(dispatch(a))

  override protected def initialModel = EngageAppRootModel.Initial

  override protected def actionHandler =
    composeHandlers(
      wsHandler,
      serverMessagesHandler,
      loginBoxHandler,
      userLoginHandler,
      globalLogHandler,
      siteHandler,
      soundHandler
    )

  /**
   * Handles a fatal error most likely during action processing
   */
  override def handleFatal(action: Any, e: Throwable): Unit = {
    logger.error(s"Action not handled $action")
    super.handleFatal(action, e)
  }

  /**
   * Handle a non-fatal error, such as dispatching an action with no action handler.
   */
  override def handleError(msg: String): Unit =
    logger.error(s"Action error $msg")

}
