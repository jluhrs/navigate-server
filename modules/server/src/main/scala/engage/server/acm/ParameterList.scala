// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.acm

import cats.{ Applicative, Parallel }
import cats.syntax.all._
import engage.epics.{ EpicsSystem, RemoteChannel }
import engage.epics.VerifiedEpics.VerifiedEpics

object ParameterList {
  type ParameterList[F[_]] = List[VerifiedEpics[F, Unit]]

  implicit class ParameterListOps[F[_]: Applicative: Parallel](l: ParameterList[F]) extends AnyRef {
    def compile: VerifiedEpics[F, Unit] = new VerifiedEpics[F, Unit] {
      override val systems: Map[EpicsSystem.TelltaleChannel, Set[RemoteChannel]] =
        l.flatMap(_.systems.toList).groupBy(_._1).view.mapValues(_.flatMap(_._2.toList).toSet).toMap
      override val run: F[Unit]                                                  = l.map(_.run).parSequence.void
    }
  }
}
