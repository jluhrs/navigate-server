package engage.server.acm

import cats.effect.Resource
import engage.epics.{ Channel, EpicsService }

case class CarRecord[F[_]](
  name: String,
  clid: Channel[F, Int],
  oval: Channel[F, CarState],
  omss: Channel[F, String]
)

object CarRecord {
  private val CAR_VAL_SUFFIX  = ".VAL"
  private val CAR_CLID_SUFFIX = ".CLID"
  private val CAR_OMSS_SUFFIX = ".OMSS"

  def build[F[_]](srv: EpicsService[F], carName: String): Resource[F, CarRecord[F]] = for {
    v   <- srv.getChannel[CarState](carName + CAR_VAL_SUFFIX)
    cid <- srv.getChannel[Int](carName + CAR_CLID_SUFFIX)
    om  <- srv.getChannel[String](carName + CAR_OMSS_SUFFIX)
  } yield CarRecord(carName, cid, v, om)

}
