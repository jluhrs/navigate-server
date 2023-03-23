// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

case class SlewOptions(
  zeroChopThrow:            Boolean,
  zeroSourceOffset:         Boolean,
  zeroSourceDiffTrack:      Boolean,
  zeroMountOffset:          Boolean,
  zeroMountDiffTrack:       Boolean,
  shortcircuitTargetFilter: Boolean,
  shortcircuitMountFilter:  Boolean,
  resetPointing:            Boolean,
  stopGuide:                Boolean,
  zeroGuideOffset:          Boolean,
  zeroInstrumentOffset:     Boolean,
  autoparkPwfs1:            Boolean,
  autoparkPwfs2:            Boolean,
  autoparkOiwfs:            Boolean,
  autoparkGems:             Boolean,
  autoparkAowfs:            Boolean
)

object SlewOptions {
  val default: SlewOptions = SlewOptions(
    false,
    false,
    false,
    false,
    false,
    false,
    false,
    false,
    false,
    false,
    false,
    false,
    false,
    false,
    false,
    false
  )
}
