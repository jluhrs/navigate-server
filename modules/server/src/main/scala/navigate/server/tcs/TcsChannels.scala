// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import cats.effect.Resource
import navigate.epics.Channel
import navigate.epics.EpicsService
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.given
import navigate.server.acm.CadDirective
import navigate.server.epicsdata.BinaryOnOff
import navigate.server.epicsdata.BinaryYesNo
import navigate.server.epicsdata.DirSuffix

import TcsChannels.*

case class TcsChannels[F[_]](
  /**
   * List of all TcsChannels. Channel -> Defines a raw channel Other cases -> Group of channels
   */
  telltale:         TelltaleChannel[F],
  telescopeParkDir: Channel[F, CadDirective],
  mountFollow:      Channel[F, String],
  rotStopBrake:     Channel[F, String],
  rotParkDir:       Channel[F, CadDirective],
  rotFollow:        Channel[F, String],
  rotMoveAngle:     Channel[F, String],
  enclosure:        EnclosureChannels[F],
  sourceA:          TargetChannels[F],
  oiwfsTarget:      TargetChannels[F],
  wavelSourceA:     Channel[F, String],
  slew:             SlewChannels[F],
  rotator:          RotatorChannels[F],
  origin:           OriginChannels[F],
  focusOffset:      Channel[F, String],
  oiProbeTracking:  ProbeTrackingChannels[F],
  oiProbe:          ProbeChannels[F],
  m1Guide:          Channel[F, String],
  m1GuideConfig:    M1GuideConfigChannels[F],
  m2Guide:          Channel[F, String],
  m2GuideMode:      Channel[F, String],
  m2GuideConfig:    M2GuideConfigChannels[F],
  m2GuideReset:     Channel[F, CadDirective],
  mountGuide:       MountGuideChannels[F],
  oiwfs:            WfsChannels[F],
  guide:            GuideConfigStatusChannels[F]
)

object TcsChannels {

  private val sysName: String = "TCS"

  // Next case classes are the channel groups
  case class M1GuideConfigChannels[F[_]](
    weighting: Channel[F, String],
    source:    Channel[F, String],
    frames:    Channel[F, String],
    filename:  Channel[F, String]
  )

  object M1GuideConfigChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     String
    ): Resource[F, M1GuideConfigChannels[F]] = for {
      w <- service.getChannel[String](s"${top}m1GuideConfig.A")
      s <- service.getChannel[String](s"${top}m1GuideConfig.B")
      f <- service.getChannel[String](s"${top}m1GuideConfig.C")
      n <- service.getChannel[String](s"${top}m1GuideConfig.D")
    } yield M1GuideConfigChannels(w, s, f, n)
  }

  case class M2GuideConfigChannels[F[_]](
    source:     Channel[F, String],
    samplefreq: Channel[F, String],
    filter:     Channel[F, String],
    freq1:      Channel[F, String],
    freq2:      Channel[F, String],
    beam:       Channel[F, String],
    reset:      Channel[F, String]
  )

  object M2GuideConfigChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     String
    ): Resource[F, M2GuideConfigChannels[F]] = for {
      sr <- service.getChannel[String](s"${top}m2GuideConfig.A")
      sf <- service.getChannel[String](s"${top}m2GuideConfig.B")
      fl <- service.getChannel[String](s"${top}m2GuideConfig.C")
      f1 <- service.getChannel[String](s"${top}m2GuideConfig.D")
      f2 <- service.getChannel[String](s"${top}m2GuideConfig.E")
      bm <- service.getChannel[String](s"${top}m2GuideConfig.F")
      rs <- service.getChannel[String](s"${top}m2GuideConfig.G")
    } yield M2GuideConfigChannels(sr, sf, fl, f1, f2, bm, rs)
  }

  case class MountGuideChannels[F[_]](
    mode:     Channel[F, String],
    source:   Channel[F, String],
    p1weight: Channel[F, String],
    p2weight: Channel[F, String]
  )

  object MountGuideChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     String
    ): Resource[F, MountGuideChannels[F]] = for {
      mn <- service.getChannel[String](s"${top}mountGuideMode.A")
      sr <- service.getChannel[String](s"${top}mountGuideMode.B")
      p1 <- service.getChannel[String](s"${top}mountGuideMode.C")
      p2 <- service.getChannel[String](s"${top}mountGuideMode.D")
    } yield MountGuideChannels(mn, sr, p1, p2)
  }

  case class EnclosureChannels[F[_]](
    ecsDomeMode:      Channel[F, String],
    ecsShutterMode:   Channel[F, String],
    ecsSlitHeight:    Channel[F, String],
    ecsDomeEnable:    Channel[F, String],
    ecsShutterEnable: Channel[F, String],
    ecsMoveAngle:     Channel[F, String],
    ecsShutterTop:    Channel[F, String],
    ecsShutterBottom: Channel[F, String],
    ecsVentGateEast:  Channel[F, String],
    ecsVentGateWest:  Channel[F, String]
  )

  case class TargetChannels[F[_]](
    objectName:     Channel[F, String],
    coordSystem:    Channel[F, String],
    coord1:         Channel[F, String],
    coord2:         Channel[F, String],
    epoch:          Channel[F, String],
    equinox:        Channel[F, String],
    parallax:       Channel[F, String],
    properMotion1:  Channel[F, String],
    properMotion2:  Channel[F, String],
    radialVelocity: Channel[F, String],
    brightness:     Channel[F, String],
    ephemerisFile:  Channel[F, String]
  )

  case class ProbeTrackingChannels[F[_]](
    nodachopa: Channel[F, String],
    nodachopb: Channel[F, String],
    nodbchopa: Channel[F, String],
    nodbchopb: Channel[F, String]
  )

  case class SlewChannels[F[_]](
    zeroChopThrow:            Channel[F, String],
    zeroSourceOffset:         Channel[F, String],
    zeroSourceDiffTrack:      Channel[F, String],
    zeroMountOffset:          Channel[F, String],
    zeroMountDiffTrack:       Channel[F, String],
    shortcircuitTargetFilter: Channel[F, String],
    shortcircuitMountFilter:  Channel[F, String],
    resetPointing:            Channel[F, String],
    stopGuide:                Channel[F, String],
    zeroGuideOffset:          Channel[F, String],
    zeroInstrumentOffset:     Channel[F, String],
    autoparkPwfs1:            Channel[F, String],
    autoparkPwfs2:            Channel[F, String],
    autoparkOiwfs:            Channel[F, String],
    autoparkGems:             Channel[F, String],
    autoparkAowfs:            Channel[F, String]
  )

  case class RotatorChannels[F[_]](
    ipa:     Channel[F, String],
    system:  Channel[F, String],
    equinox: Channel[F, String],
    iaa:     Channel[F, String]
  )

  case class OriginChannels[F[_]](
    xa: Channel[F, String],
    ya: Channel[F, String],
    xb: Channel[F, String],
    yb: Channel[F, String],
    xc: Channel[F, String],
    yc: Channel[F, String]
  )

  case class ProbeChannels[F[_]](
    parkDir: Channel[F, CadDirective],
    follow:  Channel[F, String]
  )

  // Build functions to construct each epics channel for each
  // channels group
  def buildEnclosureChannels[F[_]](
    service: EpicsService[F],
    top:     String
  ): Resource[F, EnclosureChannels[F]] =
    for {
      edm <- service.getChannel[String](s"${top}carouselMode.A")
      esm <- service.getChannel[String](s"${top}carouselMode.B")
      esh <- service.getChannel[String](s"${top}carouselMode.C")
      ede <- service.getChannel[String](s"${top}carouselMode.D")
      ese <- service.getChannel[String](s"${top}carouselMode.E")
      ema <- service.getChannel[String](s"${top}carousel.A")
      est <- service.getChannel[String](s"${top}shutter.A")
      esb <- service.getChannel[String](s"${top}shutter.B")
      eve <- service.getChannel[String](s"${top}ventgates.A")
      evw <- service.getChannel[String](s"${top}ventgates.B")
    } yield EnclosureChannels(
      edm,
      esm,
      esh,
      ede,
      ese,
      ema,
      est,
      esb,
      eve,
      evw
    )

  def buildTargetChannels[F[_]](
    service: EpicsService[F],
    prefix:  String
  ): Resource[F, TargetChannels[F]] =
    for {
      on  <- service.getChannel[String](prefix + ".A")
      cs  <- service.getChannel[String](prefix + ".B")
      co1 <- service.getChannel[String](prefix + ".C")
      co2 <- service.getChannel[String](prefix + ".D")
      eq  <- service.getChannel[String](prefix + ".E")
      ep  <- service.getChannel[String](prefix + ".F")
      pr  <- service.getChannel[String](prefix + ".G")
      pm1 <- service.getChannel[String](prefix + ".H")
      pm2 <- service.getChannel[String](prefix + ".I")
      rv  <- service.getChannel[String](prefix + ".J")
      br  <- service.getChannel[String](prefix + ".K")
      eph <- service.getChannel[String](prefix + ".L")
    } yield TargetChannels(
      on,
      cs,
      co1,
      co2,
      ep,
      eq,
      pr,
      pm1,
      pm2,
      rv,
      br,
      eph
    )

  def buildProbeTrackingChannels[F[_]](
    service: EpicsService[F],
    top:     String,
    name:    String
  ): Resource[F, ProbeTrackingChannels[F]] = for {
    aa <- service.getChannel[String](s"${top}config${name}.A")
    ab <- service.getChannel[String](s"${top}config${name}.B")
    ba <- service.getChannel[String](s"${top}config${name}.D")
    bb <- service.getChannel[String](s"${top}config${name}.E")
  } yield ProbeTrackingChannels(aa, ab, ba, bb)

  def buildSlewChannels[F[_]](
    service: EpicsService[F],
    top:     String
  ): Resource[F, SlewChannels[F]] = for {
    zct <- service.getChannel[String](s"${top}slew.A")
    zso <- service.getChannel[String](s"${top}slew.B")
    zsd <- service.getChannel[String](s"${top}slew.C")
    zmo <- service.getChannel[String](s"${top}slew.D")
    zmd <- service.getChannel[String](s"${top}slew.E")
    fl1 <- service.getChannel[String](s"${top}slew.F")
    fl2 <- service.getChannel[String](s"${top}slew.G")
    rp  <- service.getChannel[String](s"${top}slew.H")
    sg  <- service.getChannel[String](s"${top}slew.I")
    zgo <- service.getChannel[String](s"${top}slew.J")
    zio <- service.getChannel[String](s"${top}slew.K")
    ap1 <- service.getChannel[String](s"${top}slew.L")
    ap2 <- service.getChannel[String](s"${top}slew.M")
    aoi <- service.getChannel[String](s"${top}slew.N")
    agm <- service.getChannel[String](s"${top}slew.O")
    aao <- service.getChannel[String](s"${top}slew.P")
  } yield SlewChannels(
    zct,
    zso,
    zsd,
    zmo,
    zmd,
    fl1,
    fl2,
    rp,
    sg,
    zgo,
    zio,
    ap1,
    ap2,
    aoi,
    agm,
    aao
  )

  def buildRotatorChannels[F[_]](
    service: EpicsService[F],
    top:     String
  ): Resource[F, RotatorChannels[F]] = for {
    ipa     <- service.getChannel[String](s"${top}rotator.A")
    system  <- service.getChannel[String](s"${top}rotator.B")
    equinox <- service.getChannel[String](s"${top}rotator.C")
    iaa     <- service.getChannel[String](s"${top}rotator.D")
  } yield RotatorChannels(
    ipa,
    system,
    equinox,
    iaa
  )

  def buildOriginChannels[F[_]](
    service: EpicsService[F],
    top:     String
  ): Resource[F, OriginChannels[F]] = for {
    xa <- service.getChannel[String](s"${top}poriginA.A")
    ya <- service.getChannel[String](s"${top}poriginA.B")
    xb <- service.getChannel[String](s"${top}poriginB.A")
    yb <- service.getChannel[String](s"${top}poriginB.B")
    xc <- service.getChannel[String](s"${top}poriginC.A")
    yc <- service.getChannel[String](s"${top}poriginC.B")
  } yield OriginChannels(
    xa,
    ya,
    xb,
    yb,
    xc,
    yc
  )

  def buildProbeChannels[F[_]](
    service: EpicsService[F],
    prefix:  String
  ): Resource[F, ProbeChannels[F]] = for {
    pd <- service.getChannel[CadDirective](s"${prefix}Park${DirSuffix}")
    fl <- service.getChannel[String](s"${prefix}Follow.A")
  } yield ProbeChannels(pd, fl)

  case class WfsObserveChannels[F[_]](
    numberOfExposures: Channel[F, String],
    interval:          Channel[F, String],
    options:           Channel[F, String],
    label:             Channel[F, String],
    output:            Channel[F, String],
    path:              Channel[F, String],
    fileName:          Channel[F, String]
  )

  object WfsObserveChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     String,
      wfs:     String
    ): Resource[F, WfsObserveChannels[F]] = for {
      ne <- service.getChannel[String](s"${top}${wfs}Observe.A")
      in <- service.getChannel[String](s"${top}${wfs}Observe.B")
      op <- service.getChannel[String](s"${top}${wfs}Observe.C")
      lb <- service.getChannel[String](s"${top}${wfs}Observe.D")
      ou <- service.getChannel[String](s"${top}${wfs}Observe.E")
      pa <- service.getChannel[String](s"${top}${wfs}Observe.F")
      fn <- service.getChannel[String](s"${top}${wfs}Observe.G")
    } yield WfsObserveChannels(
      ne,
      in,
      op,
      lb,
      ou,
      pa,
      fn
    )
  }

  case class WfsClosedLoopChannels[F[_]](
    global:      Channel[F, String],
    average:     Channel[F, String],
    zernikes2m2: Channel[F, String],
    mult:        Channel[F, String]
  )

  object WfsClosedLoopChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     String,
      wfs:     String
    ): Resource[F, WfsClosedLoopChannels[F]] = for {
      gl <- service.getChannel[String](s"${top}${wfs}Seq.A")
      av <- service.getChannel[String](s"${top}${wfs}Seq.B")
      z2 <- service.getChannel[String](s"${top}${wfs}Seq.C")
      m  <- service.getChannel[String](s"${top}${wfs}Seq.D")
    } yield WfsClosedLoopChannels(gl, av, z2, m)
  }

  case class WfsChannels[F[_]](
    observe:    WfsObserveChannels[F],
    stop:       Channel[F, CadDirective],
    procParams: Channel[F, String],
    dark:       Channel[F, String],
    closedLoop: WfsClosedLoopChannels[F]
  )

  object WfsChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     String,
      wfsl:    String,
      wfss:    String
    ): Resource[F, WfsChannels[F]] = for {
      ob <- WfsObserveChannels.build(service, top, wfsl)
      st <- service.getChannel[CadDirective](s"${top}${wfsl}StopObserve${DirSuffix}")
      pr <- service.getChannel[String](s"${top}${wfss}DetSigInit.A")
      dk <- service.getChannel[String](s"${top}${wfss}SeqDark.A")
      cl <- WfsClosedLoopChannels.build(service, top, wfss)
    } yield WfsChannels(ob, st, pr, dk, cl)
  }

  case class GuideConfigStatusChannels[F[_]](
    pwfs1Integrating: Channel[F, BinaryYesNo],
    pwfs2Integrating: Channel[F, BinaryYesNo],
    oiwfsIntegrating: Channel[F, BinaryYesNo],
    m2State:          Channel[F, BinaryOnOff],
    absorbTipTilt:    Channel[F, Int],
    m2ComaCorrection: Channel[F, BinaryOnOff],
    m1State:          Channel[F, BinaryOnOff],
    m1Source:         Channel[F, String],
    p1ProbeGuide:     Channel[F, Double],
    p2ProbeGuide:     Channel[F, Double],
    oiProbeGuide:     Channel[F, Double],
    p1ProbeGuided:    Channel[F, Double],
    p2ProbeGuided:    Channel[F, Double],
    oiProbeGuided:    Channel[F, Double],
    mountP1Weight:    Channel[F, Double],
    mountP2Weight:    Channel[F, Double],
    m2P1Guide:        Channel[F, String],
    m2P2Guide:        Channel[F, String],
    m2OiGuide:        Channel[F, String],
    m2AoGuide:        Channel[F, String]
  )

  object GuideConfigStatusChannels {
    def build[F[_]](
      service: EpicsService[F],
      top:     String
    ): Resource[F, GuideConfigStatusChannels[F]] = for {
      p1i   <- service.getChannel[BinaryYesNo](s"${top}drives:p1Integrating.VAL")
      p2i   <- service.getChannel[BinaryYesNo](s"${top}drives:p2Integrating.VAL")
      oii   <- service.getChannel[BinaryYesNo](s"${top}drives:oiIntegrating.VAL")
      m2    <- service.getChannel[BinaryOnOff](s"${top}om:m2GuideState.VAL")
      tt    <- service.getChannel[Int](s"${top}absorbTipTiltFlag.VAL")
      cm    <- service.getChannel[BinaryOnOff](s"${top}im:m2gm:comaCorrectFlag.VAL")
      m1    <- service.getChannel[BinaryOnOff](s"${top}im:m1GuideOn.VAL")
      m1s   <- service.getChannel[String](s"${top}m1GuideConfig.VALB")
      p1pg  <- service.getChannel[Double](s"${top}ak:wfsguide:p1weight.VAL")
      p2pg  <- service.getChannel[Double](s"${top}ak:wfsguide:p2weight.VAL")
      oipg  <- service.getChannel[Double](s"${top}ak:wfsguide:oiweight.VAL")
      p1pd  <- service.getChannel[Double](s"${top}wfsGuideMode:p1.VAL")
      p2pd  <- service.getChannel[Double](s"${top}wfsGuideMode:p2.VAL")
      oipd  <- service.getChannel[Double](s"${top}wfsGuideMode:oi.VAL")
      p1w   <- service.getChannel[Double](s"${top}mountGuideMode.VALC")
      p2w   <- service.getChannel[Double](s"${top}mountGuideMode.VALD")
      m2p1g <- service.getChannel[String](s"${top}drives:p1GuideConfig.VAL")
      m2p2g <- service.getChannel[String](s"${top}drives:p2GuideConfig.VAL")
      m2oig <- service.getChannel[String](s"${top}drives:oiGuideConfig.VAL")
      m2aog <- service.getChannel[String](s"${top}drives:aoGuideConfig.VAL")
    } yield GuideConfigStatusChannels(
      p1i,
      p2i,
      oii,
      m2,
      tt,
      cm,
      m1,
      m1s,
      p1pg,
      p2pg,
      oipg,
      p1pd,
      p2pd,
      oipd,
      p1w,
      p2w,
      m2p1g,
      m2p2g,
      m2oig,
      m2aog
    )
  }

  /**
   * Build all TcsChannels It will construct the desired raw channel or call the build function for
   * channels group
   *
   * @param service
   *   Epics service to handle channels
   * @param top
   *   Prefix string of epics channel
   * @return
   */
  def buildChannels[F[_]](service: EpicsService[F], top: String): Resource[F, TcsChannels[F]] =
    for {
      tt   <- service.getChannel[String](s"${top}sad:health.VAL").map(TelltaleChannel(sysName, _))
      tpd  <- service.getChannel[CadDirective](s"${top}telpark$DirSuffix")
      mf   <- service.getChannel[String](s"${top}mcFollow.A")
      rsb  <- service.getChannel[String](s"${top}rotStop.B")
      rpd  <- service.getChannel[CadDirective](s"${top}rotPark$DirSuffix")
      rf   <- service.getChannel[String](s"${top}crFollow.A")
      rma  <- service.getChannel[String](s"${top}rotMove.A")
      ecs  <- buildEnclosureChannels(service, top)
      sra  <- buildTargetChannels(service, s"${top}sourceA")
      oit  <- buildTargetChannels(service, s"${top}oiwfs")
      wva  <- service.getChannel[String](s"${top}wavelSourceA.A")
      slw  <- buildSlewChannels(service, top)
      rot  <- buildRotatorChannels(service, top)
      org  <- buildOriginChannels(service, top)
      foc  <- service.getChannel[String](s"${top}dtelFocus.A")
      oig  <- buildProbeTrackingChannels(service, top, "Oiwfs")
      op   <- buildProbeChannels(service, s"${top}oiwfs")
      m1g  <- service.getChannel[String](s"${top}m1GuideMode.A")
      m1gc <- M1GuideConfigChannels.build(service, top)
      m2g  <- service.getChannel[String](s"${top}m2GuideControl.A")
      m2gm <- service.getChannel[String](s"${top}m2GuideMode.A")
      m2gc <- M2GuideConfigChannels.build(service, top)
      m2gr <- service.getChannel[CadDirective](s"${top}m2GuideReset$DirSuffix")
      mng  <- MountGuideChannels.build(service, top)
      oi   <- WfsChannels.build(service, top, "oiwfs", "oi")
      gd   <- GuideConfigStatusChannels.build(service, top)
    } yield TcsChannels[F](
      tt,
      tpd,
      mf,
      rsb,
      rpd,
      rf,
      rma,
      ecs,
      sra,
      oit,
      wva,
      slw,
      rot,
      org,
      foc,
      oig,
      op,
      m1g,
      m1gc,
      m2g,
      m2gm,
      m2gc,
      m2gr,
      mng,
      oi,
      gd
    )
}
