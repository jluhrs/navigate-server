// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.services

import cats.Show
import cats.syntax.all._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object WebpackResources {

  // marker trait
  trait WebpackResource extends js.Object

  object WebpackResource {
    implicit val show: Show[WebpackResource] = Show.fromToString
  }

  implicit class WebpackResourceOps(val r: WebpackResource) extends AnyVal {
    def resource: String = r.show
  }

  @JSImport("sounds/beep-22.mp3", JSImport.Default)
  @js.native
  object BeepResourceMP3 extends WebpackResource

  @JSImport("sounds/beep-22.webm", JSImport.Default)
  @js.native
  object BeepResourceWebM extends WebpackResource

  @JSImport("sounds/soundon.mp3", JSImport.Default)
  @js.native
  object SoundOnMP3 extends WebpackResource

  @JSImport("sounds/soundon.webm", JSImport.Default)
  @js.native
  object SoundOnWebM extends WebpackResource

}
