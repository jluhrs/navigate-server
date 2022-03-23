// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.model

import cats.{ Eq, Order }
import cats.syntax.all._
import engage.model.`enum`.ServerLogLevel
import engage.model.security.UserDetails

import java.time.Instant

sealed trait EngageEvent

object EngageEvent {
  final case class ServerLogMessage(level: ServerLogLevel, timestamp: Instant, msg: String)
      extends EngageEvent
  object ServerLogMessage {
    private implicit val instantOrder: Order[Instant]           =
      Order.by(_.getNano)
    implicit val serverLogMessageOrder: Order[ServerLogMessage] =
      Order.by(x => (x.level, x.timestamp, x.msg))
  }

  case object NullEvent extends EngageEvent
  implicit lazy val neEqual: Eq[NullEvent.type] = Eq.instance {
    case (NullEvent, NullEvent) => true
    case _                      => false
  }

  final case class ConnectionOpenEvent(
    userDetails:   Option[UserDetails],
    clientId:      ClientId,
    serverVersion: String
  ) extends EngageEvent

  object ConnectionOpenEvent {
    implicit lazy val equal: Eq[ConnectionOpenEvent] =
      Eq.by(x => (x.userDetails, x.clientId, x.serverVersion))
  }

  implicit val equal: Eq[EngageEvent] =
    Eq.instance {
      case (a: ServerLogMessage, b: ServerLogMessage)       => a === b
      case (a: ConnectionOpenEvent, b: ConnectionOpenEvent) => a === b
      case (_: NullEvent.type, _: NullEvent.type)           => true
      case _                                                => false
    }

  sealed trait ForClient extends EngageEvent {
    def clientId: ClientId
  }

}
