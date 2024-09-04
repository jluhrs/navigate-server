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

object TestAcquisitionCameraEpicsSystem {
  case class State(
    telltale: TestChannel.State[String],
    filter:   TestChannel.State[String]
  )

  val defaultState: State = State(
    TestChannel.State.of(""),
    TestChannel.State.of("")
  )

  def buildChannels[F[_]: Applicative](
    s: Ref[F, State]
  ): AcquisitionCameraChannels[F] = new AcquisitionCameraChannels[F](
    telltale =
      TelltaleChannel[F]("AC/HR", new TestChannel[F, State, String](s, Focus[State](_.telltale))),
    filter = new TestChannel[F, State, String](s, Focus[State](_.filter))
  )

  def build[F[_]: Monad: Temporal: Parallel](
    s: Ref[F, State]
  ): AcquisitionCameraEpicsSystem[F] = AcquisitionCameraEpicsSystem.buildSystem(buildChannels(s))

}
