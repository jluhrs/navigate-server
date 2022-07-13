// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.components

import react.common.style._

/**
 * Custom CSS for the Observe UI
 */
object EngageStyles {

  val headerHeight: Int = 33

  val MainUI: Css =
    Css("ObserveStyles-mainUI")

  val Footer: Css =
    Css("ObserveStyles-footer")

  val LoginError: Css =
    Css("ObserveStyles-loginError")

  // Sometimes we need to manually add css
  val item: Css = Css("item")

  val ui: Css = Css("ui")

  val header: Css = Css("header")

  // Media queries to hide/display items for mobile
  val notInMobile: Css = Css("ObserveStyles-notInMobile")

  val onlyMobile: Css = Css("ObserveStyles-onlyMobile")

  val errorText: Css = Css("ObserveStyles-errorText")

  val titleRow: Css = Css("ObserveStyles-titleRow")

  val blinking: Css = Css("ObserveStyles-blinking")

  val logArea: Css = Css("ObserveStyles-logArea")
}
