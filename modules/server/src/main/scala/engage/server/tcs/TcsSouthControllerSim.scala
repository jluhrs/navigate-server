// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import cats.Applicative

class TcsSouthControllerSim[F[_]: Applicative]
    extends TcsBaseControllerSim[F]
    with TcsSouthController[F] {}
