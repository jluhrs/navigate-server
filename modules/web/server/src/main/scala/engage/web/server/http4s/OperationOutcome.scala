// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server.http4s

import cats.Eq
import cats.syntax.eq.*
import cats.syntax.option.*
import OperationResult.*
import io.circe.Encoder

final case class OperationOutcome(
  result: OperationResult,
  msg:    Option[String]
) derives Encoder.AsObject

object OperationOutcome {

  given Eq[OperationOutcome] = Eq.by(x => (x.result, x.msg))

  val success: OperationOutcome = OperationOutcome(Success, None)

  def failure(msg: Option[String]): OperationOutcome = OperationOutcome(Failure, msg)

  def failure: OperationOutcome = failure(None)

  def failure(msg: String): OperationOutcome = failure(msg.some)

}
