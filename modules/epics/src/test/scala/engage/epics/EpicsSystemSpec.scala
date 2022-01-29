// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.effect.IO
import cats.implicits._
import engage.epics.CaWrapper.ConnectChannel
import munit.CatsEffectSuite
import org.epics.ca.ConnectionState

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class EpicsSystemSpec extends CatsEffectSuite {

  private val epicsServer = ResourceFixture(
    TestEpicsServer.init("test:").flatMap(_ => CaWrapper.getContext[IO]())
  )

  epicsServer.test("Connect all channels") { ctx =>
    assertIOBoolean {
      for {
        ch0 <- CaWrapper.getChannel[IO, Int](ctx, "test:intVal")
        ch1 <- CaWrapper.getChannel[IO, Double](ctx, "test:doubleVal")
        ch2 <- CaWrapper.getChannel[IO, String](ctx, "test:stringVal")
        a    = EpicsSystem[IO](ch0, List(ch1, ch2))
        _   <- a.connectionCheck
        r   <- List[ConnectChannel[IO]](ch0, ch1, ch1).map(_.getConnectionState).parSequence
      } yield r.forall(_.equals(ConnectionState.CONNECTED))
    }
  }

  epicsServer.test("Does not connect channels if it cannot connect taletell channel") { ctx =>
    assertIOBoolean {
      for {
        ch0 <- CaWrapper.getChannel[IO, Int](ctx, "test:foo")
        ch1 <- CaWrapper.getChannel[IO, Double](ctx, "test:doubleVal")
        ch2 <- CaWrapper.getChannel[IO, String](ctx, "test:stringVal")
        a    = EpicsSystem[IO](ch0, List(ch1, ch2), FiniteDuration(1, TimeUnit.SECONDS))
        _   <- a.connectionCheck
        r   <- List[ConnectChannel[IO]](ch0, ch1, ch1).map(_.getConnectionState).parSequence
      } yield !r.exists(_.equals(ConnectionState.CONNECTED))
    }
  }

}
