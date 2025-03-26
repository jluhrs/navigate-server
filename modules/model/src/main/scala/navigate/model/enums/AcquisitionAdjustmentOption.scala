// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

enum AcquisitionAdjustmentOption(val tag: String) derives Enumerated:
  case AskUser      extends AcquisitionAdjustmentOption("ASK_USER")
  case UserConfirms extends AcquisitionAdjustmentOption("USER_CONFIRMS")
