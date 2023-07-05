// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs
import cats.effect.Ref
import cats.{Applicative, Monad, Parallel}
import navigate.epics.EpicsSystem.TelltaleChannel
import navigate.epics.VerifiedEpics.VerifiedEpics
import navigate.epics.{TestChannel, VerifiedEpics}
import navigate.model.enums.{DomeMode, ShutterMode}
import navigate.server.acm.{CadDirective, GeminiApplyCommand}
import navigate.server.epicsdata.{BinaryOnOff, BinaryYesNo}
import navigate.server.tcs.TcsEpicsSystem.{
  EnclosureChannels,
  OriginChannels,
  RotatorChannels,
  SlewChannels,
  TargetChannels,
  TcsChannels
}
import navigate.server.ApplyCommandResult
import monocle.{Focus, Lens}

import scala.concurrent.duration.FiniteDuration

object TestTcsEpicsSystem {

  class TestApplyCommand[F[_]: Applicative] extends GeminiApplyCommand[F] {
    override def post(timeout: FiniteDuration): VerifiedEpics[F, F, ApplyCommandResult] =
      VerifiedEpics.pureF[F, F, ApplyCommandResult](ApplyCommandResult.Completed)
  }

  case class EnclosureChannelsState(
    ecsDomeMode:      TestChannel.State[String],
    ecsShutterMode:   TestChannel.State[String],
    ecsSlitHeight:    TestChannel.State[String],
    ecsDomeEnable:    TestChannel.State[String],
    ecsShutterEnable: TestChannel.State[String],
    ecsMoveAngle:     TestChannel.State[String],
    ecsShutterTop:    TestChannel.State[String],
    ecsShutterBottom: TestChannel.State[String],
    ecsVentGateEast:  TestChannel.State[String],
    ecsVentGateWest:  TestChannel.State[String]
  )

  case class TargetChannelsState(
    objectName:     TestChannel.State[String],
    coordSystem:    TestChannel.State[String],
    coord1:         TestChannel.State[String],
    coord2:         TestChannel.State[String],
    epoch:          TestChannel.State[String],
    equinox:        TestChannel.State[String],
    parallax:       TestChannel.State[String],
    properMotion1:  TestChannel.State[String],
    properMotion2:  TestChannel.State[String],
    radialVelocity: TestChannel.State[String],
    brightness:     TestChannel.State[String],
    ephemerisFile:  TestChannel.State[String]
  )

  case class SlewChannelsState(
    zeroChopThrow:            TestChannel.State[String],
    zeroSourceOffset:         TestChannel.State[String],
    zeroSourceDiffTrack:      TestChannel.State[String],
    zeroMountOffset:          TestChannel.State[String],
    zeroMountDiffTrack:       TestChannel.State[String],
    shortcircuitTargetFilter: TestChannel.State[String],
    shortcircuitMountFilter:  TestChannel.State[String],
    resetPointing:            TestChannel.State[String],
    stopGuide:                TestChannel.State[String],
    zeroGuideOffset:          TestChannel.State[String],
    zeroInstrumentOffset:     TestChannel.State[String],
    autoparkPwfs1:            TestChannel.State[String],
    autoparkPwfs2:            TestChannel.State[String],
    autoparkOiwfs:            TestChannel.State[String],
    autoparkGems:             TestChannel.State[String],
    autoparkAowfs:            TestChannel.State[String]
  )

  case class RotatorChannelState(
    ipa:     TestChannel.State[String],
    system:  TestChannel.State[String],
    equinox: TestChannel.State[String],
    iaa:     TestChannel.State[String]
  )

  case class OriginChannelState(
    xa: TestChannel.State[String],
    ya: TestChannel.State[String],
    xb: TestChannel.State[String],
    yb: TestChannel.State[String],
    xc: TestChannel.State[String],
    yc: TestChannel.State[String]
  )

  case class State(
    telltale:         TestChannel.State[String],
    telescopeParkDir: TestChannel.State[CadDirective],
    mountFollow:      TestChannel.State[String],
    rotStopBrake:     TestChannel.State[String],
    rotParkDir:       TestChannel.State[CadDirective],
    rotFollow:        TestChannel.State[String],
    rotMoveAngle:     TestChannel.State[String],
    enclosure:        EnclosureChannelsState,
    sourceA:          TargetChannelsState,
    wavelSourceA:     TestChannel.State[String],
    slew:             SlewChannelsState,
    rotator:          RotatorChannelState,
    origin:           OriginChannelState,
    focusOffset:      TestChannel.State[String]
  )

  val defaultState: State = State(
    telltale = TestChannel.State.default,
    telescopeParkDir = TestChannel.State.default,
    mountFollow = TestChannel.State.default,
    rotStopBrake = TestChannel.State.default,
    rotParkDir = TestChannel.State.default,
    rotFollow = TestChannel.State.default,
    rotMoveAngle = TestChannel.State.default,
    enclosure = EnclosureChannelsState(
      ecsDomeMode = TestChannel.State.default,
      ecsShutterMode = TestChannel.State.default,
      ecsSlitHeight = TestChannel.State.default,
      ecsDomeEnable = TestChannel.State.default,
      ecsShutterEnable = TestChannel.State.default,
      ecsMoveAngle = TestChannel.State.default,
      ecsShutterTop = TestChannel.State.default,
      ecsShutterBottom = TestChannel.State.default,
      ecsVentGateEast = TestChannel.State.default,
      ecsVentGateWest = TestChannel.State.default
    ),
    sourceA = TargetChannelsState(
      objectName = TestChannel.State.default,
      coordSystem = TestChannel.State.default,
      coord1 = TestChannel.State.default,
      coord2 = TestChannel.State.default,
      epoch = TestChannel.State.default,
      equinox = TestChannel.State.default,
      parallax = TestChannel.State.default,
      properMotion1 = TestChannel.State.default,
      properMotion2 = TestChannel.State.default,
      radialVelocity = TestChannel.State.default,
      brightness = TestChannel.State.default,
      ephemerisFile = TestChannel.State.default
    ),
    wavelSourceA = TestChannel.State.default,
    slew = SlewChannelsState(
      zeroChopThrow = TestChannel.State.default,
      zeroSourceOffset = TestChannel.State.default,
      zeroSourceDiffTrack = TestChannel.State.default,
      zeroMountOffset = TestChannel.State.default,
      zeroMountDiffTrack = TestChannel.State.default,
      shortcircuitTargetFilter = TestChannel.State.default,
      shortcircuitMountFilter = TestChannel.State.default,
      resetPointing = TestChannel.State.default,
      stopGuide = TestChannel.State.default,
      zeroGuideOffset = TestChannel.State.default,
      zeroInstrumentOffset = TestChannel.State.default,
      autoparkPwfs1 = TestChannel.State.default,
      autoparkPwfs2 = TestChannel.State.default,
      autoparkOiwfs = TestChannel.State.default,
      autoparkGems = TestChannel.State.default,
      autoparkAowfs = TestChannel.State.default
    ),
    rotator = RotatorChannelState(
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default,
      TestChannel.State.default
    ),
    origin = OriginChannelState(
      xa = TestChannel.State.default,
      ya = TestChannel.State.default,
      xb = TestChannel.State.default,
      yb = TestChannel.State.default,
      xc = TestChannel.State.default,
      yc = TestChannel.State.default
    ),
    focusOffset = TestChannel.State.default
  )

  def buildEnclosureChannels[F[_]: Applicative](s: Ref[F, State]): EnclosureChannels[F] =
    EnclosureChannels[F](
      ecsDomeMode = new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsDomeMode)),
      ecsShutterMode =
        new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsShutterMode)),
      ecsSlitHeight = new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsSlitHeight)),
      ecsDomeEnable = new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsDomeEnable)),
      ecsShutterEnable =
        new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsShutterEnable)),
      ecsMoveAngle = new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsMoveAngle)),
      ecsShutterTop = new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsShutterTop)),
      ecsShutterBottom =
        new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsShutterBottom)),
      ecsVentGateEast =
        new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsVentGateEast)),
      ecsVentGateWest =
        new TestChannel[F, State, String](s, Focus[State](_.enclosure.ecsVentGateWest))
    )

  def buildTargetChannels[F[_]: Applicative](
    s: Ref[F, State],
    l: Lens[State, TargetChannelsState]
  ): TargetChannels[F] =
    TargetChannels[F](
      objectName =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.objectName))),
      coordSystem =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.coordSystem))),
      coord1 =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.coord1))),
      coord2 =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.coord2))),
      epoch = new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.epoch))),
      equinox =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.equinox))),
      parallax =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.parallax))),
      properMotion1 =
        new TestChannel[F, State, String](s,
                                          l.andThen(Focus[TargetChannelsState](_.properMotion1))
        ),
      properMotion2 =
        new TestChannel[F, State, String](s,
                                          l.andThen(Focus[TargetChannelsState](_.properMotion2))
        ),
      radialVelocity =
        new TestChannel[F, State, String](s,
                                          l.andThen(Focus[TargetChannelsState](_.radialVelocity))
        ),
      brightness =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.brightness))),
      ephemerisFile =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.ephemerisFile)))
    )

  def buildSlewChannels[F[_]: Applicative](
    s: Ref[F, State]
  ): SlewChannels[F] =
    SlewChannels(
      zeroChopThrow = new TestChannel[F, State, String](s, Focus[State](_.slew.zeroChopThrow)),
      zeroSourceOffset =
        new TestChannel[F, State, String](s, Focus[State](_.slew.zeroSourceOffset)),
      zeroSourceDiffTrack = new TestChannel[F, State, String](
        s,
        Focus[State](_.slew.zeroSourceDiffTrack)
      ),
      zeroMountOffset = new TestChannel[F, State, String](
        s,
        Focus[State](_.slew.zeroMountOffset)
      ),
      zeroMountDiffTrack = new TestChannel[F, State, String](
        s,
        Focus[State](_.slew.zeroMountDiffTrack)
      ),
      shortcircuitTargetFilter = new TestChannel[F, State, String](
        s,
        Focus[State](_.slew.shortcircuitTargetFilter)
      ),
      shortcircuitMountFilter = new TestChannel[F, State, String](
        s,
        Focus[State](_.slew.shortcircuitMountFilter)
      ),
      resetPointing = new TestChannel[F, State, String](s, Focus[State](_.slew.resetPointing)),
      stopGuide = new TestChannel[F, State, String](s, Focus[State](_.slew.stopGuide)),
      zeroGuideOffset = new TestChannel[F, State, String](s, Focus[State](_.slew.zeroGuideOffset)),
      zeroInstrumentOffset = new TestChannel[F, State, String](
        s,
        Focus[State](_.slew.zeroInstrumentOffset)
      ),
      autoparkPwfs1 = new TestChannel[F, State, String](s, Focus[State](_.slew.autoparkPwfs1)),
      autoparkPwfs2 = new TestChannel[F, State, String](s, Focus[State](_.slew.autoparkPwfs2)),
      autoparkOiwfs = new TestChannel[F, State, String](s, Focus[State](_.slew.autoparkOiwfs)),
      autoparkGems = new TestChannel[F, State, String](s, Focus[State](_.slew.autoparkGems)),
      autoparkAowfs = new TestChannel[F, State, String](s, Focus[State](_.slew.autoparkAowfs))
    )

  def buildRotatorChannels[F[_]: Applicative](s: Ref[F, State]): RotatorChannels[F] =
    RotatorChannels(
      new TestChannel[F, State, String](s, Focus[State](_.rotator.ipa)),
      new TestChannel[F, State, String](s, Focus[State](_.rotator.system)),
      new TestChannel[F, State, String](s, Focus[State](_.rotator.equinox)),
      new TestChannel[F, State, String](s, Focus[State](_.rotator.iaa))
    )

  def buildOriginChannels[F[_]: Applicative](s: Ref[F, State]): OriginChannels[F] =
    OriginChannels(
      new TestChannel[F, State, String](s, Focus[State](_.origin.xa)),
      new TestChannel[F, State, String](s, Focus[State](_.origin.ya)),
      new TestChannel[F, State, String](s, Focus[State](_.origin.xb)),
      new TestChannel[F, State, String](s, Focus[State](_.origin.yb)),
      new TestChannel[F, State, String](s, Focus[State](_.origin.xc)),
      new TestChannel[F, State, String](s, Focus[State](_.origin.yc))
    )

  def buildChannels[F[_]: Applicative](s: Ref[F, State]): TcsChannels[F] =
    TcsChannels(
      telltale =
        TelltaleChannel[F]("dummy", new TestChannel[F, State, String](s, Focus[State](_.telltale))),
      telescopeParkDir =
        new TestChannel[F, State, CadDirective](s, Focus[State](_.telescopeParkDir)),
      mountFollow = new TestChannel[F, State, String](s, Focus[State](_.mountFollow)),
      rotStopBrake = new TestChannel[F, State, String](s, Focus[State](_.rotStopBrake)),
      rotParkDir = new TestChannel[F, State, CadDirective](s, Focus[State](_.rotParkDir)),
      rotFollow = new TestChannel[F, State, String](s, Focus[State](_.rotFollow)),
      rotMoveAngle = new TestChannel[F, State, String](s, Focus[State](_.rotMoveAngle)),
      enclosure = buildEnclosureChannels(s),
      sourceA = buildTargetChannels(s, Focus[State](_.sourceA)),
      wavelSourceA = new TestChannel[F, State, String](s, Focus[State](_.rotMoveAngle)),
      slew = buildSlewChannels(s),
      rotator = buildRotatorChannels(s),
      origin = buildOriginChannels(s),
      focusOffset = new TestChannel[F, State, String](s, Focus[State](_.focusOffset))
    )

  def build[F[_]: Monad: Parallel](s: Ref[F, State]): TcsEpicsSystem[F] =
    TcsEpicsSystem.buildSystem(new TestApplyCommand[F], buildChannels(s))

}
