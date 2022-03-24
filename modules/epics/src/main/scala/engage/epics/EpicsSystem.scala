// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.Parallel
import cats.effect.Async
import cats.implicits._
import mouse.all._
import org.epics.ca.ConnectionState
import fs2.Stream

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

case class EpicsSystem[F[_]: Async: Parallel](
  telltale:          CaWrapper.ConnectChannel[F],
  channelList:       List[CaWrapper.ConnectChannel[F]],
  connectionTimeout: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)
) {
  def isConnected: F[Boolean] = telltale.getConnectionState.map(_.equals(ConnectionState.CONNECTED))

  def isAllConnected: F[Boolean] = isConnected.flatMap(
    _.fold(
      channelList
        .map(_.getConnectionState.map(_.equals(ConnectionState.CONNECTED)))
        .parSequence
        .map(_.forall(x => x)),
      false.pure[F]
    )
  )

  def telltaleConnectionCheck: F[Boolean] = isConnected.flatMap(
    _.fold(
      true.pure[F],
      telltale.connect(connectionTimeout).attempt.map(_.isRight)
    )
  )

  def connectionCheck: F[Boolean] = telltaleConnectionCheck
    .flatMap(
      _.fold(
        channelList
          .map(ch =>
            ch.getConnectionState.flatMap(x =>
              (x.equals(ConnectionState.CONNECTED))
                .fold(
                  true.pure[F],
                  ch.connect(connectionTimeout).attempt.map(_.isRight)
                )
            )
          )
          .parSequence
          .map(_.forall(x => x)),
        false.pure[F]
      )
    )

}

object EpicsSystem {
  def checkLoop[F[_]: Async: Parallel](
    l:      List[EpicsSystem[F]],
    period: FiniteDuration
  ): Stream[F, Nothing] = Stream
    .awakeEvery(period)
    .flatMap { _ =>
      Stream.eval(
        l.map(_.connectionCheck).parSequence
      )
    }
    .drain
}
