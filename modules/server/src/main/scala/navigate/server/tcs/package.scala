// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Applicative
import cats.Monad
import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.util.NewType
import navigate.epics.Channel
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.VerifiedEpics.VerifiedEpics
import navigate.epics.VerifiedEpics.writeChannel
import navigate.server.acm.CadDirective
import navigate.server.acm.Encoder
import navigate.server.acm.writeCadParam

object TcsTop extends NewType[NonEmptyString]
type TcsTop = TcsTop.Type
object Pwfs1Top extends NewType[NonEmptyString]
type Pwfs1Top = Pwfs1Top.Type
object Pwfs2Top extends NewType[NonEmptyString]
type Pwfs2Top = Pwfs2Top.Type
object OiwfsTop extends NewType[NonEmptyString]
type OiwfsTop = OiwfsTop.Type
object AgTop extends NewType[NonEmptyString]
type AgTop = AgTop.Type

case class ParameterlessCommandChannels[F[_]: Monad](
  tt:         TelltaleChannel[F],
  dirChannel: Channel[F, CadDirective]
) {
  val mark: VerifiedEpics[F, F, Unit] =
    writeChannel[F, CadDirective](tt, dirChannel)(Applicative[F].pure(CadDirective.MARK))
}

case class Command1Channels[F[_]: Monad, A: Encoder[*, String]](
  tt:            TelltaleChannel[F],
  param1Channel: Channel[F, String]
) {
  def setParam1(v: A): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, A](tt, param1Channel)(v)
}

case class Command2Channels[F[_]: Monad, A: Encoder[*, String], B: Encoder[*, String]](
  tt:            TelltaleChannel[F],
  param1Channel: Channel[F, String],
  param2Channel: Channel[F, String]
) {
  def setParam1(v: A): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, A](tt, param1Channel)(v)

  def setParam2(v: B): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, B](tt, param2Channel)(v)
}

case class Command3Channels[F[_]: Monad, A: Encoder[*, String], B: Encoder[*, String], C: Encoder[
  *,
  String
]](
  tt:            TelltaleChannel[F],
  param1Channel: Channel[F, String],
  param2Channel: Channel[F, String],
  param3Channel: Channel[F, String]
) {
  def setParam1(v: A): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, A](tt, param1Channel)(v)

  def setParam2(v: B): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, B](tt, param2Channel)(v)

  def setParam3(v: C): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, C](tt, param3Channel)(v)
}

case class Command4Channels[F[_]: Monad, A: Encoder[*, String], B: Encoder[*, String], C: Encoder[
  *,
  String
], D: Encoder[*, String]](
  tt:            TelltaleChannel[F],
  param1Channel: Channel[F, String],
  param2Channel: Channel[F, String],
  param3Channel: Channel[F, String],
  param4Channel: Channel[F, String]
) {
  def setParam1(v: A): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, A](tt, param1Channel)(v)

  def setParam2(v: B): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, B](tt, param2Channel)(v)

  def setParam3(v: C): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, C](tt, param3Channel)(v)

  def setParam4(v: D): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, D](tt, param4Channel)(v)
}

case class Command5Channels[F[_]: Monad, A: Encoder[*, String], B: Encoder[*, String], C: Encoder[
  *,
  String
], D: Encoder[*, String], E: Encoder[*, String]](
  tt:            TelltaleChannel[F],
  param1Channel: Channel[F, String],
  param2Channel: Channel[F, String],
  param3Channel: Channel[F, String],
  param4Channel: Channel[F, String],
  param5Channel: Channel[F, String]
) {
  def setParam1(v: A): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, A](tt, param1Channel)(v)

  def setParam2(v: B): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, B](tt, param2Channel)(v)

  def setParam3(v: C): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, C](tt, param3Channel)(v)

  def setParam4(v: D): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, D](tt, param4Channel)(v)

  def setParam5(v: E): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, E](tt, param5Channel)(v)
}

case class Command6Channels[F[_]: Monad, A: Encoder[*, String], B: Encoder[*, String], C: Encoder[
  *,
  String
], D: Encoder[*, String], E: Encoder[*, String], G: Encoder[*, String]](
  tt:            TelltaleChannel[F],
  param1Channel: Channel[F, String],
  param2Channel: Channel[F, String],
  param3Channel: Channel[F, String],
  param4Channel: Channel[F, String],
  param5Channel: Channel[F, String],
  param6Channel: Channel[F, String]
) {
  def setParam1(v: A): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, A](tt, param1Channel)(v)

  def setParam2(v: B): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, B](tt, param2Channel)(v)

  def setParam3(v: C): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, C](tt, param3Channel)(v)

  def setParam4(v: D): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, D](tt, param4Channel)(v)

  def setParam5(v: E): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, E](tt, param5Channel)(v)

  def setParam6(v: G): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, G](tt, param6Channel)(v)
}

case class Command7Channels[F[_]: Monad, A: Encoder[*, String], B: Encoder[*, String], C: Encoder[
  *,
  String
], D: Encoder[*, String], E: Encoder[*, String], G: Encoder[*, String], H: Encoder[*, String]](
  tt:            TelltaleChannel[F],
  param1Channel: Channel[F, String],
  param2Channel: Channel[F, String],
  param3Channel: Channel[F, String],
  param4Channel: Channel[F, String],
  param5Channel: Channel[F, String],
  param6Channel: Channel[F, String],
  param7Channel: Channel[F, String]
) {
  def setParam1(v: A): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, A](tt, param1Channel)(v)

  def setParam2(v: B): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, B](tt, param2Channel)(v)

  def setParam3(v: C): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, C](tt, param3Channel)(v)

  def setParam4(v: D): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, D](tt, param4Channel)(v)

  def setParam5(v: E): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, E](tt, param5Channel)(v)

  def setParam6(v: G): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, G](tt, param6Channel)(v)

  def setParam7(v: H): VerifiedEpics[F, F, Unit] =
    writeCadParam[F, H](tt, param7Channel)(v)
}

trait BaseCommand[F[_], +S] {
  def mark: S
}

def readTop(tops: Map[String, String], key: NonEmptyString): NonEmptyString =
  tops
    .get(key.value)
    .flatMap(NonEmptyString.from(_).toOption)
    .getOrElse(NonEmptyString.unsafeFrom(s"${key.value}:"))
