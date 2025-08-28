// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model

import cats.Eq
import cats.derived.*
import navigate.model.enums.AcFilter
import navigate.model.enums.AcLens
import navigate.model.enums.AcNdFilter

case class AcMechsState(
  lens:     AcLens,
  ndFilter: AcNdFilter,
  filter:   AcFilter
) derives Eq
