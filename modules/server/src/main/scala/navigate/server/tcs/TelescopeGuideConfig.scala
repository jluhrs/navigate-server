// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.{Eq, Show}

/** Data type for guide config. */
final case class TelescopeGuideConfig(
  mountGuide: Boolean,
  m1Guide:    M1GuideConfig,
  m2Guide:    M2GuideConfig
)

object TelescopeGuideConfig {
  implicit val eq: Eq[TelescopeGuideConfig] =
    Eq.by(x => (x.mountGuide, x.m1Guide, x.m2Guide))

  implicit val show: Show[TelescopeGuideConfig] = Show.fromToString[TelescopeGuideConfig]
}
