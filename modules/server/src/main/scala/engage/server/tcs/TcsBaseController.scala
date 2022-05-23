package engage.server.tcs

import engage.server.ApplyCommandResult

trait TcsBaseController[F[_]] {
  def mcsPark: F[ApplyCommandResult]
}
