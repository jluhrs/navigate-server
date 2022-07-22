// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs
import cats.effect.Ref
import cats.{ Applicative, Monad, Parallel }
import engage.epics.EpicsSystem.TelltaleChannel
import engage.epics.VerifiedEpics.VerifiedEpics
import engage.epics.{ TestChannel, VerifiedEpics }
import engage.model.enums.{ DomeMode, ShutterMode }
import engage.server.acm.{ CadDirective, GeminiApplyCommand }
import engage.server.epicsdata.{ BinaryOnOff, BinaryYesNo }
import engage.server.tcs.TcsEpicsSystem.TcsChannels
import engage.server.ApplyCommandResult
import monocle.Focus

import scala.concurrent.duration.FiniteDuration

object TestTcsEpicsSystem {

  class TestApplyCommand[F[_]: Applicative] extends GeminiApplyCommand[F] {
    override def post(timeout: FiniteDuration): VerifiedEpics[F, F, ApplyCommandResult] =
      VerifiedEpics.pureF[F, F, ApplyCommandResult](ApplyCommandResult.Completed)
  }

  case class State(
    telltale:         TestChannel.State[String],
    telescopeParkDir: TestChannel.State[CadDirective],
    mountFollow:      TestChannel.State[Boolean],
    rotStopBrake:     TestChannel.State[BinaryYesNo],
    rotParkDir:       TestChannel.State[CadDirective],
    rotFollow:        TestChannel.State[Boolean],
    rotMoveAngle:     TestChannel.State[Double],
    ecsDomeMode:      TestChannel.State[DomeMode],
    ecsShutterMode:   TestChannel.State[ShutterMode],
    ecsSlitHeight:    TestChannel.State[Double],
    ecsDomeEnable:    TestChannel.State[BinaryOnOff],
    ecsShutterEnable: TestChannel.State[BinaryOnOff],
    ecsMoveAngle:     TestChannel.State[Double],
    ecsShutterTop:    TestChannel.State[Double],
    ecsShutterBottom: TestChannel.State[Double],
    ecsVentGateEast:  TestChannel.State[Double],
    ecsVentGateWest:  TestChannel.State[Double]
  )

  val defaultState: State = State(
    telltale = TestChannel.State.default,
    telescopeParkDir = TestChannel.State.default,
    mountFollow = TestChannel.State.default,
    rotStopBrake = TestChannel.State.default,
    rotParkDir = TestChannel.State.default,
    rotFollow = TestChannel.State.default,
    rotMoveAngle = TestChannel.State.default,
    ecsDomeMode = TestChannel.State.default,
    ecsShutterMode = TestChannel.State.default,
    ecsSlitHeight = TestChannel.State.default,
    ecsDomeEnable = TestChannel.State.default,
    ecsShutterEnable = TestChannel.State.default,
    ecsMoveAngle = TestChannel.State.default,
    ecsShutterTop = TestChannel.State.default,
    ecsShutterBottom = TestChannel.State.default,
    ecsVentGateEast = TestChannel.State.default,
    ecsVentGateWest = TestChannel.State.default
  )

  def buildChannels[F[_]: Applicative](s: Ref[F, State]): TcsChannels[F] =
    TcsChannels(
      telltale =
        TelltaleChannel[F]("dummy", new TestChannel[F, State, String](s, Focus[State](_.telltale))),
      telescopeParkDir =
        new TestChannel[F, State, CadDirective](s, Focus[State](_.telescopeParkDir)),
      mountFollow = new TestChannel[F, State, Boolean](s, Focus[State](_.mountFollow)),
      rotStopBrake = new TestChannel[F, State, BinaryYesNo](s, Focus[State](_.rotStopBrake)),
      rotParkDir = new TestChannel[F, State, CadDirective](s, Focus[State](_.rotParkDir)),
      rotFollow = new TestChannel[F, State, Boolean](s, Focus[State](_.rotFollow)),
      rotMoveAngle = new TestChannel[F, State, Double](s, Focus[State](_.rotMoveAngle)),
      ecsDomeMode = new TestChannel[F, State, DomeMode](s, Focus[State](_.ecsDomeMode)),
      ecsShutterMode = new TestChannel[F, State, ShutterMode](s, Focus[State](_.ecsShutterMode)),
      ecsSlitHeight = new TestChannel[F, State, Double](s, Focus[State](_.ecsSlitHeight)),
      ecsDomeEnable = new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.ecsDomeEnable)),
      ecsShutterEnable =
        new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.ecsShutterEnable)),
      ecsMoveAngle = new TestChannel[F, State, Double](s, Focus[State](_.ecsMoveAngle)),
      ecsShutterTop = new TestChannel[F, State, Double](s, Focus[State](_.ecsShutterTop)),
      ecsShutterBottom = new TestChannel[F, State, Double](s, Focus[State](_.ecsShutterBottom)),
      ecsVentGateEast = new TestChannel[F, State, Double](s, Focus[State](_.ecsVentGateEast)),
      ecsVentGateWest = new TestChannel[F, State, Double](s, Focus[State](_.ecsVentGateWest))
    )

  def build[F[_]: Monad: Parallel](s: Ref[F, State]): TcsEpicsSystem[F] =
    TcsEpicsSystem.buildSystem(new TestApplyCommand[F], buildChannels(s))

}
