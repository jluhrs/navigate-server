// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.epics

import cats.Eq
import cats.effect.{ Concurrent, Resource }
import cats.effect.std.Dispatcher
import cats.implicits._
import mouse.all._
import fs2.Stream
import org.epics.ca.{ AccessRights, ConnectionState, Status }

import scala.concurrent.duration.FiniteDuration

case class Channel[F[_], T](ch: CaWrapper.Channel[F, T]) {
  import Channel._

  val connect: F[Unit]                          = ch.connect
  def connect(timeout: FiniteDuration): F[Unit] = ch.connect(timeout)
  def getName: F[String]                        = ch.getName
  def getConnectionState: F[ConnectionState]    = ch.getConnectionState
  def getAccessRights: F[AccessRights]          = ch.getAccessRights
  val get: F[T]                                 = ch.get
  def get(timeout: FiniteDuration): F[T]        = ch.get(timeout)
  def put(v: T): F[Status]                      = ch.put(v)

  def eventStream(implicit
    dispatcher: Dispatcher[F],
    concurrent: Concurrent[F]
  ): Resource[F, Stream[F, StreamEvent[T]]] = for {
    vs <- ch.valueStream
    cs <- ch.connectionStream
  } yield vs
    .map(StreamEvent.ValueChanged[T])
    .merge(cs.map(_.fold(StreamEvent.Connected, StreamEvent.Disconnected)))
}

object Channel {

  sealed trait StreamEvent[+T]

  object StreamEvent {
    case object Connected            extends StreamEvent[Nothing]
    case object Disconnected         extends StreamEvent[Nothing]
    case class ValueChanged[T](v: T) extends StreamEvent[T]

    implicit def streamEventEq[T: Eq]: Eq[StreamEvent[T]] = Eq.instance {
      case (Connected, Connected)             => true
      case (Disconnected, Disconnected)       => true
      case (ValueChanged(a), ValueChanged(b)) => a === b
      case _                                  => false
    }

  }
}
