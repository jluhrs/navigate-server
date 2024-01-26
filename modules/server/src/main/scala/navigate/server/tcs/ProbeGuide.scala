// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Eq
import cats.Show
import cats.derived.*
import lucuma.ags.GuideProbe

/** Data type for guide config. */
case class ProbeGuide(
  from: GuideProbe,
  to:   GuideProbe
) derives Eq,
      Show
