// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.{Eq, Show}
import cats.derived.*

/** Data type for guide config. */
case class TelescopeGuideConfig(
  mountGuide: Boolean,
  m1Guide:    M1GuideConfig,
  m2Guide:    M2GuideConfig
) derives Eq, Show