// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import engage.server.ApplyCommandResult
import squants.Angle

trait TcsBaseController[F[_]] {
  def mcsPark: F[ApplyCommandResult]
  def mcsFollow(enable:  Boolean): F[ApplyCommandResult]
  def rotStop(useBrakes: Boolean): F[ApplyCommandResult]
  def rotPark: F[ApplyCommandResult]
  def rotFollow(enable:  Boolean): F[ApplyCommandResult]
  def rotMove(angle:     Angle): F[ApplyCommandResult]
}
