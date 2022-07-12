// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs

import cats.{ Applicative, Monad, Parallel }
import cats.effect.std.Dispatcher
import cats.effect.{ Resource, Temporal }
import engage.epics.EpicsSystem.TelltaleChannel
import engage.epics.{ Channel, EpicsService }
import engage.epics.VerifiedEpics._
import engage.server.{ ApplyCommandResult, tcs }
import engage.server.acm.ParameterList._
import engage.server.acm.{ CadDirective, GeminiApplyCommand }

import scala.concurrent.duration.FiniteDuration

trait TcsEpicsSystem[F[_]] {
  // TcsCommands accumulates the list of channels that need to be written to set parameters.
  // Once all the parameters are defined, the user cals the post method. Only then the EPICS channels will be verified,
  // the parameters written, and the apply record triggered.
  def startCommand(timeout: FiniteDuration): tcs.TcsEpicsSystem.TcsCommands[F]
}

object TcsEpicsSystem {

  trait TcsEpics[F[_]] extends {

    def post(timeout: FiniteDuration): VerifiedEpics[F, ApplyCommandResult]

    val mountParkCmd: ParameterlessCommandChannel[F]

    /*  val m1GuideCmd: M1GuideCmd[F]

  val m2GuideCmd: M2GuideCmd[F]

  val m2GuideModeCmd: M2GuideModeCmd[F]

  val m2GuideConfigCmd: M2GuideConfigCmd[F]

  val mountGuideCmd: MountGuideCmd[F]

  val offsetACmd: OffsetCmd[F]

  val offsetBCmd: OffsetCmd[F]

  val wavelSourceA: TargetWavelengthCmd[F]

  val wavelSourceB: TargetWavelengthCmd[F]

  val m2Beam: M2Beam[F]

  val pwfs1ProbeGuideCmd: ProbeGuideCmd[F]

  val pwfs2ProbeGuideCmd: ProbeGuideCmd[F]

  val oiwfsProbeGuideCmd: ProbeGuideCmd[F]

  val pwfs1ProbeFollowCmd: ProbeFollowCmd[F]

  val pwfs2ProbeFollowCmd: ProbeFollowCmd[F]

  val oiwfsProbeFollowCmd: ProbeFollowCmd[F]

  val aoProbeFollowCmd: ProbeFollowCmd[F]

  val pwfs1Park: EpicsCommand[F]

  val pwfs2Park: EpicsCommand[F]

  val oiwfsPark: EpicsCommand[F]

  val pwfs1StopObserveCmd: EpicsCommand[F]

  val pwfs2StopObserveCmd: EpicsCommand[F]

  val oiwfsStopObserveCmd: EpicsCommand[F]

  val pwfs1ObserveCmd: WfsObserveCmd[F]

  val pwfs2ObserveCmd: WfsObserveCmd[F]

  val oiwfsObserveCmd: WfsObserveCmd[F]

  val hrwfsParkCmd: EpicsCommand[F]

  val hrwfsPosCmd: HrwfsPosCmd[F]

  val scienceFoldParkCmd: EpicsCommand[F]

  val scienceFoldPosCmd: ScienceFoldPosCmd[F]

  val observe: EpicsCommand[F]

  val endObserve: EpicsCommand[F]

  val aoCorrect: AoCorrect[F]

  val aoPrepareControlMatrix: AoPrepareControlMatrix[F]

  val aoFlatten: EpicsCommand[F]

  val aoStatistics: AoStatistics[F]

  val targetFilter: TargetFilter[F]

  def absorbTipTilt: F[Int]

  def m1GuideSource: F[String]

  def m1Guide: F[BinaryOnOff]

  def m2p1Guide: F[String]

  def m2p2Guide: F[String]

  def m2oiGuide: F[String]

  def m2aoGuide: F[String]

  def comaCorrect: F[String]

  def m2GuideState: F[BinaryOnOff]

  def xoffsetPoA1: F[Double]

  def yoffsetPoA1: F[Double]

  def xoffsetPoB1: F[Double]

  def yoffsetPoB1: F[Double]

  def xoffsetPoC1: F[Double]

  def yoffsetPoC1: F[Double]

  def sourceAWavelength: F[Double]

  def sourceBWavelength: F[Double]

  def sourceCWavelength: F[Double]

  def chopBeam: F[String]

  def p1FollowS: F[String]

  def p2FollowS: F[String]

  def oiFollowS: F[String]

  def aoFollowS: F[String]

  def p1Parked: F[Boolean]

  def p2Parked: F[Boolean]

  def oiName: F[String]

  def oiParked: F[Boolean]

  def pwfs1On: F[BinaryYesNo]

  def pwfs2On: F[BinaryYesNo]

  def oiwfsOn: F[BinaryYesNo]

  def sfName: F[String]

  def sfParked: F[Int]

  def agHwName: F[String]

  def agHwParked: F[Int]

  def instrAA: F[Double]

  def inPosition: F[String]

  def agInPosition: F[Double]

  val pwfs1ProbeGuideConfig: ProbeGuideConfig[F]

  val pwfs2ProbeGuideConfig: ProbeGuideConfig[F]

  val oiwfsProbeGuideConfig: ProbeGuideConfig[F]

  // This functions returns a F that, when run, first waits tcsSettleTime to absorb in-position transients, then waits
  // for the in-position to change to true and stay true for stabilizationTime. It will wait up to `timeout`
  // seconds for that to happen.
  def waitInPosition(stabilizationTime: Duration, timeout: FiniteDuration)(implicit
    T:                                  Timer[F]
  ): F[Unit]

  // `waitAGInPosition` works like `waitInPosition`, but for the AG in-position flag.
  /* TODO: AG inposition can take up to 1[s] to react to a TCS command. If the value is read before that, it may induce
     * an error. A better solution is to detect the edge, from not in position to in-position.
     */
  def waitAGInPosition(timeout: FiniteDuration)(implicit T: Timer[F]): F[Unit]

  def hourAngle: F[String]

  def localTime: F[String]

  def trackingFrame: F[String]

  def trackingEpoch: F[Double]

  def equinox: F[Double]

  def trackingEquinox: F[String]

  def trackingDec: F[Double]

  def trackingRA: F[Double]

  def elevation: F[Double]

  def azimuth: F[Double]

  def crPositionAngle: F[Double]

  def ut: F[String]

  def date: F[String]

  def m2Baffle: F[String]

  def m2CentralBaffle: F[String]

  def st: F[String]

  def sfRotation: F[Double]

  def sfTilt: F[Double]

  def sfLinear: F[Double]

  def instrPA: F[Double]

  def targetA: F[List[Double]]

  def aoFoldPosition: F[String]

  def useAo: F[BinaryYesNo]

  def airmass: F[Double]

  def airmassStart: F[Double]

  def airmassEnd: F[Double]

  def carouselMode: F[String]

  def crFollow: F[Int]

  def crTrackingFrame: F[String]

  def sourceATarget: Target[F]

  val pwfs1Target: Target[F]

  val pwfs2Target: Target[F]

  val oiwfsTarget: Target[F]

  def parallacticAngle: F[Angle]

  def m2UserFocusOffset: F[Double]

  def pwfs1IntegrationTime: F[Double]

  def pwfs2IntegrationTime: F[Double]

  // Attribute must be changed back to Double after EPICS channel is fixed.
  def oiwfsIntegrationTime: F[Double]

  def gsaoiPort: F[Int]

  def gpiPort: F[Int]

  def f2Port: F[Int]

  def niriPort: F[Int]

  def gnirsPort: F[Int]

  def nifsPort: F[Int]

  def gmosPort: F[Int]

  def ghostPort: F[Int]

  def aoGuideStarX: F[Double]

  def aoGuideStarY: F[Double]

  def aoPreparedCMX: F[Double]

  def aoPreparedCMY: F[Double]

  // GeMS Commands
  import VirtualGemsTelescope._

  val g1ProbeGuideCmd: ProbeGuideCmd[F]

  val g2ProbeGuideCmd: ProbeGuideCmd[F]

  val g3ProbeGuideCmd: ProbeGuideCmd[F]

  val g4ProbeGuideCmd: ProbeGuideCmd[F]

  def gemsProbeGuideCmd(g: VirtualGemsTelescope): ProbeGuideCmd[F] = g match {
    case G1 => g1ProbeGuideCmd
    case G2 => g2ProbeGuideCmd
    case G3 => g3ProbeGuideCmd
    case G4 => g4ProbeGuideCmd
  }

  val wavelG1: TargetWavelengthCmd[F]

  val wavelG2: TargetWavelengthCmd[F]

  val wavelG3: TargetWavelengthCmd[F]

  val wavelG4: TargetWavelengthCmd[F]

  def gemsWavelengthCmd(g: VirtualGemsTelescope): TargetWavelengthCmd[F] = g match {
    case G1 => wavelG1
    case G2 => wavelG2
    case G3 => wavelG3
    case G4 => wavelG4
  }

  def gwfs1Target: Target[F]

  def gwfs2Target: Target[F]

  def gwfs3Target: Target[F]

  def gwfs4Target: Target[F]

  def gemsTarget(g: VirtualGemsTelescope): Target[F] = g match {
    case G1 => gwfs1Target
    case G2 => gwfs2Target
    case G3 => gwfs3Target
    case G4 => gwfs4Target
  }

  val cwfs1ProbeFollowCmd: ProbeFollowCmd[F]

  val cwfs2ProbeFollowCmd: ProbeFollowCmd[F]

  val cwfs3ProbeFollowCmd: ProbeFollowCmd[F]

  val odgw1FollowCmd: ProbeFollowCmd[F]

  val odgw2FollowCmd: ProbeFollowCmd[F]

  val odgw3FollowCmd: ProbeFollowCmd[F]

  val odgw4FollowCmd: ProbeFollowCmd[F]

  val odgw1ParkCmd: EpicsCommand[F]

  val odgw2ParkCmd: EpicsCommand[F]

  val odgw3ParkCmd: EpicsCommand[F]

  val odgw4ParkCmd: EpicsCommand[F]

  // GeMS statuses

  def cwfs1Follow: F[Boolean]

  def cwfs2Follow: F[Boolean]

  def cwfs3Follow: F[Boolean]

  def odgw1Follow: F[Boolean]

  def odgw2Follow: F[Boolean]

  def odgw3Follow: F[Boolean]

  def odgw4Follow: F[Boolean]

  def odgw1Parked: F[Boolean]

  def odgw2Parked: F[Boolean]

  def odgw3Parked: F[Boolean]

  def odgw4Parked: F[Boolean]

  def g1MapName: F[Option[GemsSource]]

  def g2MapName: F[Option[GemsSource]]

  def g3MapName: F[Option[GemsSource]]

  def g4MapName: F[Option[GemsSource]]

  def g1Wavelength: F[Double]

  def g2Wavelength: F[Double]

  def g3Wavelength: F[Double]

  def g4Wavelength: F[Double]

  def gemsWavelength(g: VirtualGemsTelescope): F[Double] = g match {
    case G1 => g1Wavelength
    case G2 => g2Wavelength
    case G3 => g3Wavelength
    case G4 => g4Wavelength
  }

  val g1GuideConfig: ProbeGuideConfig[F]

  val g2GuideConfig: ProbeGuideConfig[F]

  val g3GuideConfig: ProbeGuideConfig[F]

  val g4GuideConfig: ProbeGuideConfig[F]

  def gemsGuideConfig(g: VirtualGemsTelescope): ProbeGuideConfig[F] = g match {
    case G1 => g1GuideConfig
    case G2 => g2GuideConfig
    case G3 => g3GuideConfig
    case G4 => g4GuideConfig
  }
     */
  }

  val sysName: String = "TCS"

  val className: String = getClass.getName

  def build[F[_]: Dispatcher: Temporal: Parallel](
    service: EpicsService[F],
    tops:    Map[String, String]
  ): Resource[F, TcsEpicsSystem[F]] = {
    val top = tops.getOrElse("tcs", "tcs:")
    for {
      channels <- buildChannels(service, top)
      applyCmd <-
        GeminiApplyCommand.build(service, channels.telltale, top + "apply", top + "applyC")
    } yield new TcsEpicsSystemImpl[F](new TcsEpicsImpl[F](applyCmd, channels))
  }

  val CadDirName: String = ".DIR"

  case class TcsChannels[F[_]](
    telltale:         TelltaleChannel,
    telescopeParkDir: Channel[F, CadDirective]
  )

  def buildChannels[F[_]](service: EpicsService[F], top: String): Resource[F, TcsChannels[F]] =
    for {
      tt  <- service.getChannel[String](top + "sad:health.VAL").map(TelltaleChannel(sysName, _))
      tpd <- service.getChannel[CadDirective](top + "telpark" + CadDirName)
    } yield TcsChannels[F](
      tt,
      tpd
    )

  case class TcsCommandsImpl[F[_]: Monad: Parallel](
    tcsEpics: TcsEpics[F],
    timeout:  FiniteDuration,
    params:   ParameterList[F]
  ) extends TcsCommands[F] {
    override def post: VerifiedEpics[F, ApplyCommandResult] =
      params.compile *> tcsEpics.post(timeout)

    override val mcsParkCmd: BaseCommand[F, TcsCommands[F]] =
      new BaseCommand[F, TcsCommands[F]] {
        override def mark: TcsCommands[F] =
          TcsCommandsImpl(tcsEpics, timeout, params :+ tcsEpics.mountParkCmd.mark)
      }
  }

  class TcsEpicsSystemImpl[F[_]: Monad: Parallel](epics: TcsEpics[F]) extends TcsEpicsSystem[F] {
    override def startCommand(timeout: FiniteDuration): TcsCommands[F] =
      TcsCommandsImpl(epics, timeout, List.empty)
  }

  class TcsEpicsImpl[F[_]: Monad](
    applyCmd: GeminiApplyCommand[F],
    channels: TcsChannels[F]
  ) extends TcsEpics[F] {
    override def post(timeout: FiniteDuration): VerifiedEpics[F, ApplyCommandResult] =
      applyCmd.post(timeout)

    override val mountParkCmd: ParameterlessCommandChannel[F] =
      ParameterlessCommandChannel(channels.telltale, channels.telescopeParkDir)
  }

  case class ParameterlessCommandChannel[F[_]: Monad](
    tt:         TelltaleChannel,
    dirChannel: Channel[F, CadDirective]
  ) {
    val mark: VerifiedEpics[F, Unit] =
      writeChannel[F, CadDirective](tt, dirChannel)(Applicative[F].pure(CadDirective.MARK))
  }

  trait BaseCommand[F[_], +S] {
    def mark: S
  }

  trait TcsCommands[F[_]] {
    def post: VerifiedEpics[F, ApplyCommandResult]

    val mcsParkCmd: BaseCommand[F, TcsCommands[F]]
  }
  /*
  trait ProbeGuideCmd[F[_]] extends EpicsCommand[F] {
    def setNodachopa(v: String): F[Unit]
    def setNodachopb(v: String): F[Unit]
    def setNodbchopa(v: String): F[Unit]
    def setNodbchopb(v: String): F[Unit]
  }

  final class ProbeGuideCmdImpl[F[_]: Async](csName: String, epicsService: CaService)
      extends EpicsCommandBase[F](sysName)
      with ProbeGuideCmd[F] {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender(csName))

    private val nodachopa                         = cs.map(_.getString("nodachopa"))
    override def setNodachopa(v: String): F[Unit] = setParameter(nodachopa, v)

    private val nodachopb                         = cs.map(_.getString("nodachopb"))
    override def setNodachopb(v: String): F[Unit] = setParameter(nodachopb, v)

    private val nodbchopa                         = cs.map(_.getString("nodbchopa"))
    override def setNodbchopa(v: String): F[Unit] = setParameter(nodbchopa, v)

    private val nodbchopb                         = cs.map(_.getString("nodbchopb"))
    override def setNodbchopb(v: String): F[Unit] = setParameter(nodbchopb, v)
  }

  trait WfsObserveCmd[F[_]] extends EpicsCommand[F] {
    def setNoexp(v:  Integer): F[Unit]
    def setInt(v:    Double): F[Unit]
    def setOutopt(v: String): F[Unit]
    def setLabel(v:  String): F[Unit]
    def setOutput(v: String): F[Unit]
    def setPath(v:   String): F[Unit]
    def setName(v:   String): F[Unit]
  }

  final class WfsObserveCmdImpl[F[_]: Async](csName: String, epicsService: CaService)
      extends EpicsCommandBase[F](sysName)
      with WfsObserveCmd[F] {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender(csName))

    private val noexp                          = cs.map(_.getInteger("noexp"))
    override def setNoexp(v: Integer): F[Unit] = setParameter(noexp, v)

    private val int                         = cs.map(_.getDouble("int"))
    override def setInt(v: Double): F[Unit] = setParameter[F, java.lang.Double](int, v)

    private val outopt                         = cs.map(_.getString("outopt"))
    override def setOutopt(v: String): F[Unit] = setParameter(outopt, v)

    private val label                         = cs.map(_.getString("label"))
    override def setLabel(v: String): F[Unit] = setParameter(label, v)

    private val output                         = cs.map(_.getString("output"))
    override def setOutput(v: String): F[Unit] = setParameter(output, v)

    private val path                         = cs.map(_.getString("path"))
    override def setPath(v: String): F[Unit] = setParameter(path, v)

    private val name                         = cs.map(_.getString("name"))
    override def setName(v: String): F[Unit] = setParameter(name, v)
  }

  trait ProbeFollowCmd[F[_]] extends EpicsCommand[F] {
    def setFollowState(v: String): F[Unit]
  }

  final class ProbeFollowCmdImpl[F[_]: Async](csName: String, epicsService: CaService)
      extends EpicsCommandBase[F](sysName)
      with ProbeFollowCmd[F] {
    override protected val cs: Option[CaCommandSender] = Option(
      epicsService.getCommandSender(csName)
    )

    private val follow                              = cs.map(_.getString("followState"))
    override def setFollowState(v: String): F[Unit] = setParameter(follow, v)
  }

  trait TargetWavelengthCmd[F[_]] extends EpicsCommand[F] {
    def setWavel(v: Double): F[Unit]
  }

  final class TargetWavelengthCmdImpl[F[_]: Async](csName: String, epicsService: CaService)
      extends EpicsCommandBase[F](sysName)
      with TargetWavelengthCmd[F] {
    override val cs: Option[CaCommandSender] = Option(epicsService.getCommandSender(csName))

    private val wavel = cs.map(_.getDouble("wavel"))

    override def setWavel(v: Double): F[Unit] = setParameter[F, java.lang.Double](wavel, v)
  }

  case class ProbeGuideChannels[F[_]](
    val nodachopa: Channel[F, Int],
    val nodachopb: Channel[F, Int],
    val nodbchopa: Channel[F, Int],
    val nodbchopb: Channel[F, Int]
  )

  trait ProbeGuideConfig[F[_]] {
    def nodachopa: VerifiedEpics[F, Int]
    def nodachopb: VerifiedEpics[F, Int]
    def nodbchopa: VerifiedEpics[F, Int]
    def nodbchopb: VerifiedEpics[F, Int]
  }

  final class ProbeGuideConfigImpl[F[_]: Sync](
    protected val prefix:   String,
    protected val service: EpicsService[F]
  ) extends ProbeGuideConfig[F] {
    private val aa = service.
    override def nodachopa: VerifiedEpics[F, Int] = service.safeAttributeSIntF(
      tcsState.getIntegerAttribute(prefix + "nodachopa")
    )
    override def nodachopb: VerifiedEpics[F, Int] = safeAttributeSIntF(
      tcsState.getIntegerAttribute(prefix + "nodachopb")
    )
    override def nodbchopa: VerifiedEpics[F, Int] = safeAttributeSIntF(
      tcsState.getIntegerAttribute(prefix + "nodbchopa")
    )
    override def nodbchopb: VerifiedEpics[F, Int] = safeAttributeSIntF(
      tcsState.getIntegerAttribute(prefix + "nodbchopb")
    )
  }

  trait Target[F[_]] {
    def objectName: F[String]
    def ra: F[Double]
    def dec: F[Double]
    def frame: F[String]
    def equinox: F[String]
    def epoch: F[String]
    def properMotionRA: F[Double]
    def properMotionDec: F[Double]
    def centralWavelenght: F[Double]
    def parallax: F[Double]
    def radialVelocity: F[Double]
  }

  // TODO: Delete me after fully moved to tagless
  implicit class TargetIOOps(val tio: Target[IO]) extends AnyVal {
    def to[F[_]: LiftIO]: Target[F] = new Target[F] {
      def objectName: F[String]        = tio.objectName.to[F]
      def ra: F[Double]                = tio.ra.to[F]
      def dec: F[Double]               = tio.dec.to[F]
      def frame: F[String]             = tio.frame.to[F]
      def equinox: F[String]           = tio.equinox.to[F]
      def epoch: F[String]             = tio.epoch.to[F]
      def properMotionRA: F[Double]    = tio.properMotionRA.to[F]
      def properMotionDec: F[Double]   = tio.properMotionDec.to[F]
      def centralWavelenght: F[Double] = tio.centralWavelenght.to[F]
      def parallax: F[Double]          = tio.parallax.to[F]
      def radialVelocity: F[Double]    = tio.radialVelocity.to[F]
    }
  }

  sealed trait VirtualGemsTelescope extends Product with Serializable

  object VirtualGemsTelescope {
    case object G1 extends VirtualGemsTelescope
    case object G2 extends VirtualGemsTelescope
    case object G3 extends VirtualGemsTelescope
    case object G4 extends VirtualGemsTelescope
  }

  trait M1GuideCmd[F[_]] {
    def setState(v: String): VerifiedEpics[F, Unit]
  }

  trait M2GuideCmd[F[_]] {
    def setState(v: String): VerifiedEpics[F, Unit]
  }

  trait M2GuideModeCmd[F[_]] {
    def setComa(v: String): VerifiedEpics[F, Unit]
  }

  trait M2GuideConfigCmd[F[_]] {
    def setSource(v: String): VerifiedEpics[F, Unit]
    def setBeam(v:   String): VerifiedEpics[F, Unit]
    def setReset(v:  String): VerifiedEpics[F, Unit]
  }

  trait MountGuideCmd[F[_]] {
    def setSource(v:   String): VerifiedEpics[F, Unit]
    def setP1Weight(v: Double): VerifiedEpics[F, Unit]
    def setP2Weight(v: Double): VerifiedEpics[F, Unit]
    def setMode(v:     String): VerifiedEpics[F, Unit]
  }

  trait OffsetCmd[F[_]] {
    def setX(v: Double): VerifiedEpics[F, Unit]
    def setY(v: Double): VerifiedEpics[F, Unit]
  }

  trait M2Beam[F[_]] {
    def setBeam(v: String): VerifiedEpics[F, Unit]
  }

  trait HrwfsPosCmd[F[_]] {
    def setHrwfsPos(v: String): VerifiedEpics[F, Unit]
  }

  trait ScienceFoldPosCmd[F[_]] {
    def setScfold(v: String): VerifiedEpics[F, Unit]
  }

  trait AoCorrect[F[_]] {
    def setCorrections(v: String): VerifiedEpics[F, Unit]
    def setGains(v:       Int): VerifiedEpics[F, Unit]
    def setMatrix(v:      Int): VerifiedEpics[F, Unit]
  }

  trait AoPrepareControlMatrix[F[_]] {
    def setX(v:             Double): VerifiedEpics[F, Unit]
    def setY(v:             Double): VerifiedEpics[F, Unit]
    def setSeeing(v:        Double): VerifiedEpics[F, Unit]
    def setStarMagnitude(v: Double): VerifiedEpics[F, Unit]
    def setWindSpeed(v:     Double): VerifiedEpics[F, Unit]
  }

  trait AoStatistics[F[_]] {
    def setFileName(v:            String): VerifiedEpics[F, Unit]
    def setSamples(v:             Int): VerifiedEpics[F, Unit]
    def setInterval(v:            Double): VerifiedEpics[F, Unit]
    def setTriggerTimeInterval(v: Double): VerifiedEpics[F, Unit]
  }

  trait TargetFilter[F[_]] {
    def setBandwidth(v:    Double): VerifiedEpics[F, Unit]
    def setMaxVelocity(v:  Double): VerifiedEpics[F, Unit]
    def setGrabRadius(v:   Double): VerifiedEpics[F, Unit]
    def setShortCircuit(v: String): VerifiedEpics[F, Unit]
  }
   */
}
