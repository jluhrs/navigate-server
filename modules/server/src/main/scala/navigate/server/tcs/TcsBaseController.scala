// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import lucuma.core.enums.Instrument
import lucuma.core.math.Angle
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.TimeSpan
import navigate.model.enums.CentralBafflePosition
import navigate.model.enums.DeployableBafflePosition
import navigate.model.enums.DomeMode
import navigate.model.enums.ShutterMode
import navigate.server.ApplyCommandResult

trait TcsBaseController[F[_]] {
  import TcsBaseController.*
  def mcsPark: F[ApplyCommandResult]
  def mcsFollow(enable:           Boolean): F[ApplyCommandResult]
  def rotStop(useBrakes:          Boolean): F[ApplyCommandResult]
  def rotPark: F[ApplyCommandResult]
  def rotFollow(enable:           Boolean): F[ApplyCommandResult]
  def rotMove(angle:              Angle): F[ApplyCommandResult]
  def ecsCarouselMode(
    domeMode:      DomeMode,
    shutterMode:   ShutterMode,
    slitHeight:    Double,
    domeEnable:    Boolean,
    shutterEnable: Boolean
  ): F[ApplyCommandResult]
  def ecsVentGatesMove(gateEast:  Double, westGate:       Double): F[ApplyCommandResult]
  def tcsConfig(config:           TcsConfig): F[ApplyCommandResult]
  def slew(slewOptions:           SlewOptions, tcsConfig: TcsConfig): F[ApplyCommandResult]
  def swapTarget(target:          Target): F[ApplyCommandResult]
  def instrumentSpecifics(config: InstrumentSpecifics): F[ApplyCommandResult]
  def oiwfsTarget(target:         Target): F[ApplyCommandResult]
  def rotIaa(angle:               Angle): F[ApplyCommandResult]
  def rotTrackingConfig(cfg:      RotatorTrackConfig): F[ApplyCommandResult]
  def oiwfsProbeTracking(config:  TrackingConfig): F[ApplyCommandResult]
  def oiwfsPark: F[ApplyCommandResult]
  def oiwfsFollow(enable:         Boolean): F[ApplyCommandResult]
  def enableGuide(config:         TelescopeGuideConfig): F[ApplyCommandResult]
  def disableGuide: F[ApplyCommandResult]
  def oiwfsObserve(exposureTime:  TimeSpan): F[ApplyCommandResult]
  def oiwfsStopObserve: F[ApplyCommandResult]
  def baffles(
    central:    CentralBafflePosition,
    deployable: DeployableBafflePosition
  ): F[ApplyCommandResult]
  def scsFollow(enable:           Boolean): F[ApplyCommandResult]

  def getGuideState: F[GuideState]
  def getGuideQuality: F[GuidersQualityValues]
  def getTelescopeState: F[TelescopeState]
}

object TcsBaseController {

  case class TcsConfig(
    sourceATarget:       Target,
    instrumentSpecifics: InstrumentSpecifics,
    oiwfs:               Option[GuiderConfig],
    rotatorTrackConfig:  RotatorTrackConfig,
    instrument:          Instrument
  )

  val SystemDefault: String  = "FK5"
  val EquinoxDefault: String = "J2000"
  val FixedSystem: String    = "Fixed"

}
