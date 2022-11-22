// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.server.config

import cats.effect.Sync
import cats.syntax.all.*
import engage.model.config.*
import lucuma.core.enums.Site
import pureconfig.*
import pureconfig.error.*
import pureconfig.generic.derivation.default.*
import pureconfig.module.catseffect.syntax.*
import pureconfig.module.http4s.*

case class SiteValueUnknown(site: String)         extends FailureReason {
  def description: String = s"site '$site' invalid"
}
case class ModeValueUnknown(mode: String)         extends FailureReason {
  def description: String = s"mode '$mode' invalid"
}
case class StrategyValueUnknown(strategy: String) extends FailureReason {
  def description: String = s"strategy '$strategy' invalid"
}

given ConfigReader[Site] = ConfigReader.fromCursor[Site] { cf =>
  cf.asString.flatMap {
    case "GS" => Site.GS.asRight
    case "GN" => Site.GN.asRight
    case s    => cf.failed(SiteValueUnknown(s))
  }
}

given ConfigReader[Mode] = ConfigReader.fromCursor[Mode] { cf =>
  cf.asString.flatMap {
    case "production" => Mode.Production.asRight
    case "dev"        => Mode.Development.asRight
    case s            => cf.failed(ModeValueUnknown(s))
  }
}

given ConfigReader[ControlStrategy] =
  ConfigReader.fromCursor[ControlStrategy] { cf =>
    cf.asString.flatMap { c =>
      ControlStrategy.fromString(c) match {
        case Some(x) => x.asRight
        case _       => cf.failed(StrategyValueUnknown(c))
      }
    }
  }

def loadConfiguration[F[_]: Sync](config: ConfigObjectSource): F[EngageConfiguration] = {
  given ConfigReader[EngageConfiguration] =
    ConfigReader.derived[EngageConfiguration]
  config.loadF[F, EngageConfiguration]()
}
