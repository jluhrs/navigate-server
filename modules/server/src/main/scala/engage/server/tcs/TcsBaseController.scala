// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import engage.server.ApplyCommandResult

trait TcsBaseController[F[_]] {
  def mcsPark: F[ApplyCommandResult]
}
