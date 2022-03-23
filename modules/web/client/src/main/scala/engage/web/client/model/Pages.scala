// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.model

import cats.Eq
import cats.syntax.all._
import diode.Action
import engage.web.client.Actions._
import engage.web.client.circuit.EngageCircuit
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.extra.router.RouterCtl
import monocle.Prism

import scala.annotation.nowarn

object Pages {

  sealed trait EngagePages extends Product with Serializable

  case object Root extends EngagePages

  implicit val engagePagesEq: Eq[EngagePages] = Eq.instance {
    case (Root, Root) => true
    case _            => false
  }

  // Pages forms a prism with Page
  @nowarn
  val PageActionP: Prism[Action, EngagePages] = Prism[Action, EngagePages] { case SelectRoot =>
    Root.some
  } { case Root =>
    SelectRoot
  }

  /**
   * Extensions methods for RouterCtl
   */
  implicit class RouterCtlOps(val r: RouterCtl[EngagePages]) extends AnyVal {

    /**
     * Some pages are linked to actions. This methods lets you set the url and dispatch an action at
     * the same time
     */
    def setUrlAndDispatchCB(b: EngagePages): Callback =
      r.set(b) *> EngageCircuit.dispatchCB(PageActionP.reverseGet(b))

    /**
     * Some actions are linked to a page. This methods lets you dispatch and action and set the url
     */
    def dispatchAndSetUrlCB(b: Action): Callback =
      PageActionP.getOption(b).map(r.set).getOrEmpty *>
        EngageCircuit.dispatchCB(b)

  }
}
