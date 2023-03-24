// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model

import cats.Eq
import cats.Order
import cats.syntax.all._
import navigate.model.enums.ServerLogLevel
import navigate.model.security.UserDetails

import java.time.Instant

sealed trait NavigateEvent

object NavigateEvent {
  final case class ServerLogMessage(level: ServerLogLevel, timestamp: Instant, msg: String)
      extends NavigateEvent
  object ServerLogMessage {
    private implicit val instantOrder: Order[Instant]           =
      Order.by(_.getNano)
    implicit val serverLogMessageOrder: Order[ServerLogMessage] =
      Order.by(x => (x.level, x.timestamp, x.msg))
  }

  case object NullEvent extends NavigateEvent

  final case class ConnectionOpenEvent(
    userDetails:   Option[UserDetails],
    clientId:      ClientId,
    serverVersion: String
  ) extends NavigateEvent

  object ConnectionOpenEvent {
    implicit lazy val equal: Eq[ConnectionOpenEvent] =
      Eq.by(x => (x.userDetails, x.clientId, x.serverVersion))
  }

  final case class CommandStart(cmd: NavigateCommand) extends NavigateEvent
  object CommandStart {
    implicit val commandStartEq: Eq[CommandStart] = Eq.by(_.cmd)
  }

  final case class CommandSuccess(cmd: NavigateCommand) extends NavigateEvent
  object CommandSuccess {
    implicit val commandSuccessEq: Eq[CommandSuccess] = Eq.by(_.cmd)
  }

  final case class CommandPaused(cmd: NavigateCommand) extends NavigateEvent
  object CommandPaused {
    implicit val commandPausedEq: Eq[CommandPaused] = Eq.by(_.cmd)
  }

  final case class CommandFailure(cmd: NavigateCommand, msg: String) extends NavigateEvent
  object CommandFailure {
    implicit val commandFailureEq: Eq[CommandFailure] = Eq.by(x => (x.cmd, x.msg))
  }

  implicit val equal: Eq[NavigateEvent] =
    Eq.instance {
      case (a: ServerLogMessage, b: ServerLogMessage)       => a === b
      case (a: ConnectionOpenEvent, b: ConnectionOpenEvent) => a === b
      case (NullEvent, NullEvent)                           => true
      case (a: CommandStart, b: CommandStart)               => a === b
      case (a: CommandFailure, b: CommandFailure)           => a === b
      case (a: CommandSuccess, b: CommandSuccess)           => a === b
      case (a: CommandPaused, b: CommandPaused)             => a === b
      case _                                                => false
    }

  sealed trait ForClient extends NavigateEvent {
    def clientId: ClientId
  }

}
