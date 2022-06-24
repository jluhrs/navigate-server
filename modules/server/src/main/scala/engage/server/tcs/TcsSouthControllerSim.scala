package engage.server.tcs

import cats.Applicative

class TcsSouthControllerSim[F[_]: Applicative]
    extends TcsBaseControllerSim[F]
    with TcsSouthController[F] {}
