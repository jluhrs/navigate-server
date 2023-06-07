// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import lucuma.core.math.Angle

case class SlewConfig(
  slewOptions: SlewOptions,
  baseTarget:  Target,
  rotIaa: Angle
)
