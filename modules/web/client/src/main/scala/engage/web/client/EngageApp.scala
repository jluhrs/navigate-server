// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client

import scala.concurrent.ExecutionContext
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel

import cats.effect.Sync
import cats.effect._
import lucuma.core.enum.Site
import org.scalajs.dom.document
import org.scalajs.dom.Element
import engage.web.client.Actions.{ Initialize, WSClose }
import engage.web.client.circuit.EngageCircuit
import engage.web.client.components.EngageUI
import engage.web.client.services.EngageWebClient
import typings.loglevel.mod.{ ^ => logger }

/**
 * Engage WebApp entry point
 */
final class EngageLauncher[F[_]](implicit val F: Sync[F], L: LiftIO[F]) {
  // japgolly.scalajs.react.extra.ReusabilityOverlay.overrideGloballyInDev()

  def serverSite: F[Site] =
    L.liftIO(IO.fromFuture {
      IO {
        import ExecutionContext.Implicits.global

        // Read the site from the webserver
        EngageWebClient.site().map(Site.fromTag(_).getOrElse(Site.GS))
      }
    })

  def initializeDataModel(engageSite: Site): F[Unit] =
    F.delay {
      // Set the instruments before adding it to the dom
      EngageCircuit.dispatch(Initialize(engageSite))
    }

  def renderingNode: F[Element] =
    F.delay {
      // Find or create the node where we render
      Option(document.getElementById("root")).getOrElse {
        val elem = document.createElement("div")
        elem.id = "root"
        document.body.appendChild(elem)
        elem
      }
    }

}

/**
 * Engage WebApp entry point Exposed to the js world
 */
@JSExportTopLevel("EngageApp")
object EngageApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val launcher = new EngageLauncher[IO]
    // Render the UI using React
    for {
      engageSite <- launcher.serverSite
      _          <- launcher.initializeDataModel(engageSite)
      router     <- EngageUI.router[IO](engageSite)
      node       <- launcher.renderingNode
      _          <- IO(router().renderIntoDOM(node)).handleErrorWith(p => IO(logger.error(p.toString)))
    } yield ExitCode.Success
  }

  @JSExport
  def stop(): Unit =
    // Close the websocket
    EngageCircuit.dispatch(WSClose)

  @JSExport
  def start(): Unit =
    super.main(Array())

}
