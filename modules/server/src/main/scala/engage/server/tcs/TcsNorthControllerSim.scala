// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import cats.Applicative

class TcsNorthControllerSim[F[_]: Applicative]
    extends TcsBaseControllerSim[F]
    with TcsNorthController[F] {}
