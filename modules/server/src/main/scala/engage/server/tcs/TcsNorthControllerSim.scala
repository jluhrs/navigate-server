package engage.server.tcs

import cats.Applicative

class TcsNorthControllerSim[F[_]: Applicative] extends TcsBaseControllerSim[F] with TcsNorthController[F] {

}
