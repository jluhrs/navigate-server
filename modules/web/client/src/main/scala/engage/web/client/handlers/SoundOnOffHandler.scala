// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.handlers

import diode.{ ActionHandler, ActionResult, ModelRW }
import engage.web.client.Actions.FlipSoundOnOff
import engage.web.client.model.SoundSelection

/**
 * Handles changing the user selection
 */
class SoundOnOffHandler[M](modelRW: ModelRW[M, SoundSelection])
    extends ActionHandler(modelRW)
    with Handlers[M, SoundSelection] {
  override def handle: PartialFunction[Any, ActionResult[M]] = { case FlipSoundOnOff =>
    updatedL(SoundSelection.flip)
  }
}
