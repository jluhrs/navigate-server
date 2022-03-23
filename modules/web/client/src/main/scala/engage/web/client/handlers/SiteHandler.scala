// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.handlers

import diode.{ ActionHandler, ActionResult, ModelRW }
import engage.web.client.Actions.Initialize
import lucuma.core.`enum`.Site

/**
 * Handles setting the site
 */
class SiteHandler[M](modelRW: ModelRW[M, Option[Site]])
    extends ActionHandler(modelRW)
    with Handlers[M, Option[Site]] {

  override def handle: PartialFunction[Any, ActionResult[M]] = { case Initialize(site) =>
    updated(Some(site))
  }
}
