// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server

sealed trait EngageFailure extends Exception with Product with Serializable

object EngageFailure {

  /** Something went wrong while applying a configuration. * */
  final case class Execution(errMsg: String) extends EngageFailure

  final case class Unexpected(msg: String) extends EngageFailure

  final case class Timeout(msg: String) extends EngageFailure

  final case class NullEpicsError(channel: String) extends EngageFailure

  def explain(f: EngageFailure): String = f match {
    case Execution(errMsg)       => s"Configuration action failed with error: $errMsg"
    case Unexpected(msg)         => s"Unexpected error: $msg"
    case Timeout(msg)            => s"Timeout while waiting for $msg"
    case NullEpicsError(channel) => s"Failed to read epics channel: $channel"
  }

}
