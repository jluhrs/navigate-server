// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.model

import cats.Show
import cats.kernel.Eq
import cats.syntax.all.*
import lucuma.core.enums.GuideProbe
import lucuma.core.math.Angle
import lucuma.core.math.Offset
import lucuma.core.util.TimeSpan
import navigate.model.HandsetAdjustment.given
import navigate.model.enums.DomeMode
import navigate.model.enums.ShutterMode
import navigate.model.enums.VirtualTelescope

sealed trait NavigateCommand extends Product with Serializable

object NavigateCommand {

  case class McsFollow(enable: Boolean)                                      extends NavigateCommand
  case class ScsFollow(enable: Boolean)                                      extends NavigateCommand
  case class CrcsFollow(enable: Boolean)                                     extends NavigateCommand
  case class Pwfs1Follow(enable: Boolean)                                    extends NavigateCommand
  case class Pwfs2Follow(enable: Boolean)                                    extends NavigateCommand
  case class OiwfsFollow(enable: Boolean)                                    extends NavigateCommand
  case class AowfsFollow(enable: Boolean)                                    extends NavigateCommand
  case class Cwfs1Follow(enable: Boolean)                                    extends NavigateCommand
  case class Cwfs2Follow(enable: Boolean)                                    extends NavigateCommand
  case class Cwfs3Follow(enable: Boolean)                                    extends NavigateCommand
  case class Odgw1Follow(enable: Boolean)                                    extends NavigateCommand
  case class Odgw2Follow(enable: Boolean)                                    extends NavigateCommand
  case class Odgw3Follow(enable: Boolean)                                    extends NavigateCommand
  case class Odgw4Follow(enable: Boolean)                                    extends NavigateCommand
  case object McsPark                                                        extends NavigateCommand
  case object ScsPark                                                        extends NavigateCommand
  case object CrcsPark                                                       extends NavigateCommand
  case object Pwfs1Park                                                      extends NavigateCommand
  case object Pwfs2Park                                                      extends NavigateCommand
  case object OiwfsPark                                                      extends NavigateCommand
  case object AowfsPark                                                      extends NavigateCommand
  case object Cwfs1Park                                                      extends NavigateCommand
  case object Cwfs2Park                                                      extends NavigateCommand
  case object Cwfs3Park                                                      extends NavigateCommand
  case object Odgw1Park                                                      extends NavigateCommand
  case object Odgw2Park                                                      extends NavigateCommand
  case object Odgw3Park                                                      extends NavigateCommand
  case object Odgw4Park                                                      extends NavigateCommand
  case class CrcsStop(brakes: Boolean)                                       extends NavigateCommand
  case class CrcsMove(angle: Angle)                                          extends NavigateCommand
  case class EcsCarouselMode(
    domeMode:      DomeMode,
    shutterMode:   ShutterMode,
    slitHeight:    Double,
    domeEnable:    Boolean,
    shutterEnable: Boolean
  ) extends NavigateCommand
  case class EcsVentGatesMove(
    gateEast: Double,
    gateWest: Double
  ) extends NavigateCommand
  case object TcsConfigure                                                   extends NavigateCommand
  case object Slew                                                           extends NavigateCommand
  case object SwapTarget                                                     extends NavigateCommand
  case object InstSpecifics                                                  extends NavigateCommand
  case object Pwfs1Target                                                    extends NavigateCommand
  case object Pwfs1ProbeTracking                                             extends NavigateCommand
  case object Pwfs2Target                                                    extends NavigateCommand
  case object Pwfs2ProbeTracking                                             extends NavigateCommand
  case object OiwfsTarget                                                    extends NavigateCommand
  case object OiwfsProbeTracking                                             extends NavigateCommand
  case object RotatorTrackingConfig                                          extends NavigateCommand
  case object EnableGuide                                                    extends NavigateCommand
  case object DisableGuide                                                   extends NavigateCommand
  case object Pwfs1Observe                                                   extends NavigateCommand
  case object Pwfs1StopObserve                                               extends NavigateCommand
  case object Pwfs2Observe                                                   extends NavigateCommand
  case object Pwfs2StopObserve                                               extends NavigateCommand
  case object OiwfsObserve                                                   extends NavigateCommand
  case object OiwfsStopObserve                                               extends NavigateCommand
  case object AcObserve                                                      extends NavigateCommand
  case object AcStopObserve                                                  extends NavigateCommand
  case object M1Park                                                         extends NavigateCommand
  case object M1Unpark                                                       extends NavigateCommand
  case object M1OpenLoopOff                                                  extends NavigateCommand
  case object M1OpenLoopOn                                                   extends NavigateCommand
  case object M1ZeroFigure                                                   extends NavigateCommand
  case object M1LoadAoFigure                                                 extends NavigateCommand
  case object M1LoadNonAoFigure                                              extends NavigateCommand
  case object LightPathConfig                                                extends NavigateCommand
  case class AcquisitionAdjust(offset: Offset, ipa: Option[Angle], iaa: Option[Angle])
      extends NavigateCommand
  case class WfsSky(wfs: GuideProbe, period: TimeSpan)                       extends NavigateCommand
  case class TargetAdjust(
    target:            VirtualTelescope,
    handsetAdjustment: HandsetAdjustment,
    openLoops:         Boolean
  ) extends NavigateCommand
  case class TargetOffsetAbsorb(target: VirtualTelescope)                    extends NavigateCommand
  case class TargetOffsetClear(target: VirtualTelescope, openLoops: Boolean) extends NavigateCommand
  case class OriginAdjust(handsetAdjustment: HandsetAdjustment, openLoops: Boolean)
      extends NavigateCommand
  case object OriginOffsetAbsorb                                             extends NavigateCommand
  case class OriginOffsetClear(openLoops: Boolean)                           extends NavigateCommand
  case class PointingAdjust(handsetAdjustment: HandsetAdjustment)            extends NavigateCommand
  case object PointingOffsetClearLocal                                       extends NavigateCommand
  case object PointingOffsetAbsorbGuide                                      extends NavigateCommand
  case object PointingOffsetClearGuide                                       extends NavigateCommand

  given Eq[NavigateCommand] = Eq.fromUniversalEquals

  extension (self: NavigateCommand) {
    def name: String = self.getClass.getSimpleName.takeWhile(_ =!= '$')
  }

  given Show[NavigateCommand] = Show.show { self =>
    self match {
      case McsFollow(enable)                                                             => s"${self.name}(enable = $enable)"
      case ScsFollow(enable)                                                             => s"${self.name}(enable = $enable)"
      case CrcsFollow(enable)                                                            => s"${self.name}(enable = $enable)"
      case Pwfs1Follow(enable)                                                           => s"${self.name}(enable = $enable)"
      case Pwfs2Follow(enable)                                                           => s"${self.name}(enable = $enable)"
      case OiwfsFollow(enable)                                                           => s"${self.name}(enable = $enable)"
      case AowfsFollow(enable)                                                           => s"${self.name}(enable = $enable)"
      case Cwfs1Follow(enable)                                                           => s"${self.name}(enable = $enable)"
      case Cwfs2Follow(enable)                                                           => s"${self.name}(enable = $enable)"
      case Cwfs3Follow(enable)                                                           => s"${self.name}(enable = $enable)"
      case Odgw1Follow(enable)                                                           => s"${self.name}(enable = $enable)"
      case Odgw2Follow(enable)                                                           => s"${self.name}(enable = $enable)"
      case Odgw3Follow(enable)                                                           => s"${self.name}(enable = $enable)"
      case Odgw4Follow(enable)                                                           => s"${self.name}(enable = $enable)"
      case CrcsStop(brakes)                                                              => s"${self.name}(brakes = $brakes)"
      case CrcsMove(angle)                                                               => s"${self.name}(angle = $angle)"
      case EcsCarouselMode(domeMode, shutterMode, slitHeight, domeEnable, shutterEnable) =>
        s"${self.name}(domeMode = $domeMode, shutterMode = $shutterMode, slitHeight = $slitHeight, domeEnable = $domeEnable, shutterEnable = $shutterEnable)"
      case EcsVentGatesMove(gateEast, gateWest)                                          =>
        s"${self.name}(gateEast = $gateEast, gateWest = $gateWest)"
      case AcquisitionAdjust(offset, ipa, iaa)                                           =>
        s"${self.name}(offset = $offset, ipa = $ipa, iaa = $iaa)"
      case WfsSky(wfs, period)                                                           =>
        f"${self.name}(wfs = $wfs, period = ${period.toSeconds.toDouble}%.3f)"
      case TargetAdjust(target, handsetAdjustment, openLoops)                            =>
        s"${self.name}(target = $target, handsetAdjustment = ${handsetAdjustment.show}, openLoops = $openLoops)"
      case TargetOffsetAbsorb(target)                                                    => s"${self.name}(target = $target)"
      case TargetOffsetClear(target, openLoops)                                          =>
        s"${self.name}(target = $target, openLoops = $openLoops)"
      case OriginAdjust(handsetAdjustment, openLoops)                                    =>
        s"${self.name}(handsetAdjustment = ${handsetAdjustment.show}, openLoops = $openLoops)"
      case OriginOffsetClear(openLoops)                                                  => s"${self.name}(openLoops = $openLoops)"
      case PointingAdjust(handsetAdjustment)                                             =>
        s"${self.name}(handsetAdjustment = ${handsetAdjustment.show})"
      case _                                                                             => self.name
    }
  }

}
