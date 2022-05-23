// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.Parallel
import cats.effect.Async
import cats.syntax.all._
import engage.epics.EpicsSystem.TelltaleChannel
import mouse.all._
import org.epics.ca.ConnectionState

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import RemoteChannel._

case class EpicsSystem[F[_]: Async: Parallel](
  telltale:    TelltaleChannel,
  channelList: Set[RemoteChannel]
) {
  def isConnected: F[Boolean] =
    telltale.channel.getConnectionState.map(_.equals(ConnectionState.CONNECTED))

  def isAllConnected: F[Boolean] = isConnected.flatMap(
    _.fold(
      channelList
        .map(_.getConnectionState)
        .toList
        .parSequence
        .map(_.forall(_.equals(ConnectionState.CONNECTED))),
      false.pure[F]
    )
  )

  def telltaleConnectionCheck(
    connectionTimeout: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)
  ): F[Boolean] = isConnected.flatMap(
    _.fold(
      true.pure[F],
      telltale.channel.connect(connectionTimeout).attempt.map(_.isRight)
    )
  )

  def connectionCheck(
    connectionTimeout: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)
  ): F[Boolean] = telltaleConnectionCheck(connectionTimeout)
    .flatMap(
      _.fold(
        channelList
          .map(ch =>
            ch.getConnectionState.flatMap(x =>
              x.equals(ConnectionState.CONNECTED)
                .fold(
                  true.pure[F],
                  ch.connect(connectionTimeout).attempt.map(_.isRight)
                )
            )
          )
          .toList
          .parSequence
          .map(_.forall(x => x)),
        false.pure[F]
      )
    )
}

object EpicsSystem {
  case class TelltaleChannel(sysName: String, channel: RemoteChannel)

}
