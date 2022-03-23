// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.handlers

import diode.{ ActionHandler, ActionResult, ModelRW }
import engage.web.client.Actions.{ AppendToLog, ToggleLogArea }
import engage.web.client.model.GlobalLog

/**
 * Handles updates to the log
 */
class GlobalLogHandler[M](modelRW: ModelRW[M, GlobalLog])
    extends ActionHandler(modelRW)
    with Handlers[M, GlobalLog] {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case AppendToLog(s) =>
      updated(value.copy(log = value.log.append(s)))

    case ToggleLogArea =>
      updated(value.copy(display = value.display.toggle))
  }
}
