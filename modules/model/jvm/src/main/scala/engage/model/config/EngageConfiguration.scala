// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.model.config

import cats.Eq
import lucuma.core.enum.Site

/**
 * Top configuration of the engage
 * @param site
 *   Site this engage instance handles (GN/GS)
 * @param mode
 *   Execution mode
 * @param engageEngine
 *   Configuration of the engine
 * @param webServer
 *   Web side configuration
 * @param authentication
 *   Configuration to support authentication
 */
final case class EngageConfiguration(
  site:           Site,
  mode:           Mode,
  engageEngine:   EngageEngineConfiguration,
  webServer:      WebServerConfiguration,
  authentication: AuthenticationConfig
)

object EngageConfiguration {
  implicit val eqEngageConfiguration: Eq[EngageConfiguration] =
    Eq.by(x => (x.site, x.mode, x.engageEngine, x.webServer, x.authentication))
}
