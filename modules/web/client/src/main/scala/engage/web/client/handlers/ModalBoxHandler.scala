// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.handlers

import cats.syntax.all._
import diode.{ Action, ActionHandler, ActionResult, ModelRW }
import engage.web.client.model.SectionVisibilityState
import engage.web.client.model.SectionVisibilityState._

/**
 * Handles actions related to opening/closing a modal
 */
class ModalBoxHandler[M](
  openAction:  Action,
  closeAction: Action,
  modelRW:     ModelRW[M, SectionVisibilityState]
) extends ActionHandler(modelRW)
    with Handlers[M, SectionVisibilityState] {
  def openModal: PartialFunction[Any, ActionResult[M]] = {
    case x if x == openAction && value === SectionClosed =>
      updated(SectionOpen)

    case x if x == openAction =>
      noChange
  }

  def closeModal: PartialFunction[Any, ActionResult[M]] = {
    case x if x == closeAction && value === SectionOpen =>
      updated(SectionClosed)

    case x if x == closeAction =>
      noChange
  }

  override def handle: PartialFunction[Any, ActionResult[M]] =
    openModal |+| closeModal
}
