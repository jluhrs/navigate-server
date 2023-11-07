// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model.boopickle

import boopickle.CompositePickler
import boopickle.Default.UUIDPickler
import boopickle.Default.compositePickler
import boopickle.Default.doublePickler
import boopickle.Default.generatePickler
import boopickle.Default.intPickler
import boopickle.Default.longPickler
import boopickle.Default.optionPickler
import boopickle.Default.stringPickler
import boopickle.Default.transformPickler
import boopickle.Pickler
import cats._
import cats.syntax.all._
import eu.timepit.refined.api.RefType
import eu.timepit.refined.types.numeric.PosLong
import lucuma.core.util.Enumerated
import navigate.model.NavigateEvent._
import navigate.model._
import navigate.model.enums.ServerLogLevel
import navigate.model.security.UserDetails
import squants.time.Time
import squants.time.TimeConversions._

import java.time._

/**
 * Contains boopickle given picklers of model objects Boopickle can auto derive encoders but it is
 * preferred to make them explicitly
 */
trait ModelBooPicklers extends BooPicklerSyntax {
  given Pickler[Year]      = transformPickler(Year.of)(_.getValue)
  given Pickler[LocalDate] =
    transformPickler(LocalDate.ofEpochDay)(_.toEpochDay)

  given Pickler[PosLong] =
    transformPickler[PosLong, Long]((l: Long) =>
      RefType
        .applyRef[PosLong](l)
        .getOrElse(throw new RuntimeException(s"Failed to decode value"))
    )(_.value)

  def valuesMap[F[_]: Traverse, A, B](c: F[A], f: A => B): Map[B, A] =
    c.fproduct(f).map(_.swap).toList.toMap

  def sourceIndex[A: Enumerated]: Map[Int, A] =
    Enumerated[A].all.zipWithIndex.map(_.swap).toMap

  def valuesMapPickler[A: Enumerated, B: Monoid: Pickler](
    valuesMap: Map[B, A]
  ): Pickler[A] =
    transformPickler((t: B) =>
      valuesMap
        .getOrElse(t, throw new RuntimeException(s"Failed to decode value"))
    )(t => valuesMap.find { case (_, v) => v === t }.foldMap(_._1))

  def enumeratedPickler[A: Enumerated]: Pickler[A] =
    valuesMapPickler[A, Int](sourceIndex[A])

  given Pickler[Time] =
    transformPickler((t: Double) => t.milliseconds)(_.toMilliseconds)

  given Pickler[UserDetails] = generatePickler[UserDetails]

  given Pickler[Instant] =
    transformPickler((t: Long) => Instant.ofEpochMilli(t))(_.toEpochMilli)

  given Pickler[ClientId] = generatePickler[ClientId]

  given Pickler[ServerLogLevel] = enumeratedPickler[ServerLogLevel]

  given Pickler[ConnectionOpenEvent] =
    generatePickler[ConnectionOpenEvent]
  given Pickler[ServerLogMessage]    =
    generatePickler[ServerLogMessage]

  // Composite pickler for the navigate event hierarchy
  given CompositePickler[NavigateEvent] = compositePickler[NavigateEvent]
    .addConcreteType[ConnectionOpenEvent]
    .addConcreteType[ServerLogMessage]
    .addConcreteType[NullEvent.type]

}
