// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.enums

import lucuma.core.util.Enumerated

enum PwfsFieldStop(val tag: String) derives Enumerated {
  case Prism extends PwfsFieldStop("prism")
  case Fs10  extends PwfsFieldStop("10.0")
  case Fs6_4 extends PwfsFieldStop("6.4")
  case Fs3_2 extends PwfsFieldStop("3.2")
  case Fs1_6 extends PwfsFieldStop("1.6")
  case Open1 extends PwfsFieldStop("open1")
  case Open2 extends PwfsFieldStop("open2")
  case Open3 extends PwfsFieldStop("open3")
  case Open4 extends PwfsFieldStop("open4")
}
