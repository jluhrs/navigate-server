// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import cats.Eq
import cats.derived.*

enum LightSource extends Product with Serializable derives Eq {
  case Sky extends LightSource

  case AO extends LightSource

  case GCAL extends LightSource
}
