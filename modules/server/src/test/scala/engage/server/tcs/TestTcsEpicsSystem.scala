// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.server.tcs
import cats.effect.Ref
import cats.{Applicative, Monad, Parallel}
import engage.epics.EpicsSystem.TelltaleChannel
import engage.epics.VerifiedEpics.VerifiedEpics
import engage.epics.{TestChannel, VerifiedEpics}
import engage.model.enums.{DomeMode, ShutterMode}
import engage.server.acm.{CadDirective, GeminiApplyCommand}
import engage.server.epicsdata.{BinaryOnOff, BinaryYesNo}
import engage.server.tcs.TcsEpicsSystem.{
  EnclosureChannels,
  SlewChannels,
  TargetChannels,
  TcsChannels
}
import engage.server.ApplyCommandResult
import monocle.{Focus, Lens}

import scala.concurrent.duration.FiniteDuration

object TestTcsEpicsSystem {

  class TestApplyCommand[F[_]: Applicative] extends GeminiApplyCommand[F] {
    override def post(timeout: FiniteDuration): VerifiedEpics[F, F, ApplyCommandResult] =
      VerifiedEpics.pureF[F, F, ApplyCommandResult](ApplyCommandResult.Completed)
  }

  case class EnclosureChannelsState(
    ecsDomeMode:      TestChannel.State[DomeMode],
    ecsShutterMode:   TestChannel.State[ShutterMode],
    ecsSlitHeight:    TestChannel.State[Double],
    ecsDomeEnable:    TestChannel.State[BinaryOnOff],
    ecsShutterEnable: TestChannel.State[BinaryOnOff],
    ecsMoveAngle:     TestChannel.State[Double],
    ecsShutterTop:    TestChannel.State[Double],
    ecsShutterBottom: TestChannel.State[Double],
    ecsVentGateEast:  TestChannel.State[Double],
    ecsVentGateWest:  TestChannel.State[Double]
  )

  case class TargetChannelsState(
    objectName:     TestChannel.State[String],
    coordSystem:    TestChannel.State[String],
    coord1:         TestChannel.State[Double],
    coord2:         TestChannel.State[Double],
    epoch:          TestChannel.State[String],
    equinox:        TestChannel.State[String],
    parallax:       TestChannel.State[Double],
    properMotion1:  TestChannel.State[Double],
    properMotion2:  TestChannel.State[Double],
    radialVelocity: TestChannel.State[Double],
    brightness:     TestChannel.State[Double],
    ephemerisFile:  TestChannel.State[String]
  )

  case class SlewChannelsState(
    zeroChopThrow:            TestChannel.State[BinaryOnOff],
    zeroSourceOffset:         TestChannel.State[BinaryOnOff],
    zeroSourceDiffTrack:      TestChannel.State[BinaryOnOff],
    zeroMountOffset:          TestChannel.State[BinaryOnOff],
    zeroMountDiffTrack:       TestChannel.State[BinaryOnOff],
    shortcircuitTargetFilter: TestChannel.State[BinaryOnOff],
    shortcircuitMountFilter:  TestChannel.State[BinaryOnOff],
    resetPointing:            TestChannel.State[BinaryOnOff],
    stopGuide:                TestChannel.State[BinaryOnOff],
    zeroGuideOffset:          TestChannel.State[BinaryOnOff],
    zeroInstrumentOffset:     TestChannel.State[BinaryOnOff],
    autoparkPwfs1:            TestChannel.State[BinaryOnOff],
    autoparkPwfs2:            TestChannel.State[BinaryOnOff],
    autoparkOiwfs:            TestChannel.State[BinaryOnOff],
    autoparkGems:             TestChannel.State[BinaryOnOff],
    autoparkAowfs:            TestChannel.State[BinaryOnOff]
  )

  case class State(
    telltale:         TestChannel.State[String],
    telescopeParkDir: TestChannel.State[CadDirective],
    mountFollow:      TestChannel.State[BinaryOnOff],
    rotStopBrake:     TestChannel.State[BinaryYesNo],
    rotParkDir:       TestChannel.State[CadDirective],
    rotFollow:        TestChannel.State[BinaryOnOff],
    rotMoveAngle:     TestChannel.State[Double],
    enclosure:        EnclosureChannelsState,
    sourceA:          TargetChannelsState,
    wavelSourceA:     TestChannel.State[Double],
    slew:             SlewChannelsState
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
    )
  )

  def buildEnclosureChannels[F[_]: Applicative](s: Ref[F, State]): EnclosureChannels[F] =
    EnclosureChannels[F](
      ecsDomeMode = new TestChannel[F, State, DomeMode](s, Focus[State](_.enclosure.ecsDomeMode)),
      ecsShutterMode =
        new TestChannel[F, State, ShutterMode](s, Focus[State](_.enclosure.ecsShutterMode)),
      ecsSlitHeight = new TestChannel[F, State, Double](s, Focus[State](_.enclosure.ecsSlitHeight)),
      ecsDomeEnable =
        new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.enclosure.ecsDomeEnable)),
      ecsShutterEnable =
        new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.enclosure.ecsShutterEnable)),
      ecsMoveAngle = new TestChannel[F, State, Double](s, Focus[State](_.enclosure.ecsMoveAngle)),
      ecsShutterTop = new TestChannel[F, State, Double](s, Focus[State](_.enclosure.ecsShutterTop)),
      ecsShutterBottom =
        new TestChannel[F, State, Double](s, Focus[State](_.enclosure.ecsShutterBottom)),
      ecsVentGateEast =
        new TestChannel[F, State, Double](s, Focus[State](_.enclosure.ecsVentGateEast)),
      ecsVentGateWest =
        new TestChannel[F, State, Double](s, Focus[State](_.enclosure.ecsVentGateWest))
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
        new TestChannel[F, State, Double](s, l.andThen(Focus[TargetChannelsState](_.coord1))),
      coord2 =
        new TestChannel[F, State, Double](s, l.andThen(Focus[TargetChannelsState](_.coord2))),
      epoch = new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.epoch))),
      equinox =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.equinox))),
      parallax =
        new TestChannel[F, State, Double](s, l.andThen(Focus[TargetChannelsState](_.parallax))),
      properMotion1 =
        new TestChannel[F, State, Double](s,
                                          l.andThen(Focus[TargetChannelsState](_.properMotion1))
        ),
      properMotion2 =
        new TestChannel[F, State, Double](s,
                                          l.andThen(Focus[TargetChannelsState](_.properMotion2))
        ),
      radialVelocity =
        new TestChannel[F, State, Double](s,
                                          l.andThen(Focus[TargetChannelsState](_.radialVelocity))
        ),
      brightness =
        new TestChannel[F, State, Double](s, l.andThen(Focus[TargetChannelsState](_.brightness))),
      ephemerisFile =
        new TestChannel[F, State, String](s, l.andThen(Focus[TargetChannelsState](_.ephemerisFile)))
    )

  def buildSlewChannels[F[_]: Applicative](
    s: Ref[F, State]
  ): SlewChannels[F] =
    SlewChannels(
      zeroChopThrow = new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.slew.zeroChopThrow)),
      zeroSourceOffset =
        new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.slew.zeroSourceOffset)),
      zeroSourceDiffTrack = new TestChannel[F, State, BinaryOnOff](
        s,
        Focus[State](_.slew.zeroSourceDiffTrack)
      ),
      zeroMountOffset = new TestChannel[F, State, BinaryOnOff](
        s,
        Focus[State](_.slew.zeroMountOffset)
      ),
      zeroMountDiffTrack = new TestChannel[F, State, BinaryOnOff](
        s,
        Focus[State](_.slew.zeroMountDiffTrack)
      ),
      shortcircuitTargetFilter = new TestChannel[F, State, BinaryOnOff](
        s,
        Focus[State](_.slew.shortcircuitTargetFilter)
      ),
      shortcircuitMountFilter = new TestChannel[F, State, BinaryOnOff](
        s,
        Focus[State](_.slew.shortcircuitMountFilter)
      ),
      resetPointing = new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.slew.resetPointing)),
      stopGuide = new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.slew.stopGuide)),
      zeroGuideOffset =
        new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.slew.zeroGuideOffset)),
      zeroInstrumentOffset = new TestChannel[F, State, BinaryOnOff](
        s,
        Focus[State](_.slew.zeroInstrumentOffset)
      ),
      autoparkPwfs1 = new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.slew.autoparkPwfs1)),
      autoparkPwfs2 = new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.slew.autoparkPwfs2)),
      autoparkOiwfs = new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.slew.autoparkOiwfs)),
      autoparkGems = new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.slew.autoparkGems)),
      autoparkAowfs = new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.slew.autoparkAowfs))
    )

  def buildChannels[F[_]: Applicative](s: Ref[F, State]): TcsChannels[F] =
    TcsChannels(
      telltale =
        TelltaleChannel[F]("dummy", new TestChannel[F, State, String](s, Focus[State](_.telltale))),
      telescopeParkDir =
        new TestChannel[F, State, CadDirective](s, Focus[State](_.telescopeParkDir)),
      mountFollow = new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.mountFollow)),
      rotStopBrake = new TestChannel[F, State, BinaryYesNo](s, Focus[State](_.rotStopBrake)),
      rotParkDir = new TestChannel[F, State, CadDirective](s, Focus[State](_.rotParkDir)),
      rotFollow = new TestChannel[F, State, BinaryOnOff](s, Focus[State](_.rotFollow)),
      rotMoveAngle = new TestChannel[F, State, Double](s, Focus[State](_.rotMoveAngle)),
      enclosure = buildEnclosureChannels(s),
      sourceA = buildTargetChannels(s, Focus[State](_.sourceA)),
      wavelSourceA = new TestChannel[F, State, Double](s, Focus[State](_.rotMoveAngle)),
      slew = buildSlewChannels(s)
    )

  def build[F[_]: Monad: Parallel](s: Ref[F, State]): TcsEpicsSystem[F] =
    TcsEpicsSystem.buildSystem(new TestApplyCommand[F], buildChannels(s))

}
