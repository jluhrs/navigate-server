// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs
import cats.effect.Ref
import cats.{ Applicative, Monad, Parallel }
import engage.epics.EpicsSystem.TelltaleChannel
import engage.epics.VerifiedEpics.VerifiedEpics
import engage.epics.{ TestChannel, VerifiedEpics }
import engage.server.acm.{ CadDirective, GeminiApplyCommand }
import engage.server.epicsdata.BinaryYesNo
import engage.server.tcs.TcsEpicsSystem.TcsChannels
import engage.server.ApplyCommandResult
import monocle.Focus

import scala.concurrent.duration.FiniteDuration

//case class TestTcsEpicsSystem[F[_]: Monad](st: Ref[F, State], out: Ref[F, List[TestTcsEvent]])
//    extends TcsEpicsSystem[F] {
//  override def startCommand(timeout: FiniteDuration): TcsEpicsSystem.TcsCommands[F] =
//    new TestTcsEpicsSystem.TcsCommandsImpl[F](
//      TestEpicsCommands(List.empty),
//      st,
//      out
//    )
//}

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
    rotMoveAngle:     TestChannel.State[Double]
  )

  val defaultState: State = State(
    telltale = TestChannel.State.default,
    telescopeParkDir = TestChannel.State.default,
    mountFollow = TestChannel.State.default,
    rotStopBrake = TestChannel.State.default,
    rotParkDir = TestChannel.State.default,
    rotFollow = TestChannel.State.default,
    rotMoveAngle = TestChannel.State.default
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
      rotMoveAngle = new TestChannel[F, State, Double](s, Focus[State](_.rotMoveAngle))
    )

  def build[F[_]: Monad: Parallel](s: Ref[F, State]): TcsEpicsSystem[F] =
    TcsEpicsSystem.buildSystem(new TestApplyCommand[F], buildChannels(s))

//  val DefaultTimeout: FiniteDuration = FiniteDuration(1, SECONDS)
//
//  case class TcsCommandsImpl[F[_]: Monad](
//    base: TestEpicsCommands[State, TestTcsEvent],
//    st:   Ref[F, State],
//    out:  Ref[F, List[TestTcsEvent]]
//  ) extends TcsEpicsSystem.TcsCommands[F] {
//    override def post: VerifiedEpics[F, ApplyCommandResult] =
//      new VerifiedEpics[F, ApplyCommandResult] {
//        override val systems: Map[EpicsSystem.TelltaleChannel, Set[RemoteChannel]] = Map.empty
//        override val run: F[ApplyCommandResult]                                    = st
//          .modify { x =>
//            val o = base.post(x)
//            (o._1, base.post(x))
//          }
//          .flatMap { case (_, l) => out.modify(x => (x ++ l, ApplyCommandResult.Completed)) }
//      }
//
//    override val mcsParkCommand: BaseCommand[F, TcsEpicsSystem.TcsCommands[F]]                    =
//      new TestEpicsCommand0[F, TcsCommandsImpl[F], State, TestTcsEvent](
//        l,
//        this
//      ) {
//        override protected def event(st: State): TestTcsEvent = TestTcsEvent.MountParkCmd
//
//        override protected def cmd(st: State): State = st.focus(_.mountParked).replace(true)
//      }
//    override val mcsFollowCommand: TcsEpicsSystem.FollowCommand[F, TcsEpicsSystem.TcsCommands[F]] =
//      new TestFollowCommand[F](this) {
//
//        override protected def event(st: State): TestTcsEvent =
//          TestTcsEvent.MountFollowCmd(st.mountFollowS)
//
//        override protected def cmd(v: Boolean)(st: State): State = st.copy(mountFollowS = v)
//      }
//    override val rotStopCommand: RotStopCommand[F, TcsEpicsSystem.TcsCommands[F]]                 =
//      new TestEpicsCommand1[F, TcsCommandsImpl[F], State, TestTcsEvent, Boolean](l, this)
//        with RotStopCommand[F, TcsEpicsSystem.TcsCommands[F]] {
//        override protected def event(st: State): TestTcsEvent =
//          TestTcsEvent.RotatorStopCmd(st.rotBrakesOn)
//
//        override protected def cmd(v: Boolean)(st: State): State = st.copy(rotBrakesOn = v)
//
//        override def setBrakes(enable: Boolean): TcsEpicsSystem.TcsCommands[F] = param1(enable)
//      }
//
//    override val rotParkCommand: BaseCommand[F, TcsEpicsSystem.TcsCommands[F]]                    =
//      new TestEpicsCommand0[F, TcsCommandsImpl[F], State, TestTcsEvent](l, this) {
//        override protected def event(st: State): TestTcsEvent = TestTcsEvent.RotatorParkCmd
//
//        override protected def cmd(st: State): State = st.copy(rotParked = true)
//      }
//    override val rotFollowCommand: TcsEpicsSystem.FollowCommand[F, TcsEpicsSystem.TcsCommands[F]] =
//      new TestFollowCommand[F](this) {
//
//        override protected def event(st: State): TestTcsEvent =
//          TestTcsEvent.RotatorFollowCmd(st.rotFollowS)
//
//        override protected def cmd(v: Boolean)(st: State): State = st.copy(rotFollowS = v)
//      }
//    override val rotMoveCommand: TcsEpicsSystem.RotMoveCommand[F, TcsEpicsSystem.TcsCommands[F]]  =
//      new TestEpicsCommand1[F, TcsCommandsImpl[F], State, TestTcsEvent, Angle](l, this)
//        with RotMoveCommand[F, TcsEpicsSystem.TcsCommands[F]] {
//        override protected def event(st: State): TestTcsEvent =
//          TestTcsEvent.RotatorMoveCmd(st.rotMoveAngle.degrees)
//
//        override protected def cmd(v: Angle)(st: State): State = st.copy(rotMoveAngle = v.toDegrees)
//
//        override def setAngle(angle: Angle): TcsEpicsSystem.TcsCommands[F] = param1(angle)
//      }
//  }
//
//  def l[F[_]: Monad]: Lens[TcsCommandsImpl[F], TestEpicsCommands[State, TestTcsEvent]] =
//    Lens[TcsCommandsImpl[F], TestEpicsCommands[State, TestTcsEvent]](_.base)(a =>
//      b => b.copy(base = a)
//    )
//
//  abstract class TestFollowCommand[F[_]: Monad](o: TcsCommandsImpl[F])
//      extends TestEpicsCommand1[F, TcsCommandsImpl[F], State, TestTcsEvent, Boolean](l, o)
//      with TcsEpicsSystem.FollowCommand[F, TcsEpicsSystem.TcsCommands[F]] {
//    override def setFollow(enable: Boolean): TcsEpicsSystem.TcsCommands[F] = param1(enable)
//  }
//
//  sealed trait TestTcsEvent extends Product with Serializable
//  object TestTcsEvent {
//    case object MountParkCmd                      extends TestTcsEvent
//    case class MountFollowCmd(enabled: Boolean)   extends TestTcsEvent
//    case class RotatorStopCmd(brakes: Boolean)    extends TestTcsEvent
//    case object RotatorParkCmd                    extends TestTcsEvent
//    case class RotatorFollowCmd(enabled: Boolean) extends TestTcsEvent
//    case class RotatorMoveCmd(angle: Angle)       extends TestTcsEvent
//  }
//
//  implicit val eqTestTcsEvPent: Eq[TestTcsEvent] = Eq.instance {
//    case (MountParkCmd, MountParkCmd)               => true
//    case (MountFollowCmd(a), MountFollowCmd(b))     => a === b
//    case (RotatorStopCmd(a), RotatorStopCmd(b))     => a === b
//    case (RotatorParkCmd, RotatorParkCmd)           => true
//    case (RotatorFollowCmd(a), RotatorFollowCmd(b)) => a === b
//    case (RotatorMoveCmd(a), RotatorMoveCmd(b))     => a.value === b.value
//    case _                                          => false
//  }
//
//  val defaultState: State = State(
//    mountParked = false,
//    mountFollowS = false,
//    rotBrakesOn = false,
//    rotParked = false,
//    rotFollowS = false,
//    rotMoveAngle = 0.0
//  )
//
//  def build[F[_]: Monad: Ref.Make](s0: State): F[TestTcsEpicsSystem[F]] = for {
//    st  <- Ref.of(s0)
//    out <- Ref.of(List.empty[TestTcsEvent])
//  } yield TestTcsEpicsSystem(st, out)

}
