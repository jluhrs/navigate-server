// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.config

import cats.*

/**
 * Indicates how each subsystems is treated, e.g. full connection or simulated
 */
case class SystemsControlConfiguration(
  altair: ControlStrategy,
  gems:   ControlStrategy,
  gcal:   ControlStrategy,
  gpi:    ControlStrategy,
  gsaoi:  ControlStrategy,
  tcs:    ControlStrategy
) {
  def connectEpics: Boolean =
    altair.connect || gems.connect || gcal.connect || tcs.connect
}

object SystemsControlConfiguration {
  given Eq[SystemsControlConfiguration] =
    Eq.by(x => (x.altair, x.gems, x.gcal, x.gpi, x.gsaoi, x.tcs))

}
