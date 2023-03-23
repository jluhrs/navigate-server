// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server.http4s

import cats.*
import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import edu.gemini.grackle.ValueMapping
import edu.gemini.grackle.syntax.*
import engage.model.EngageEvent
import engage.model.enums.DomeMode
import engage.model.enums.ShutterMode
import engage.server.EngageEngine
import engage.server.OdbProxy
import engage.server.Systems
import engage.server.tcs.FollowStatus
import engage.server.tcs.ParkStatus
import engage.server.tcs.SlewConfig
import engage.server.tcs.TcsNorthControllerSim
import engage.server.tcs.TcsSouthControllerSim
import fs2.Stream
import io.circe.Decoder
import io.circe.Json
import munit.CatsEffectSuite
import munit.Clue.generate
import squants.Angle

import scala.concurrent.duration.Duration

import EngageMappings.*

class EngageMappingsTest extends CatsEffectSuite {
  import EngageMappingsTest.*
  import EngageMappingsTest.given

  def extractResult[T: Decoder](j: Json, mutation: String): Option[T] = j.hcursor
    .downField("data")
    .downField(mutation)
    .as[T]
    .toOption

  test("Process mount follow command") {
    for {
      eng <- buildServer
      mp  <- EngageMappings[IO](eng)
      r   <- mp.compileAndRun("mutation { mountFollow(enable: true) { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "mountFollow").exists(_ === OperationOutcome.success)
    )

  }

  test("Process mount park command") {
    for {
      eng <- buildServer
      mp  <- EngageMappings[IO](eng)
      r   <- mp.compileAndRun("mutation { mountPark { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "mountPark").exists(_ === OperationOutcome.success)
    )

  }

  test("Process slew command") {
    for {
      eng <- buildServer
      mp  <- EngageMappings[IO](eng)
      r   <- mp.compileAndRun(
               """
                |mutation { slew (slewParams: {
                |  slewOptions: {
                |    zeroChopThrow:            true
                |    zeroSourceOffset:         true
                |    zeroSourceDiffTrack:      true
                |    zeroMountOffset:          true
                |    zeroMountDiffTrack:       true
                |    shortcircuitTargetFilter: true
                |    shortcircuitMountFilter:  true
                |    resetPointing:            true
                |    stopGuide:                true
                |    zeroGuideOffset:          true
                |    zeroInstrumentOffset:     true
                |    autoparkPwfs1:            true
                |    autoparkPwfs2:            true
                |    autoparkOiwfs:            true
                |    autoparkGems:             true
                |    autoparkAowfs:            true
                |  }
                |  baseTarget: {
                |    id: "T0001"
                |    name: "Dummy"
                |    sidereal: {
                |      ra: {
                |        hms: "21:15:33"
                |      }
                |      dec: {
                |        dms: "-30:26:38"
                |      }
                |      epoch:"J2000.000"
                |    }
                |    wavelength: {
                |      nanometers: "400"
                |    }
                |  }
                |}) {
                |  result
                |} }
                |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "slew").exists(_ === OperationOutcome.success)
    )
  }

}

object EngageMappingsTest {
  def buildServer: IO[EngageEngine[IO]] = new EngageEngine[IO] {
    override val systems: Systems[IO] = Systems(
      OdbProxy.dummy[IO],
      new TcsSouthControllerSim[IO],
      new TcsNorthControllerSim[IO]
    )

    override def eventStream: Stream[IO, EngageEvent] = Stream.empty

    override def mcsPark: IO[Unit] = IO.unit

    override def mcsFollow(enable: Boolean): IO[Unit] = IO.unit

    override def rotStop(useBrakes: Boolean): IO[Unit] = IO.unit

    override def rotPark: IO[Unit] = IO.unit

    override def rotFollow(enable: Boolean): IO[Unit] = IO.unit

    override def rotMove(angle: Angle): IO[Unit] = IO.unit

    override def ecsCarouselMode(
      domeMode:      DomeMode,
      shutterMode:   ShutterMode,
      slitHeight:    Double,
      domeEnable:    Boolean,
      shutterEnable: Boolean
    ): IO[Unit] = IO.unit

    override def ecsVentGatesMove(gateEast: Double, westGate: Double): IO[Unit] = IO.unit

    override def slew(slewConfig: SlewConfig): IO[Unit] = IO.unit

  }.pure[IO]

  given Decoder[OperationOutcome] = Decoder.instance(h =>
    h.downField("result")
      .as[OperationResult]
      .map(r => OperationOutcome(r, h.downField("msg").as[String].toOption))
  )
}
