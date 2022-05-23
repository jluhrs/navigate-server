package engage.server

import lucuma.core.util.Enumerated

sealed trait ApplyCommandResult extends Product with Serializable

object ApplyCommandResult {
  case object Paused    extends ApplyCommandResult
  case object Completed extends ApplyCommandResult

  /** @group Typeclass Instances */
  implicit val ApplyCommandResultEnumerated: Enumerated[ApplyCommandResult] =
    Enumerated.of(Paused, Completed)
}
