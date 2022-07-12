// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs
import cats.Monad
import cats.effect.Ref
import cats.kernel.Eq
import cats.syntax.all._
import engage.epics.{ EpicsSystem, RemoteChannel }
import engage.epics.VerifiedEpics.VerifiedEpics
import engage.server.{ ApplyCommandResult, TestEpicsCommands }
import engage.server.TestEpicsCommands.TestEpicsCommand0
import engage.server.tcs.TcsEpicsSystem.BaseCommand
import engage.server.tcs.TestTcsEpicsSystem.{ State, TestTcsEvent }
import engage.server.tcs.TestTcsEpicsSystem.TestTcsEvent.MountParkCmd
import monocle.Lens
import monocle.syntax.all._

import java.util.concurrent.TimeUnit.SECONDS
import scala.concurrent.duration.FiniteDuration

case class TestTcsEpicsSystem[F[_]: Monad](st: Ref[F, State], out: Ref[F, List[TestTcsEvent]])
    extends TcsEpicsSystem[F] {
  override def startCommand(timeout: FiniteDuration): TcsEpicsSystem.TcsCommands[F] =
    new TestTcsEpicsSystem.TcsCommandsImpl[F](
      TestEpicsCommands(List.empty),
      st,
      out
    )
}

object TestTcsEpicsSystem {
  case class State(
    mountParked: Boolean
  )

  val DefaultTimeout: FiniteDuration = FiniteDuration(1, SECONDS)

  case class TcsCommandsImpl[F[_]: Monad](
    base: TestEpicsCommands[State, TestTcsEvent],
    st:   Ref[F, State],
    out:  Ref[F, List[TestTcsEvent]]
  ) extends TcsEpicsSystem.TcsCommands[F] {
    override def post: VerifiedEpics[F, ApplyCommandResult]                =
      new VerifiedEpics[F, ApplyCommandResult] {
        override val systems: Map[EpicsSystem.TelltaleChannel, Set[RemoteChannel]] = Map.empty
        override val run: F[ApplyCommandResult]                                    = st
          .modify { x =>
            val o = base.post(x)
            (o._1, base.post(x))
          }
          .flatMap { case (_, l) => out.modify(x => (x ++ l, ApplyCommandResult.Completed)) }
      }
    override val mcsParkCmd: BaseCommand[F, TcsEpicsSystem.TcsCommands[F]] =
      new TestEpicsCommand0[F, TcsCommandsImpl[F], State, TestTcsEvent](
        Lens[TcsCommandsImpl[F], TestEpicsCommands[State, TestTcsEvent]](_.base)(a =>
          b => b.copy(base = a)
        ),
        this
      ) {
        override protected def event(st: State): TestTcsEvent = TestTcsEvent.MountParkCmd

        override protected def cmd(st: State): State = st.focus(_.mountParked).replace(true)
      }
  }

  sealed trait TestTcsEvent extends Product with Serializable
  object TestTcsEvent {
    case object MountParkCmd extends TestTcsEvent
  }

  implicit val eqTestTcsEvPent: Eq[TestTcsEvent] = Eq.instance {
    case (MountParkCmd, MountParkCmd) => true
    case _                            => false
  }

  val defaultState: State = State(
    mountParked = false
  )

  def build[F[_]: Monad: Ref.Make](s0: State): F[TestTcsEpicsSystem[F]] = for {
    st  <- Ref.of(s0)
    out <- Ref.of(List.empty[TestTcsEvent])
  } yield TestTcsEpicsSystem(st, out)

}
