// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.effect.IO
import cats.effect.kernel.Resource
import munit.CatsEffectSuite
import TestEnumerated._
import cats.effect.std.Dispatcher

import java.util.concurrent.TimeUnit
import scala.concurrent.TimeoutException
import scala.concurrent.duration.{ FiniteDuration, SECONDS }
import CaWrapper._

class CaWrapperSpec extends CatsEffectSuite {

  private val epicsServer = ResourceFixture(
    TestEpicsServer.init("test:").flatMap(_ => CaWrapper.getContext[IO]())
  )

  implicit val clazz: Class[TestEnumerated] = classOf[TestEnumerated]

  epicsServer.test("Timeout trying to connect to nonexistent channel") { ctx =>
    interceptIO[TimeoutException](
      for {
        ch <- CaWrapper.getChannel[IO, Int](ctx, "test:foo")
        _  <- ch.connect(FiniteDuration(1, SECONDS))
        v  <- ch.get
      } yield v
    )
  }

  epicsServer.test("Read Int channel") { ctx =>
    assertIO(
      for {
        ch <- CaWrapper.getChannel[IO, Int](ctx, "test:intVal")
        _  <- ch.connect
        v  <- ch.get
      } yield v,
      0
    )
  }

  epicsServer.test("Read Double channel") { ctx =>
    assertIO(
      for {
        ch <- CaWrapper.getChannel[IO, Double](ctx, "test:doubleVal")
        _  <- ch.connect
        v  <- ch.get
      } yield v,
      0.0
    )
  }

  epicsServer.test("Read Float channel") { ctx =>
    assertIO(
      for {
        ch <- CaWrapper.getChannel[IO, Float](ctx, "test:floatVal")
        _  <- ch.connect
        v  <- ch.get
      } yield v,
      0.0f
    )
  }

  epicsServer.test("Read String channel") { ctx =>
    assertIO(
      for {
        ch <- CaWrapper.getChannel[IO, String](ctx, "test:stringVal")
        _  <- ch.connect
        v  <- ch.get
      } yield v,
      "dummy"
    )
  }

  epicsServer.test("Read Enum channel") { ctx =>
    assertIO(
      for {
        ch <- CaWrapper.getEnumChannel[IO, TestEnumerated](ctx, "test:enumVal")
        _  <- ch.connect
        v  <- ch.get
      } yield v,
      TestEnumerated.VAL0
    )
  }

  epicsServer.test("Read stream of values") { ctx =>
    assertIO(
      (
        for {
          dsp <- Dispatcher[IO]
          ch  <- Resource.eval(CaWrapper.getChannel[IO, Int](ctx, "test:heartbeat"))
          _   <- Resource.eval(ch.connect)
          s   <- ch.valueStream(dsp)
        } yield s
      ).use(_.drop(2).take(5).compile.toList)
        .map(l => l.map(_ - l.head))
        .timeout(FiniteDuration(5, TimeUnit.SECONDS)),
      List.range(0, 5)
    )
  }

  epicsServer.test("Write to Int channel") { ctx =>
    val t: Int = 0xdeadbeef
    assertIO(
      for {
        ch <- CaWrapper.getChannel[IO, Int](ctx, "test:intVal")
        _  <- ch.connect
        _  <- ch.put(t)
        v  <- ch.get
      } yield v,
      t
    )
  }

  epicsServer.test("Write to Double channel") { ctx =>
    val t: Double = 3.141592653
    assertIO(
      for {
        ch <- CaWrapper.getChannel[IO, Double](ctx, "test:doubleVal")
        _  <- ch.connect
        _  <- ch.put(t)
        v  <- ch.get
      } yield v,
      t
    )
  }

  epicsServer.test("Write to Float channel") { ctx =>
    val t: Float = 3.141592653f
    assertIO(
      for {
        ch <- CaWrapper.getChannel[IO, Float](ctx, "test:floatVal")
        _  <- ch.connect
        _  <- ch.put(t)
        v  <- ch.get
      } yield v,
      t
    )
  }

  epicsServer.test("Write to String channel") { ctx =>
    val t: String = "deadbeef"
    assertIO(
      for {
        ch <- CaWrapper.getChannel[IO, String](ctx, "test:stringVal")
        _  <- ch.connect
        _  <- ch.put(t)
        v  <- ch.get
      } yield v,
      t
    )
  }

  epicsServer.test("Write to Enum channel") { ctx =>
    val t: TestEnumerated = TestEnumerated.VAL2
    assertIO(
      for {
        ch <- CaWrapper.getEnumChannel[IO, TestEnumerated](ctx, "test:enumVal")
        _  <- ch.connect
        _  <- ch.put(t)
        v  <- ch.get
      } yield v,
      t
    )
  }

  epicsServer.test("Error trying to read from unconnected channel") { ctx =>
    interceptIO[IllegalStateException](
      for {
        ch <- CaWrapper.getChannel[IO, Int](ctx, "test:intVal")
        v  <- ch.get
      } yield v
    )
  }

  epicsServer.test("Error trying to write to unconnected channel") { ctx =>
    interceptIO[IllegalStateException](
      for {
        ch <- CaWrapper.getChannel[IO, Int](ctx, "test:intVal")
        v  <- ch.put(1)
      } yield v
    )
  }

  epicsServer.test("Error trying to get stream from unconnected channel") { ctx =>
    interceptIO[IllegalStateException](
      (for {
        dsp <- Dispatcher[IO]
        ch  <- Resource.eval(CaWrapper.getChannel[IO, Int](ctx, "test:intVal"))
        v   <- ch.valueStream(dsp)
      } yield v).use_
    )
  }

}
