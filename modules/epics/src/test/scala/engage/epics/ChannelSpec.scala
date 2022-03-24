// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.effect.{ Concurrent, IO, SyncIO }
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.implicits._
import gov.aps.jca.cas.ServerContext
import munit.CatsEffectSuite
import org.epics.ca.Context
import Channel.StreamEvent._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class ChannelSpec extends CatsEffectSuite {

  private val epicsServer: SyncIO[FunFixture[(ServerContext, Context)]] = ResourceFixture(
    TestEpicsServer
      .init("test:")
      .flatMap(x =>
        CaWrapper.getBuilder
          .withConnectionTimeout(FiniteDuration(1, TimeUnit.SECONDS))
          .build[IO]
          .map((x, _))
      )
  )

  epicsServer.test("Read stream of channel events") { case (serverCtx, ctx) =>
    assertIOBoolean(
      (
        for {
          dsp <- Dispatcher[IO]
          ch  <- Resource.eval(CaWrapper.getChannel[IO, Int](ctx, "test:heartbeat"))
          x    = Channel(ch)
          _   <- Resource.eval(ch.connect)
          s   <- x.eventStream(dsp, Concurrent[IO])
        } yield s
      ).use {
        _.zipWithIndex
          .evalMap { case (x, i) =>
            (
              if (i == 10) IO.delay(serverCtx.destroy())
              else IO.unit
            ).as(x)
          }
          .takeThrough {
            case Channel.StreamEvent.Disconnected => false
            case _                                => true
          }
          .compile
          .toList
      }.map(checkStream)
    )

  }

  private def checkStream(l: List[Channel.StreamEvent[Int]]): Boolean = {

    val ns: List[Int] = l.collect { case Channel.StreamEvent.ValueChanged(v) => v }

    List.range(0, ns.length) === ns.map(_ - ns.head) && l.last === Channel.StreamEvent.Disconnected
  }

}
