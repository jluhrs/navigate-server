package engage.server.tcs

import cats.Applicative
import engage.server.ApplyCommandResult

class TcsBaseControllerSim[F[_]: Applicative] extends TcsBaseController[F] {
  override def mcsPark: F[ApplyCommandResult] = Applicative[F].pure(ApplyCommandResult.Completed)
}
