// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.model

import cats.kernel.Eq

sealed trait EngageCommand extends Product with Serializable

object EngageCommand {

  case object McsFollow   extends EngageCommand
  case object ScsFollow   extends EngageCommand
  case object CrcsFollow  extends EngageCommand
  case object Pwfs1Follow extends EngageCommand
  case object Pwfs2Follow extends EngageCommand
  case object OiwfsFollow extends EngageCommand
  case object AowfsFollow extends EngageCommand
  case object Cwfs1Follow extends EngageCommand
  case object Cwfs2Follow extends EngageCommand
  case object Cwfs3Follow extends EngageCommand
  case object Odgw1Follow extends EngageCommand
  case object Odgw2Follow extends EngageCommand
  case object Odgw3Follow extends EngageCommand
  case object Odgw4Follow extends EngageCommand
  case object McsPark     extends EngageCommand
  case object ScsPark     extends EngageCommand
  case object CrcsPark    extends EngageCommand
  case object Pwfs1Park   extends EngageCommand
  case object Pwfs2Park   extends EngageCommand
  case object OiwfsPark   extends EngageCommand
  case object AowfsPark   extends EngageCommand
  case object Cwfs1Park   extends EngageCommand
  case object Cwfs2Park   extends EngageCommand
  case object Cwfs3Park   extends EngageCommand
  case object Odgw1Park   extends EngageCommand
  case object Odgw2Park   extends EngageCommand
  case object Odgw3Park   extends EngageCommand
  case object Odgw4Park   extends EngageCommand

  implicit val engageCommandEq: Eq[EngageCommand] = Eq.fromUniversalEquals

  implicit class EngageCommandOps(self: EngageCommand) {
    def name: String = self match {
      case McsFollow   => "Mcs Follow"
      case ScsFollow   => "Scs Follow"
      case CrcsFollow  => "Crcs Follow"
      case Pwfs1Follow => "Pwfs1 Follow"
      case Pwfs2Follow => "Pwfs2 Follow"
      case OiwfsFollow => "Oiwfs Follow"
      case AowfsFollow => "Aowfs Follow"
      case Cwfs1Follow => "Cwfs1 Follow"
      case Cwfs2Follow => "Cwfs2 Follow"
      case Cwfs3Follow => "Cwfs3 Follow"
      case Odgw1Follow => "Odgw1 Follow"
      case Odgw2Follow => "Odgw2 Follow"
      case Odgw3Follow => "Odgw3 Follow"
      case Odgw4Follow => "Odgw4 Follow"
      case McsPark     => "Mcs Park"
      case ScsPark     => "Scs Park"
      case CrcsPark    => "Crcs Park"
      case Pwfs1Park   => "Pwfs1 Park"
      case Pwfs2Park   => "Pwfs2 Park"
      case OiwfsPark   => "Oiwfs Park"
      case AowfsPark   => "Aowfs Park"
      case Cwfs1Park   => "Cwfs1 Park"
      case Cwfs2Park   => "Cwfs2 Park"
      case Cwfs3Park   => "Cwfs3 Park"
      case Odgw1Park   => "Odgw1 Park"
      case Odgw2Park   => "Odgw2 Park"
      case Odgw3Park   => "Odgw3 Park"
      case Odgw4Park   => "Odgw4 Park"
    }
  }

}
