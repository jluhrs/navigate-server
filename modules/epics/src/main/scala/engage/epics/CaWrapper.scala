// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.effect.{ Async, Resource }
import cats.effect.std.{ Dispatcher, Queue }
import cats.implicits._
import cats.effect.implicits._
import org.epics.ca.impl.ProtocolConfiguration.PropertyNames._
import fs2.Stream
import org.epics.ca.{ Channel => CaChannel, Context, Status }
import lucuma.core.util.Enumerated

import java.net.Inet4Address
import java.util.Properties
import java.lang.{ Integer => JInteger }
import java.lang.{ Double => JDouble }
import java.lang.{ Float => JFloat }
import scala.concurrent.duration.FiniteDuration

object CaWrapper {

  final case class Builder private (
    addrList:          Option[List[Inet4Address]],
    autoAddrList:      Option[Boolean],
    connectionTimeout: Option[FiniteDuration],
    repeaterPort:      Option[Int],
    serverPort:        Option[Int],
    maxArrayBytes:     Option[Int]
  ) {
    def withAddressList(l: List[Inet4Address]): Builder         = this.copy(addrList = l.some)
    def withAutoAddrList(enabled: Boolean): Builder             = this.copy(autoAddrList = enabled.some)
    def withConnectionTimeout(timeout: FiniteDuration): Builder =
      this.copy(connectionTimeout = timeout.some)
    def withRepeaterPort(port: Int): Builder                    = this.copy(repeaterPort = port.some)
    def withServerPort(port: Int): Builder                      = this.copy(serverPort = port.some)
    def withMaxArrayBytes(limit: Int): Builder                  = this.copy(maxArrayBytes = limit.some)

    def build[F[_]: Async]: Resource[F, Context] = Resource
      .eval {
        Async[F].delay {
          val props: Properties = System.getProperties
          addrList.map(l => props.setProperty(EPICS_CA_ADDR_LIST.toString, l.mkString(" ")))
          autoAddrList.map(e => props.setProperty(EPICS_CA_AUTO_ADDR_LIST.toString, e.toString))
          connectionTimeout.map(x =>
            props.setProperty(EPICS_CA_CONN_TMO.toString, x.toSeconds.toString)
          )
          repeaterPort.map(x => props.setProperty(EPICS_CA_REPEATER_PORT.toString, x.toString))
          serverPort.map(x => props.setProperty(EPICS_CA_SERVER_PORT.toString, x.toString))
          maxArrayBytes.map(x => props.setProperty(EPICS_CA_MAX_ARRAY_BYTES.toString, x.toString))

          props
        }
      }
      .flatMap(getContext(_))
  }

  def getBuilder: Builder = Builder(none, none, none, none, none, none)

  sealed trait Convert[T, J] {
    def toJava(v:   T): J
    def fromJava(x: J): T
  }

  sealed trait ToJavaType[T] {
    type javaType
    val clazz: Class[javaType]
    val convert: Convert[T, javaType]
  }

  implicit val intToJavaType: ToJavaType[Int] = new ToJavaType[Int] {
    override type javaType = JInteger
    override val clazz: Class[javaType]          = classOf[JInteger]
    override val convert: Convert[Int, JInteger] = new Convert[Int, JInteger] {
      override def toJava(v: Int): javaType   = JInteger.valueOf(v)
      override def fromJava(x: javaType): Int = x.toInt
    }
  }

  implicit val doubleToJavaType: ToJavaType[Double] = new ToJavaType[Double] {
    override type javaType = JDouble
    override val clazz: Class[javaType]            = classOf[JDouble]
    override val convert: Convert[Double, JDouble] = new Convert[Double, JDouble] {
      override def toJava(v: Double): JDouble   = JDouble.valueOf(v)
      override def fromJava(x: JDouble): Double = x.toDouble
    }
  }

  implicit val floatToJavaType: ToJavaType[Float] = new ToJavaType[Float] {
    override type javaType = JFloat
    override val clazz: Class[javaType]          = classOf[JFloat]
    override val convert: Convert[Float, JFloat] = new Convert[Float, JFloat] {
      override def toJava(v: Float): JFloat   = JFloat.valueOf(v)
      override def fromJava(x: JFloat): Float = x.toFloat
    }
  }

  implicit val stringToJavaType: ToJavaType[String] = new ToJavaType[String] {
    override type javaType = String
    override val clazz: Class[javaType]           = classOf[String]
    override val convert: Convert[String, String] = new Convert[String, String] {
      override def toJava(v: String): String   = v
      override def fromJava(x: String): String = x
    }
  }

  def getContext[F[_]: Async](properties: Properties = System.getProperties): Resource[F, Context] =
    Resource.make {
      Async[F].delay(new Context(properties))
    } { c =>
      Async[F].delay(c.close())
    }

  def getChannel[F[_]: Async, T](ctx: Context, name: String)(implicit
    tjt:                              ToJavaType[T]
  ): F[Channel[F, T]] =
    Async[F]
      .delay(ctx.createChannel[tjt.javaType](name, tjt.clazz))
      .map(x => ChannelImpl[F, T, tjt.javaType](x)(Async[F], tjt.convert))

  def getEnumChannel[F[_]: Async, T: Enumerated](ctx: Context, name: String)(implicit
    clazz:                                            Class[T]
  ): F[Channel[F, T]] =
    Async[F]
      .delay(ctx.createChannel[JInteger](name, classOf[JInteger]))
      .map(ChannelEnumImpl[F, T](_))

  sealed trait Channel[F[_], T] {
    val connect: F[Unit]
    def connect(timeout:                 FiniteDuration): F[Unit]
    val get: F[T]
    def get(timeout:                     FiniteDuration): F[T]
    def put(v:                           T): F[Status]
    def valueStream(implicit dispatcher: Dispatcher[F]): Resource[F, Stream[F, T]]
  }

  abstract class ChannelConnectImpl[F[_]: Async, T, J](caChannel: CaChannel[J])
      extends Channel[F, T] {
    override val connect: F[Unit]                          =
      Async[F].fromCompletableFuture(Async[F].delay(caChannel.connectAsync())).void
    override def connect(timeout: FiniteDuration): F[Unit] = connect.timeout(timeout)
  }

  final case class ChannelImpl[F[_]: Async, T, J](caChannel: CaChannel[J])(implicit
    cv:                                                      Convert[T, J]
  ) extends ChannelConnectImpl[F, T, J](caChannel) {
    override val get: F[T]                          =
      Async[F].fromCompletableFuture(Async[F].delay(caChannel.getAsync())).map(x => cv.fromJava(x))
    override def get(timeout: FiniteDuration): F[T] = get.timeout(timeout)
    override def put(v: T): F[Status]               =
      Async[F].fromCompletableFuture(Async[F].delay(caChannel.putAsync(cv.toJava(v))))

    override def valueStream(implicit dispatcher: Dispatcher[F]): Resource[F, Stream[F, T]] = for {
      q <- Resource.eval(Queue.unbounded[F, T])
      _ <- Resource.make {
             Async[F].delay(
               caChannel.addValueMonitor((v: J) =>
                 dispatcher.unsafeRunAndForget(q.offer(cv.fromJava(v)))
               )
             )
           }(x => Async[F].delay(x.close()))
      s <- Resource.pure(Stream.fromQueueUnterminated(q))
    } yield s
  }

  final case class ChannelEnumImpl[F[_]: Async, T: Enumerated](caChannel: CaChannel[JInteger])(
    implicit clazz:                                                       Class[T]
  ) extends ChannelConnectImpl[F, T, JInteger](caChannel) {
    private def toEnum(i: JInteger): F[T] =
      Enumerated[T].all
        .get(i.toLong)
        .map(_.pure[F])
        .getOrElse(
          Async[F].raiseError(
            new ArrayIndexOutOfBoundsException(
              s"Index $i out of bounds for enumeration ${clazz.getCanonicalName}"
            )
          )
        )

    override val get: F[T]                          =
      Async[F].fromCompletableFuture(Async[F].delay(caChannel.getAsync())).flatMap(toEnum)
    override def get(timeout: FiniteDuration): F[T] = get.timeout(timeout)
    override def put(v: T): F[Status]               =
      Async[F].fromCompletableFuture(
        Async[F].delay(caChannel.putAsync(JInteger.valueOf(Enumerated[T].all.indexOf(v))))
      )

    override def valueStream(implicit dispatcher: Dispatcher[F]): Resource[F, Stream[F, T]] = for {
      q <- Resource.eval(Queue.unbounded[F, T])
      _ <- Resource.make {
             Async[F].delay(
               caChannel.addValueMonitor((t: JInteger) =>
                 dispatcher.unsafeRunAndForget {
                   toEnum(t).flatMap(q.offer)
                 }
               )
             )
           }(x => Async[F].delay(x.close()))
      s <- Resource.pure(Stream.fromQueueUnterminated(q))
    } yield s
  }

}
