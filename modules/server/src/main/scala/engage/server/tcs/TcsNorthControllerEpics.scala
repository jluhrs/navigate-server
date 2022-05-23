package engage.server.tcs

import cats.Parallel
import cats.effect.Async

import scala.concurrent.duration.FiniteDuration

class TcsNorthControllerEpics[F[_]: Async: Parallel](tcsEpics: TcsEpics[F], timeout: FiniteDuration)
  extends TcsBaseControllerEpics[F](tcsEpics, timeout) with TcsNorthController[F] {

}
