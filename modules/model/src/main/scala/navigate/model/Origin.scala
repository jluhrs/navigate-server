// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model

import cats.Show
import navigate.model.Distance

case class Origin(
  x: Distance,
  y: Distance
)

object Origin {
  given Show[Origin] = Show.show { o =>
    f"Origin(x: ${o.x.toMillimeters.value.toDouble}%.2f, y: ${o.y.toMillimeters.value.toDouble}%.2f)"
  }
}
