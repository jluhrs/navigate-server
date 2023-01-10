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
import engage.server.tcs.TcsNorthControllerSim
import engage.server.tcs.TcsSouthControllerSim
import fs2.Stream
import io.circe.Decoder
import io.circe.Json
import munit.CatsEffectSuite
import munit.Clue.generate
import squants.Angle

import EngageMappings._

class EngageMappingsTest extends CatsEffectSuite {
  import EngageMappingsTest._

  def extractResult[T: Decoder](j: Json, mutation: String): Option[T] = j.hcursor
    .downField("data")
    .downField(mutation)
    .as[T]
    .toOption

  test("Process mount follow command") {
    for {
      eng <- buildServer
      mp  <- EngageMappings[IO](eng)
      r   <- mp.compileAndRun("mutation { mountFollow(enable: true) {} }")
    } yield assert(
      extractResult[FollowStatus](r, "mountFollow").exists(_ === FollowStatus.Following)
    )

  }

  test("Process mount park command") {
    for {
      eng <- buildServer
      mp  <- EngageMappings[IO](eng)
      r   <- mp.compileAndRun("mutation { mountPark { } }")
    } yield assert(extractResult[ParkStatus](r, "mountPark").exists(_ === ParkStatus.Parked))

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
  }.pure[IO]
}
