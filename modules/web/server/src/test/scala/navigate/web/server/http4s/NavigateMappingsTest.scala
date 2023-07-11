// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.*
import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import edu.gemini.grackle.ValueMapping
import edu.gemini.grackle.syntax.*
import fs2.Stream
import io.circe.Decoder
import io.circe.Json
import lucuma.core.math.Angle
import munit.CatsEffectSuite
import munit.Clue.generate
import navigate.model.NavigateEvent
import navigate.model.enums.DomeMode
import navigate.model.enums.ShutterMode
import navigate.server.NavigateEngine
import navigate.server.OdbProxy
import navigate.server.Systems
import navigate.server.tcs.InstrumentSpecifics
import navigate.server.tcs.FollowStatus
import navigate.server.tcs.ParkStatus
import navigate.server.tcs.SlewConfig
import navigate.server.tcs.TcsNorthControllerSim
import navigate.server.tcs.TcsSouthControllerSim

import scala.concurrent.duration.Duration

import NavigateMappings.*

class NavigateMappingsTest extends CatsEffectSuite {
  import NavigateMappingsTest.*
  import NavigateMappingsTest.given

  override val munitTimeout = Duration(1800, "s")

  def extractResult[T: Decoder](j: Json, mutation: String): Option[T] = j.hcursor
    .downField("data")
    .downField(mutation)
    .as[T]
    .toOption

  test("Process mount follow command") {
    for {
      eng <- buildServer
      mp  <- NavigateMappings[IO](eng)
      r   <- mp.compileAndRun("mutation { mountFollow(enable: true) { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "mountFollow").exists(_ === OperationOutcome.success)
    )

  }

  test("Process mount park command") {
    for {
      eng <- buildServer
      mp  <- NavigateMappings[IO](eng)
      r   <- mp.compileAndRun("mutation { mountPark { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "mountPark").exists(_ === OperationOutcome.success)
    )

  }

  test("Process slew command") {
    for {
      eng <- buildServer
      mp  <- NavigateMappings[IO](eng)
      r   <- mp.compileAndRun(
               """
                |mutation { slew (slewParams: {
                |  slewOptions: {
                |    zeroChopThrow: true
                |    zeroSourceOffset: true
                |    zeroSourceDiffTrack: true
                |    zeroMountOffset: true
                |    zeroMountDiffTrack: true
                |    shortcircuitTargetFilter: true
                |    shortcircuitMountFilter: true
                |    resetPointing: true
                |    stopGuide: true
                |    zeroGuideOffset: true
                |    zeroInstrumentOffset: true
                |    autoparkPwfs1: true
                |    autoparkPwfs2: true
                |    autoparkOiwfs: true
                |    autoparkGems: true
                |    autoparkAowfs: true
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
                |  instParams: {
                |    iaa: {
                |      degrees: 178.38
                |    }
                |    focusOffset: {
                |      micrometers: 1234
                |    }
                |    agName: "gmos"
                |    origin: {
                |      x: {
                |        micrometers: 3012
                |      }
                |      y: {
                |        micrometers: -1234
                |      }
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

  test("Process instrumentSpecifics command") {
    for {
      eng <- buildServer
      mp  <- NavigateMappings[IO](eng)
      r   <- mp.compileAndRun(
               """
                |mutation { instrumentSpecifics (instrumentSpecificsParams: {
                |  iaa: {
                |      microarcseconds: 123.432
                |    }
                |    focusOffset: {
                |      millimeters: 54.5432
                |    }
                |    agName: "Test"
                |    origin: {
                |      x: {
                |        millimeters: 12.43
                |      }
                |      y: {
                |        millimeters: 54.54
                |      }
                |    }
                |}) {
                |  result
                |} }
                |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "instrumentSpecifics").exists(
        _ === OperationOutcome.success
      )
    )
  }
}

object NavigateMappingsTest {
  def buildServer: IO[NavigateEngine[IO]] = new NavigateEngine[IO] {
    override val systems: Systems[IO] = Systems(
      OdbProxy.dummy[IO],
      new TcsSouthControllerSim[IO],
      new TcsNorthControllerSim[IO]
    )

    override def eventStream: Stream[IO, NavigateEvent] = Stream.empty

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

    def instrumentSpecifics(instrumentSpecificsParams: InstrumentSpecifics): IO[Unit] = IO.unit

  }.pure[IO]

  given Decoder[OperationOutcome] = Decoder.instance(h =>
    h.downField("result")
      .as[OperationResult]
      .map(r => OperationOutcome(r, h.downField("msg").as[String].toOption))
  )
}
