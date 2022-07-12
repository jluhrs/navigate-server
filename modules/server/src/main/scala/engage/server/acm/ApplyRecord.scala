// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.acm

import cats.effect.Resource
import engage.epics.{ Channel, EpicsService }

case class ApplyRecord[F[_]](
  name: String,
  dir:  Channel[F, CadDirective],
  oval: Channel[F, Int],
  mess: Channel[F, String]
)
object ApplyRecord {
  private val DIR_SUFFIX = ".DIR"
  private val VAL_SUFFIX = ".VAL"
  private val MSG_SUFFIX = ".MESS"

  def build[F[_]](srv: EpicsService[F], applyName: String): Resource[F, ApplyRecord[F]] = for {
    v   <- srv.getChannel[Int](applyName + VAL_SUFFIX)
    dir <- srv.getChannel[CadDirective](applyName + DIR_SUFFIX)
    ms  <- srv.getChannel[String](applyName + MSG_SUFFIX)
  } yield ApplyRecord(applyName, dir, v, ms)

}
