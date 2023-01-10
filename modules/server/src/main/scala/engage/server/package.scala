// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage

import scala.concurrent.duration._

package object server {
  val ConnectionTimeout: FiniteDuration = FiniteDuration.apply(1, SECONDS)
}
