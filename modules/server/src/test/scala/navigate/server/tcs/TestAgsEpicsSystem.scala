// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Applicative
import cats.Monad
import cats.Parallel
import cats.effect.Ref
import cats.effect.Temporal
import monocle.Focus
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.TestChannel

object TestAgsEpicsSystem {

  case class State(
    telltale:   TestChannel.State[String],
    inPosition: TestChannel.State[Int],
    sfParked:   TestChannel.State[Int],
    aoParked:   TestChannel.State[Int],
    p1Parked:   TestChannel.State[Int],
    p1Follow:   TestChannel.State[String],
    p2Parked:   TestChannel.State[Int],
    p2Follow:   TestChannel.State[String],
    oiParked:   TestChannel.State[Int],
    oiFollow:   TestChannel.State[String]
  )

  val defaultState: State = State(
    TestChannel.State.of(""),
    TestChannel.State.of(0),
    TestChannel.State.of(1),
    TestChannel.State.of(1),
    TestChannel.State.of(1),
    TestChannel.State.of(""),
    TestChannel.State.of(1),
    TestChannel.State.of(""),
    TestChannel.State.of(1),
    TestChannel.State.of("")
  )

  def buildChannels[F[_]: Applicative](
    s: Ref[F, State]
  ): AgsChannels[F] = new AgsChannels[F](
    telltale =
      TelltaleChannel[F]("AGS", new TestChannel[F, State, String](s, Focus[State](_.telltale))),
    inPosition = new TestChannel[F, State, Int](s, Focus[State](_.inPosition)),
    sfParked = new TestChannel[F, State, Int](s, Focus[State](_.sfParked)),
    aoParked = new TestChannel[F, State, Int](s, Focus[State](_.aoParked)),
    p1Parked = new TestChannel[F, State, Int](s, Focus[State](_.p1Parked)),
    p1Follow = new TestChannel[F, State, String](s, Focus[State](_.p1Follow)),
    p2Parked = new TestChannel[F, State, Int](s, Focus[State](_.p2Parked)),
    p2Follow = new TestChannel[F, State, String](s, Focus[State](_.p2Follow)),
    oiParked = new TestChannel[F, State, Int](s, Focus[State](_.oiParked)),
    oiFollow = new TestChannel[F, State, String](s, Focus[State](_.oiFollow))
  )

  def build[F[_]: Monad: Temporal: Parallel](
    s: Ref[F, State]
  ): AgsEpicsSystem[F] = AgsEpicsSystem.buildSystem(buildChannels(s))
}
