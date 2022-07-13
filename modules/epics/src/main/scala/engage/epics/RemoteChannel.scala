// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics
import cats.effect.Async
import cats.implicits._
import cats.effect.implicits._
import cats.kernel.Eq
import org.epics.ca.{ Channel => CaChannel }
import org.epics.ca.{ AccessRights, ConnectionState }

import scala.concurrent.duration.FiniteDuration

abstract class RemoteChannel {
  val caChannel: CaChannel[_]
}

object RemoteChannel {

  implicit class RemoteChannelOps[R <: RemoteChannel](ch: R) {
    def connect[F[_]: Async]: F[Unit]                          =
      Async[F].fromCompletableFuture(Async[F].delay(ch.caChannel.connectAsync())).void
    def connect[F[_]: Async](timeout: FiniteDuration): F[Unit] = connect.timeout(timeout)
    def disconnect[F[_]: Async]: F[Unit]                       = Async[F].delay(ch.caChannel.close())
    def getName[F[_]: Async]: F[String]                        = Async[F].delay(ch.caChannel.getName)
    def getConnectionState[F[_]: Async]: F[ConnectionState]    =
      Async[F].delay(ch.caChannel.getConnectionState)
    def getAccessRights[F[_]: Async]: F[AccessRights]          = Async[F].delay(ch.caChannel.getAccessRights)
  }

  implicit val remoteChannelEq: Eq[RemoteChannel] = Eq.instance { case (a, b) =>
    a.caChannel == b.caChannel
  }

}
