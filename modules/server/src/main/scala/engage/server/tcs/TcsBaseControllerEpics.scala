package engage.server.tcs
import cats.Parallel
import cats.effect.Async
import engage.server.{ ApplyCommandResult, ConnectionTimeout }

import scala.concurrent.duration.FiniteDuration
import engage.epics.VerifiedEpics._

/* This class implements the common TCS commands */
class TcsBaseControllerEpics[F[_]: Async: Parallel](tcsEpics: TcsEpics[F], timeout: FiniteDuration)
    extends TcsBaseController[F] {
  override def mcsPark: F[ApplyCommandResult] =
    tcsEpics.startCommand(timeout).mcsParkCmd.mark.post.verifiedRun(ConnectionTimeout)
}
