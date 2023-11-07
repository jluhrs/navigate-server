// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.*
import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.classic.spi.LoggerContextVO
import ch.qos.logback.classic.spi.StackTraceElementProxy
import fs2.Stream
import fs2.concurrent.Topic
import io.circe.Decoder
import io.circe.Decoder.Result
import io.circe.Json
import lucuma.core.math.Angle
import lucuma.core.util.Timestamp
import munit.CatsEffectSuite
import navigate.model.NavigateEvent
import navigate.model.enums.DomeMode
import navigate.model.enums.ShutterMode
import navigate.server.NavigateEngine
import navigate.server.OdbProxy
import navigate.server.Systems
import navigate.server.tcs.InstrumentSpecifics
import navigate.server.tcs.RotatorTrackConfig
import navigate.server.tcs.SlewConfig
import navigate.server.tcs.Target
import navigate.server.tcs.TcsNorthControllerSim
import navigate.server.tcs.TcsSouthControllerSim
import navigate.server.tcs.TrackingConfig
import org.slf4j.Marker
import org.slf4j.event.KeyValuePair
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.given

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
      log <- Topic[IO, ILoggingEvent]
      mp  <- NavigateMappings[IO](eng, log)
      r   <- mp.compileAndRun("mutation { mountFollow(enable: true) { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "mountFollow").exists(_ === OperationOutcome.success)
    )

  }

  test("Process mount park command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      mp  <- NavigateMappings[IO](eng, log)
      r   <- mp.compileAndRun("mutation { mountPark { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "mountPark").exists(_ === OperationOutcome.success)
    )

  }

  test("Process slew command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      mp  <- NavigateMappings[IO](eng, log)
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
                |  oiwfs: {
                |    target: {
                |      id: "T0002"
                |      name: "OiwfsDummy"
                |      sidereal: {
                |        ra: {
                |          hms: "10:11:12"
                |        }
                |        dec: {
                |          dms: "-30:31:32"
                |        }
                |        epoch:"J2000.000"
                |      }
                |      wavelength: {
                |        nanometers: "600"
                |      }
                |    }
                |    tracking: {
                |      nodAchopA: true
                |      nodAchopB: false
                |      nodBchopA: false
                |      nodBchopB: true
                |    }
                |  }
                |  rotator: {
                |    ipa: {
                |      microarcseconds: 89.76
                |    }
                |    mode: TRACKING
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
      log <- Topic[IO, ILoggingEvent]
      mp  <- NavigateMappings[IO](eng, log)
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

  test("Process oiwfsTarget command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      mp  <- NavigateMappings[IO](eng, log)
      r   <- mp.compileAndRun(
               """
                |mutation { oiwfsTarget (target: {
                |  id: "T0001"
                |  name: "Dummy"
                |  sidereal: {
                |    ra: {
                |      hms: "21:15:33"
                |    }
                |    dec: {
                |      dms: "-30:26:38"
                |    }
                |    epoch:"J2000.000"
                |  }
                |  wavelength: {
                |    nanometers: "400"
                |  }
                |}) {
                |  result
                |} }
                |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "oiwfsTarget").exists(_ === OperationOutcome.success)
    )
  }

  test("Process oiwfsProbeTracking command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      mp  <- NavigateMappings[IO](eng, log)
      r   <- mp.compileAndRun(
               """
          |mutation { oiwfsProbeTracking (config: {
          |  nodAchopA: true
          |  nodAchopB: false
          |  nodBchopA: false
          |  nodBchopB: true
          |}) {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "oiwfsProbeTracking").exists(
        _ === OperationOutcome.success
      )
    )
  }

  test("Process oiwfs follow command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      mp  <- NavigateMappings[IO](eng, log)
      r   <- mp.compileAndRun("mutation { oiwfsFollow(enable: true) { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "oiwfsFollow").exists(_ === OperationOutcome.success)
    )
  }

  test("Process oiwfs park command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      mp  <- NavigateMappings[IO](eng, log)
      r   <- mp.compileAndRun("mutation { oiwfsPark { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "oiwfsPark").exists(_ === OperationOutcome.success)
    )
  }

  test("Process rotator follow command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      mp  <- NavigateMappings[IO](eng, log)
      r   <- mp.compileAndRun("mutation { rotatorFollow(enable: true) { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "rotatorFollow").exists(_ === OperationOutcome.success)
    )
  }

  test("Process rotator park command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      mp  <- NavigateMappings[IO](eng, log)
      r   <- mp.compileAndRun("mutation { rotatorPark { result } }")
    } yield assert(
      extractResult[OperationOutcome](r, "rotatorPark").exists(_ === OperationOutcome.success)
    )
  }

  test("Process rotator tracking configuration command") {
    for {
      eng <- buildServer
      log <- Topic[IO, ILoggingEvent]
      mp  <- NavigateMappings[IO](eng, log)
      r   <- mp.compileAndRun(
               """
          |mutation { rotatorConfig( config: {
          |    ipa: {
          |      microarcseconds: 89.76
          |    }
          |    mode: TRACKING
          |  }
          |) {
          |  result
          |} }
          |""".stripMargin
             )
    } yield assert(
      extractResult[OperationOutcome](r, "rotatorConfig").exists(_ === OperationOutcome.success)
    )
  }

  test("Provide logs subscription") {
    val logger: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("navigate")
    val debugMsg: String   = "debug message"
    val infoMsg: String    = "info message"
    val warningMsg: String = "warning message"
    val errorMsg: String   = "error message"

    def putLogs(topic: Topic[IO, ILoggingEvent]): Stream[IO, Option[Result[SimpleLoggingEvent]]] =
      Stream
        .emits(
          List(
            topic.publish1(SimpleLoggingEvent(Timestamp.Min, Level.DEBUG, "", debugMsg)).as(none),
            topic.publish1(SimpleLoggingEvent(Timestamp.Min, Level.INFO, "", infoMsg)).as(none),
            topic.publish1(SimpleLoggingEvent(Timestamp.Min, Level.WARN, "", warningMsg)).as(none),
            topic.publish1(SimpleLoggingEvent(Timestamp.Min, Level.ERROR, "", errorMsg)).as(none)
          )
        )
        .evalMap(a => a)

    def s(dispatcher: Dispatcher[IO]): Stream[IO, Result[SimpleLoggingEvent]] = for {
      eng  <- Stream.eval(buildServer)
      log  <- Stream.eval(Topic[IO, ILoggingEvent])
      mp   <- Stream.eval(NavigateMappings[IO](eng, log))
      logs <-
        putLogs(log)
          .merge(
            mp.compileAndRunSubscription(
              """
          | subscription {
          |   logMessage {
          |     timestamp
          |     level
          |     thread
          |     message
          |   }
          | }
          |""".stripMargin
            ).map(_.hcursor.downField("data").downField("logMessage").as[SimpleLoggingEvent].some)
          )
          .flattenOption
    } yield logs

    Dispatcher.sequential[IO].use { d =>
      s(d).take(3).compile.toList.timeout(Duration.fromNanos(5e9)).map { l =>
        val g: List[SimpleLoggingEvent] = l.collect { case Right(a) => a }
        assertEquals(g.length, l.length)
        assert(g.forall(_.message =!= debugMsg))
        assert(g.exists(_.message === infoMsg))
        assert(g.exists(_.message === warningMsg))
        assert(g.exists(_.message === errorMsg))
      }
    }

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

    override def instrumentSpecifics(instrumentSpecificsParams: InstrumentSpecifics): IO[Unit] =
      IO.unit

    override def oiwfsTarget(target: Target): IO[Unit] = IO.unit

    override def oiwfsProbeTracking(config: TrackingConfig): IO[Unit] = IO.unit

    override def oiwfsPark: IO[Unit] = IO.unit

    override def oiwfsFollow(enable: Boolean): IO[Unit] = IO.unit

    override def rotTrackingConfig(cfg: RotatorTrackConfig): IO[Unit] = IO.unit
  }.pure[IO]

  given Decoder[OperationOutcome] = Decoder.instance(h =>
    h.downField("result")
      .as[OperationResult]
      .map(r => OperationOutcome(r, h.downField("msg").as[String].toOption))
  )

  case class SimpleLoggingEvent(
    timestamp: Timestamp,
    level:     Level,
    thread:    String,
    message:   String
  ) extends ILoggingEvent {

    override def getThreadName: String = thread

    override def getLevel: Level = level

    override def getMessage: String = message

    override def getArgumentArray: Array[AnyRef] = Array.empty

    override def getFormattedMessage: String = message

    override def getLoggerName: String = ""

    override def getLoggerContextVO: LoggerContextVO = new LoggerContextVO("", Map.empty.asJava, 0)

    override def getThrowableProxy: IThrowableProxy = null

    override def getCallerData: Array[StackTraceElement] = Array.empty

    override def hasCallerData: Boolean = false

    override def getMarkerList: util.List[Marker] = List.empty.asJava

    override def getMDCPropertyMap: util.Map[String, String] = Map.empty.asJava

    override def getMdc: util.Map[String, String] = Map.empty.asJava

    override def getTimeStamp: Long = timestamp.toEpochMilli

    override def getNanoseconds: Int = 0

    override def getSequenceNumber: Long = 0

    override def getKeyValuePairs: util.List[KeyValuePair] = List.empty.asJava

    override def prepareForDeferredProcessing(): Unit = ()
  }

  given Decoder[SimpleLoggingEvent] = h =>
    for {
      ts <- h.downField("timestamp").as[Timestamp]
      l  <- h.downField("level").as[String].map(Level.toLevel)
      th <- h.downField("thread").as[String]
      ms <- h.downField("message").as[String]
    } yield SimpleLoggingEvent(ts, l, th, ms)
}
