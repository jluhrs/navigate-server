// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.Applicative
import cats.effect.Ref
import cats.syntax.all.*
import lucuma.core.enums.MountGuideOption
import lucuma.core.math.Angle
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.TimeSpan
import monocle.Focus.focus
import mouse.boolean.*
import navigate.model.enums.CentralBafflePosition
import navigate.model.enums.DeployableBafflePosition
import navigate.model.enums.DomeMode
import navigate.model.enums.ShutterMode
import navigate.server.ApplyCommandResult
import navigate.server.tcs.FollowStatus.*
import navigate.server.tcs.ParkStatus.*

class TcsBaseControllerSim[F[_]: Applicative](
  guideRef:          Ref[F, GuideState],
  guidersQualityRef: Ref[F, GuidersQualityValues],
  telStateRef:       Ref[F, TelescopeState]
) extends TcsBaseController[F] {
  override def mcsPark: F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.mount).replace(MechSystemState(Parked, NotFollowing))
    )
    .as(ApplyCommandResult.Completed)

  override def mcsFollow(enable: Boolean): F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.mount).replace(MechSystemState(NotParked, enable.fold(Following, NotFollowing)))
    )
    .as(ApplyCommandResult.Completed)

  override def rotStop(useBrakes: Boolean): F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.crcs.following).replace(NotFollowing)
    )
    .as(ApplyCommandResult.Completed)

  override def rotPark: F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.crcs).replace(MechSystemState(Parked, NotFollowing))
    )
    .as(ApplyCommandResult.Completed)

  override def rotFollow(enable: Boolean): F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.crcs).replace(MechSystemState(NotParked, enable.fold(Following, NotFollowing)))
    )
    .as(ApplyCommandResult.Completed)

  override def rotMove(angle: Angle): F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.crcs.parked).replace(NotParked)
    )
    .as(ApplyCommandResult.Completed)

  override def ecsCarouselMode(
    domeMode:      DomeMode,
    shutterMode:   ShutterMode,
    slitHeight:    Double,
    domeEnable:    Boolean,
    shutterEnable: Boolean
  ): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def ecsVentGatesMove(gateEast: Double, westGate: Double): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def tcsConfig(config: TcsBaseController.TcsConfig): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def slew(
    slewOptions: SlewOptions,
    tcsConfig:   TcsBaseController.TcsConfig
  ): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def instrumentSpecifics(config: InstrumentSpecifics): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def oiwfsTarget(target: Target): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def rotIaa(angle: Angle): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def oiwfsProbeTracking(config: TrackingConfig): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def oiwfsPark: F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.oiwfs).replace(MechSystemState(Parked, NotFollowing))
    )
    .as(ApplyCommandResult.Completed)

  override def oiwfsFollow(enable: Boolean): F[ApplyCommandResult] = telStateRef
    .update(
      _.focus(_.oiwfs).replace(MechSystemState(NotParked, enable.fold(Following, NotFollowing)))
    )
    .as(ApplyCommandResult.Completed)

  override def rotTrackingConfig(cfg: RotatorTrackConfig): F[ApplyCommandResult] =
    Applicative[F].pure(ApplyCommandResult.Completed)

  override def enableGuide(config: TelescopeGuideConfig): F[ApplyCommandResult] = guideRef
    .update(
      _.copy(mountOffload = config.mountGuide, m1Guide = config.m1Guide, m2Guide = config.m2Guide)
    )
    .as(ApplyCommandResult.Completed)

  override def disableGuide: F[ApplyCommandResult] = guideRef
    .update(
      _.copy(mountOffload = MountGuideOption.MountGuideOff,
             m1Guide = M1GuideConfig.M1GuideOff,
             m2Guide = M2GuideConfig.M2GuideOff
      )
    )
    .as(ApplyCommandResult.Completed)

  override def oiwfsObserve(exposureTime: TimeSpan): F[ApplyCommandResult] = guideRef
    .update(_.copy(oiIntegrating = true))
    .as(ApplyCommandResult.Completed)

  override def oiwfsStopObserve: F[ApplyCommandResult] = guideRef
    .update(_.copy(oiIntegrating = false))
    .as(ApplyCommandResult.Completed)

  override def getGuideState: F[GuideState] = guideRef.get

  override def getGuideQuality: F[GuidersQualityValues] = guidersQualityRef.get

  override def baffles(
    central:    CentralBafflePosition,
    deployable: DeployableBafflePosition
  ): F[ApplyCommandResult] = Applicative[F].pure(ApplyCommandResult.Completed)

  override def getTelescopeState: F[TelescopeState] = telStateRef.get
}
