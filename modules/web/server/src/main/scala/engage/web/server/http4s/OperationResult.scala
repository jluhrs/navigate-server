// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server.http4s

import cats.Eq
import lucuma.core.util.Enumerated

sealed abstract class OperationResult(val tag: String) extends Product with Serializable

object OperationResult {

  case object Success extends OperationResult("Success")
  case object Failure extends OperationResult("Failure")

  given Eq[OperationResult] = Eq.instance {
    case (Success, Success) => true
    case (Failure, Failure) => true
    case _                  => false
  }

  given Enumerated[OperationResult] =
    Enumerated.from[OperationResult](Success, Failure).withTag(_.tag)

}
