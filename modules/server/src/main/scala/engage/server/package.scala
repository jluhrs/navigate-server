package engage

import scala.concurrent.duration._

package object server {
  val ConnectionTimeout: FiniteDuration = FiniteDuration.apply(1, SECONDS)
}
