// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import cats.effect.Sync
import cats.syntax.all.*
import ch.qos.logback.classic.spi.ILoggingEvent
import edu.gemini.schema.util.SchemaStitcher
import edu.gemini.schema.util.SourceResolver
import fs2.concurrent.Topic
import grackle.Env
import grackle.Path
import grackle.Query
import grackle.Query.Binding
import grackle.QueryCompiler.Elab
import grackle.QueryCompiler.SelectElaborator
import grackle.Result
import grackle.Schema
import grackle.TypeRef
import grackle.Value
import grackle.Value.BooleanValue
import grackle.Value.EnumValue
import grackle.Value.ListValue
import grackle.Value.ObjectValue
import grackle.Value.StringValue
import grackle.circe.CirceMapping
import grackle.syntax.given
import io.circe.syntax.*
import lucuma.core.enums.ComaOption
import lucuma.core.enums.GuideProbe
import lucuma.core.enums.Instrument
import lucuma.core.enums.M1Source
import lucuma.core.enums.MountGuideOption
import lucuma.core.enums.TipTiltSource
import lucuma.core.math.Coordinates
import lucuma.core.math.Declination
import lucuma.core.math.Epoch
import lucuma.core.math.Parallax
import lucuma.core.math.ProperMotion
import lucuma.core.math.RadialVelocity
import lucuma.core.math.RightAscension
import lucuma.core.math.Wavelength
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M1GuideConfig.M1GuideOn
import lucuma.core.model.M2GuideConfig
import lucuma.core.model.ProbeGuide
import lucuma.core.model.TelescopeGuideConfig
import lucuma.core.util.TimeSpan
import mouse.boolean.given
import navigate.model.Distance
import navigate.server.NavigateEngine
import navigate.server.tcs.AutoparkAowfs
import navigate.server.tcs.AutoparkGems
import navigate.server.tcs.AutoparkOiwfs
import navigate.server.tcs.AutoparkPwfs1
import navigate.server.tcs.AutoparkPwfs2
import navigate.server.tcs.GuideState
import navigate.server.tcs.GuiderConfig
import navigate.server.tcs.GuidersQualityValues
import navigate.server.tcs.InstrumentSpecifics
import navigate.server.tcs.Origin
import navigate.server.tcs.ResetPointing
import navigate.server.tcs.RotatorTrackConfig
import navigate.server.tcs.RotatorTrackingMode
import navigate.server.tcs.ShortcircuitMountFilter
import navigate.server.tcs.ShortcircuitTargetFilter
import navigate.server.tcs.SlewOptions
import navigate.server.tcs.StopGuide
import navigate.server.tcs.Target
import navigate.server.tcs.TcsBaseController.TcsConfig
import navigate.server.tcs.TelescopeState
import navigate.server.tcs.TrackingConfig
import navigate.server.tcs.ZeroChopThrow
import navigate.server.tcs.ZeroGuideOffset
import navigate.server.tcs.ZeroInstrumentOffset
import navigate.server.tcs.ZeroMountDiffTrack
import navigate.server.tcs.ZeroMountOffset
import navigate.server.tcs.ZeroSourceDiffTrack
import navigate.server.tcs.ZeroSourceOffset

import java.nio.file.Path as JPath
import scala.reflect.classTag

import encoder.given

class NavigateMappings[F[_]: Sync](
  server:              NavigateEngine[F],
  logTopic:            Topic[F, ILoggingEvent],
  guideStateTopic:     Topic[F, GuideState],
  guidersQualityTopic: Topic[F, GuidersQualityValues],
  telescopeStateTopic: Topic[F, TelescopeState]
)(
  override val schema: Schema
) extends CirceMapping[F] {
  import NavigateMappings._

  def guideState(p: Path, env: Env): F[Result[GuideState]] =
    server.getGuideState.attempt.map(_.fold(Result.internalError, Result.success))

  def telescopeState(p: Path, env: Env): F[Result[TelescopeState]] =
    server.getTelescopeState.attempt.map(_.fold(Result.internalError, Result.success))

  def mountPark(p: Path, env: Env): F[Result[OperationOutcome]] =
    server.mcsPark.attempt
      .map(x =>
        Result.Success(
          x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
        )
      )

  def mountFollow(p: Path, env: Env): F[Result[OperationOutcome]] =
    env
      .get[Boolean]("enable")
      .map { en =>
        server
          .mcsFollow(en)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure[OperationOutcome]("mountFollow parameter could not be parsed.").pure[F]
      )

  def rotatorPark(p: Path, env: Env): F[Result[OperationOutcome]] =
    server.rotPark.attempt
      .map(x =>
        Result.Success(
          x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
        )
      )

  def rotatorFollow(p: Path, env: Env): F[Result[OperationOutcome]] =
    env
      .get[Boolean]("enable")
      .map { en =>
        server
          .rotFollow(en)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure("rotatorFollow parameter could not be parsed.").pure[F]
      )

  def rotatorConfig(p: Path, env: Env): F[Result[OperationOutcome]] =
    env
      .get[RotatorTrackConfig]("config")
      .map { cfg =>
        server
          .rotTrackingConfig(cfg)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure("rotatorConfig parameter could not be parsed.").pure[F]
      )

  def scsFollow(p: Path, env: Env): F[Result[OperationOutcome]] =
    env
      .get[Boolean]("enable")
      .map { en =>
        server
          .scsFollow(en)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure[OperationOutcome]("scsFollow parameter could not be parsed.").pure[F]
      )

  def instrumentSpecifics(p: Path, env: Env): F[Result[OperationOutcome]] =
    env
      .get[InstrumentSpecifics]("instrumentSpecificsParams")(using classTag[InstrumentSpecifics])
      .map { isp =>
        server
          .instrumentSpecifics(isp)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result
          .failure[OperationOutcome]("InstrumentSpecifics parameters could not be parsed.")
          .pure[F]
      )

  def slew(p: Path, env: Env): F[Result[OperationOutcome]] = (for {
    so <- env.get[SlewOptions]("slewOptions")(using classTag[SlewOptions])
    tc <- env.get[TcsConfig]("config")(using classTag[TcsConfig])
  } yield server
    .slew(so, tc)
    .attempt
    .map(x =>
      Result.success(
        x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
      )
    )).getOrElse(Result.failure[OperationOutcome]("Slew parameters could not be parsed.").pure[F])

  def tcsConfig(p: Path, env: Env): F[Result[OperationOutcome]] =
    env
      .get[TcsConfig]("config")(using classTag[TcsConfig])
      .map { tc =>
        server
          .tcsConfig(tc)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result
          .failure[OperationOutcome]("tcsConfig parameters could not be parsed.")
          .pure[F]
      )

  def swapTarget(p: Path, env: Env): F[Result[OperationOutcome]] =
    env
      .get[Target]("guideTarget")(using classTag[Target])
      .map { t =>
        server
          .swapTarget(t)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result
          .failure[OperationOutcome]("swapTarget parameters could not be parsed.")
          .pure[F]
      )

  def oiwfsTarget(p: Path, env: Env): F[Result[OperationOutcome]] =
    env
      .get[Target]("target")(using classTag[Target])
      .map { oi =>
        server
          .oiwfsTarget(oi)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure[OperationOutcome]("oiwfsTarget parameters could not be parsed.").pure[F]
      )

  def oiwfsProbeTracking(p: Path, env: Env): F[Result[OperationOutcome]] =
    env
      .get[TrackingConfig]("config")(using classTag[TrackingConfig])
      .map { tc =>
        server
          .oiwfsProbeTracking(tc)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result
          .failure[OperationOutcome]("oiwfsProbeTracking parameters could not be parsed.")
          .pure[F]
      )

  def oiwfsPark(p: Path, env: Env): F[Result[OperationOutcome]] =
    server.oiwfsPark.attempt
      .map(x =>
        Result.Success(
          x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
        )
      )

  def oiwfsFollow(p: Path, env: Env): F[Result[OperationOutcome]] =
    env
      .get[Boolean]("enable")
      .map { en =>
        server
          .oiwfsFollow(en)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure[OperationOutcome]("oiwfsFollow parameter could not be parsed.").pure[F]
      )

  def oiwfsObserve(env: Env): F[Result[OperationOutcome]] =
    env
      .get[TimeSpan]("period")
      .map { p =>
        server
          .oiwfsObserve(p)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure[OperationOutcome]("oiwfsObserve parameter could not be parsed.").pure[F]
      )

  def oiwfsStopObserve: F[Result[OperationOutcome]] =
    server.oiwfsStopObserve.attempt
      .map(x =>
        Result.Success(
          x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
        )
      )

  def guideEnable(p: Path, env: Env): F[Result[OperationOutcome]] =
    env
      .get[TelescopeGuideConfig]("config")
      .map { cfg =>
        server
          .enableGuide(cfg)
          .attempt
          .map(x =>
            Result.success(
              x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
            )
          )
      }
      .getOrElse(
        Result.failure[OperationOutcome]("guideEnable parameters could not be parsed.").pure[F]
      )

  def guideDisable(p: Path, env: Env): F[Result[OperationOutcome]] =
    server.disableGuide.attempt
      .map(x =>
        Result.Success(
          x.fold(e => OperationOutcome.failure(e.getMessage), _ => OperationOutcome.success)
        )
      )

  val QueryType: TypeRef               = schema.ref("Query")
  val MutationType: TypeRef            = schema.ref("Mutation")
  val SubscriptionType: TypeRef        = schema.ref("Subscription")
  val ParkStatusType: TypeRef          = schema.ref("ParkStatus")
  val FollowStatusType: TypeRef        = schema.ref("FollowStatus")
  val OperationOutcomeType: TypeRef    = schema.ref("OperationOutcome")
  val OperationResultType: TypeRef     = schema.ref("OperationResult")
  val RotatorTrackingModeType: TypeRef = schema.ref("RotatorTrackingMode")
  val M1SourceType: TypeRef            = schema.ref("M1Source")
  val TipTiltSourceType: TypeRef       = schema.ref("TipTiltSource")

  override val selectElaborator: SelectElaborator = SelectElaborator {
    case (MutationType, "mountFollow", List(Binding("enable", BooleanValue(en))))           =>
      Elab.env("enable" -> en)
    case (MutationType, "rotatorFollow", List(Binding("enable", BooleanValue(en))))         =>
      Elab.env("enable" -> en)
    case (MutationType, "rotatorConfig", List(Binding("config", ObjectValue(fields))))      =>
      for {
        x <- Elab.liftR(
               parseRotatorConfig(fields).toResult("Could not parse rotatorConfig parameters.")
             )
        _ <- Elab.env("config", x)
      } yield ()
    case (MutationType, "scsFollow", List(Binding("enable", BooleanValue(en))))             =>
      Elab.env("enable" -> en)
    case (MutationType, "tcsConfig", List(Binding("config", ObjectValue(fields))))          =>
      for {
        x <-
          Elab.liftR(parseTcsConfigInput(fields).toResult("Could not parse TCS config parameters."))
        _ <- Elab.env("config", x)
      } yield ()
    case (MutationType,
          "slew",
          List(Binding("slewOptions", ObjectValue(so)), Binding("config", ObjectValue(cf)))
        ) =>
      for {
        x <-
          Elab.liftR(parseSlewOptionsInput(so).toResult("Could not parse Slew options parameters."))
        _ <- Elab.env("slewOptions" -> x)
        y <- Elab.liftR(parseTcsConfigInput(cf).toResult("Could not parse TCS config parameters."))
        _ <- Elab.env("config" -> y)
      } yield ()
    case (MutationType,
          "instrumentSpecifics",
          List(Binding("instrumentSpecificsParams", ObjectValue(fields)))
        ) =>
      for {
        x <- Elab.liftR(
               parseInstrumentSpecificsInput(fields).toResult(
                 "Could not parse instrumentSpecifics parameters."
               )
             )
        _ <- Elab.env("instrumentSpecificsParams" -> x)
      } yield ()
    case (MutationType, "oiwfsTarget", List(Binding("target", ObjectValue(fields))))        =>
      for {
        x <-
          Elab.liftR(parseTargetInput(fields).toResult("Could not parse oiwfsTarget parameters."))
        _ <- Elab.env("target" -> x)
      } yield ()
    case (MutationType, "oiwfsProbeTracking", List(Binding("config", ObjectValue(fields)))) =>
      for {
        x <- Elab.liftR(
               parseTrackingInput(fields).toResult("Could not parse oiwfsProbeTracking parameters.")
             )
        _ <- Elab.env("config" -> x)
      } yield ()
    case (MutationType, "oiwfsFollow", List(Binding("enable", BooleanValue(en))))           =>
      Elab.env("enable" -> en)
    case (MutationType, "oiwfsObserve", List(Binding("period", ObjectValue(fields))))       =>
      for {
        x <- Elab.liftR(
               parseTimeSpan(fields).toResult(
                 "Could not parse oiwfsObserve parameters."
               )
             )
        _ <- Elab.env("period" -> x)
      } yield ()
    case (MutationType, "guideEnable", List(Binding("config", ObjectValue(fields))))        =>
      for {
        x <- Elab.liftR(
               parseGuideConfig(fields).toResult(
                 "Could not parse guideEnable parameters."
               )
             )
        _ <- Elab.env("config" -> x)
      } yield ()
  }

  override val typeMappings: List[TypeMapping] = List(
    ObjectMapping(
      tpe = QueryType,
      fieldMappings = List(
        RootEffect.computeEncodable("guideState")((p, env) => guideState(p, env)),
        RootEffect.computeEncodable("telescopeState")((p, env) => telescopeState(p, env))
      )
    ),
    ObjectMapping(
      tpe = MutationType,
      fieldMappings = List(
        RootEffect.computeEncodable("mountPark")((p, env) => mountPark(p, env)),
        RootEffect.computeEncodable("mountFollow")((p, env) => mountFollow(p, env)),
        RootEffect.computeEncodable("rotatorPark")((p, env) => rotatorPark(p, env)),
        RootEffect.computeEncodable("rotatorFollow")((p, env) => rotatorFollow(p, env)),
        RootEffect.computeEncodable("rotatorConfig")((p, env) => rotatorConfig(p, env)),
        RootEffect.computeEncodable("scsFollow")((p, env) => scsFollow(p, env)),
        RootEffect.computeEncodable("tcsConfig")((p, env) => tcsConfig(p, env)),
        RootEffect.computeEncodable("slew")((p, env) => slew(p, env)),
        RootEffect.computeEncodable("instrumentSpecifics")((p, env) => instrumentSpecifics(p, env)),
        RootEffect.computeEncodable("oiwfsTarget")((p, env) => oiwfsTarget(p, env)),
        RootEffect.computeEncodable("oiwfsProbeTracking")((p, env) => oiwfsProbeTracking(p, env)),
        RootEffect.computeEncodable("oiwfsPark")((p, env) => oiwfsPark(p, env)),
        RootEffect.computeEncodable("oiwfsFollow")((p, env) => oiwfsFollow(p, env)),
        RootEffect.computeEncodable("oiwfsObserve")((_, env) => oiwfsObserve(env)),
        RootEffect.computeEncodable("oiwfsStopObserve")((_, _) => oiwfsStopObserve),
        RootEffect.computeEncodable("guideEnable")((p, env) => guideEnable(p, env)),
        RootEffect.computeEncodable("guideDisable")((p, env) => guideDisable(p, env))
      )
    ),
    ObjectMapping(
      tpe = SubscriptionType,
      List(
        RootStream.computeCursor("logMessage") { (p, env) =>
          logTopic
            .subscribe(10)
            .map(_.asJson)
            .map(circeCursor(p, env, _))
            .map(Result.success)
        },
        RootStream.computeCursor("guideState") { (p, env) =>
          guideStateTopic
            .subscribe(10)
            .map(_.asJson)
            .map(circeCursor(p, env, _))
            .map(Result.success)
        },
        RootStream.computeCursor("guidersQualityValues") { (p, env) =>
          guidersQualityTopic
            .subscribe(10)
            .map(_.asJson)
            .map(circeCursor(p, env, _))
            .map(Result.success)
        },
        RootStream.computeCursor("telescopeState") { (p, env) =>
          telescopeStateTopic
            .subscribe(10)
            .map(_.asJson)
            .map(circeCursor(p, env, _))
            .map(Result.success)
        }
      )
    )
  )
}

object NavigateMappings extends GrackleParsers {

  def loadSchema[F[_]: Sync]: F[Result[Schema]] = SchemaStitcher
    .apply[F](JPath.of("NewTCC.graphql"), SourceResolver.fromResource(getClass.getClassLoader))
    .build

  def apply[F[_]: Sync](
    server:              NavigateEngine[F],
    logTopic:            Topic[F, ILoggingEvent],
    guideStateTopic:     Topic[F, GuideState],
    guidersQualityTopic: Topic[F, GuidersQualityValues],
    telescopeStateTopic: Topic[F, TelescopeState]
  ): F[NavigateMappings[F]] = loadSchema.flatMap {
    case Result.Success(schema)           =>
      new NavigateMappings[F](server,
                              logTopic,
                              guideStateTopic,
                              guidersQualityTopic,
                              telescopeStateTopic
      )(schema)
        .pure[F]
    case Result.Warning(problems, schema) =>
      new NavigateMappings[F](server,
                              logTopic,
                              guideStateTopic,
                              guidersQualityTopic,
                              telescopeStateTopic
      )(schema)
        .pure[F]
    case Result.Failure(problems)         =>
      Sync[F].raiseError[NavigateMappings[F]](
        new Throwable(
          s"Unable to load schema because: ${problems.map(_.message).toList.mkString(",")}"
        )
      )
    case Result.InternalError(error)      =>
      Sync[F].raiseError[NavigateMappings[F]](
        new Throwable(s"Unable to load schema because: ${error.getMessage}")
      )

  }

  def parseSlewOptionsInput(l: List[(String, Value)]): Option[SlewOptions] = for {
    zct  <-
      l.collectFirst { case ("zeroChopThrow", BooleanValue(v)) => v }.map(ZeroChopThrow.value(_))
    zso  <- l.collectFirst { case ("zeroSourceOffset", BooleanValue(v)) => v }
              .map(ZeroSourceOffset.value(_))
    zsdt <- l.collectFirst { case ("zeroSourceDiffTrack", BooleanValue(v)) => v }
              .map(ZeroSourceDiffTrack.value(_))
    zmo  <- l.collectFirst { case ("zeroMountOffset", BooleanValue(v)) => v }
              .map(ZeroMountOffset.value(_))
    zmdt <- l.collectFirst { case ("zeroMountDiffTrack", BooleanValue(v)) => v }
              .map(ZeroMountDiffTrack.value(_))
    stf  <- l.collectFirst { case ("shortcircuitTargetFilter", BooleanValue(v)) => v }
              .map(ShortcircuitTargetFilter.value(_))
    smf  <- l.collectFirst { case ("shortcircuitMountFilter", BooleanValue(v)) => v }
              .map(ShortcircuitMountFilter.value(_))
    rp   <-
      l.collectFirst { case ("resetPointing", BooleanValue(v)) => v }.map(ResetPointing.value(_))
    sg   <- l.collectFirst { case ("stopGuide", BooleanValue(v)) => v }.map(StopGuide.value(_))
    zgo  <- l.collectFirst { case ("zeroGuideOffset", BooleanValue(v)) => v }
              .map(ZeroGuideOffset.value(_))
    zio  <- l.collectFirst { case ("zeroInstrumentOffset", BooleanValue(v)) => v }
              .map(ZeroInstrumentOffset.value(_))
    ap1  <-
      l.collectFirst { case ("autoparkPwfs1", BooleanValue(v)) => v }.map(AutoparkPwfs1.value(_))
    ap2  <-
      l.collectFirst { case ("autoparkPwfs2", BooleanValue(v)) => v }.map(AutoparkPwfs2.value(_))
    ao   <-
      l.collectFirst { case ("autoparkOiwfs", BooleanValue(v)) => v }.map(AutoparkOiwfs.value(_))
    ag   <- l.collectFirst { case ("autoparkGems", BooleanValue(v)) => v }.map(AutoparkGems.value(_))
    aa   <-
      l.collectFirst { case ("autoparkAowfs", BooleanValue(v)) => v }.map(AutoparkAowfs.value(_))
  } yield SlewOptions(
    zct,
    zso,
    zsdt,
    zmo,
    zmdt,
    stf,
    smf,
    rp,
    sg,
    zgo,
    zio,
    ap1,
    ap2,
    ao,
    ag,
    aa
  )

  def parseSiderealTarget(
    name:         String,
    centralWavel: Option[Wavelength],
    l:            List[(String, Value)]
  ): Option[Target.SiderealTarget] = for {
    ra    <- l.collectFirst { case ("ra", ObjectValue(v)) => parseRightAscension(v) }.flatten
    dec   <- l.collectFirst { case ("dec", ObjectValue(v)) => parseDeclination(v) }.flatten
    epoch <- l.collectFirst { case ("epoch", StringValue(v)) => parseEpoch(v) }.flatten
  } yield Target.SiderealTarget(
    name,
    centralWavel,
    Coordinates(ra, dec),
    epoch,
    l.collectFirst { case ("properMotion", ObjectValue(v)) => parseProperMotion(v) }.flatten,
    l.collectFirst { case ("radialVelocity", ObjectValue(v)) => parseRadialVelocity(v) }.flatten,
    l.collectFirst { case ("parallax", ObjectValue(v)) => parseParallax(v) }.flatten
  )

  def parseNonSiderealTarget(
    name: String,
    w:    Option[Wavelength],
    l:    List[(String, Value)]
  ): Option[Target.SiderealTarget] = none

  def parseEphemerisTarget(
    name: String,
    w:    Option[Wavelength],
    l:    List[(String, Value)]
  ): Option[Target.EphemerisTarget] = none

  def parseTargetInput(l: List[(String, Value)]): Option[Target] = for {
    nm <- l.collectFirst { case ("name", StringValue(v)) => v }
    wv <- l.collectFirst { case ("wavelength", ObjectValue(v)) => parseWavelength(v) } match {
            case Some(None) => None
            case None       => Some(None)
            case x          => x
          }
    bt <- l.collectFirst { case ("sidereal", ObjectValue(v)) => v }
            .flatMap[Target](parseSiderealTarget(nm, wv, _))
            .orElse(
              l.collectFirst { case ("nonsidereal", ObjectValue(v)) => v }
                .flatMap(parseNonSiderealTarget(nm, wv, _))
            )
  } yield bt

  def parseGuideTargetInput(l: List[(String, Value)]): Option[Target] = for {
    nm <- l.collectFirst { case ("name", StringValue(v)) => v }
    bt <- l.collectFirst { case ("sidereal", ObjectValue(v)) => v }
            .flatMap[Target](parseSiderealTarget(nm, None, _))
            .orElse(
              l.collectFirst { case ("nonsidereal", ObjectValue(v)) => v }
                .flatMap(parseNonSiderealTarget(nm, None, _))
            )
  } yield bt

  def parseTrackingInput(l: List[(String, Value)]): Option[TrackingConfig] = for {
    aa <- l.collectFirst { case ("nodAchopA", BooleanValue(v)) => v }
    ab <- l.collectFirst { case ("nodAchopB", BooleanValue(v)) => v }
    ba <- l.collectFirst { case ("nodBchopA", BooleanValue(v)) => v }
    bb <- l.collectFirst { case ("nodBchopB", BooleanValue(v)) => v }
  } yield TrackingConfig(aa, ab, ba, bb)

  def parseOrigin(l: List[(String, Value)]): Option[Origin] = for {
    x <- l.collectFirst { case ("x", ObjectValue(v)) => parseDistance(v) }.flatten
    y <- l.collectFirst { case ("y", ObjectValue(v)) => parseDistance(v) }.flatten
  } yield Origin(x, y)

  def parseInstrumentSpecificsInput(l: List[(String, Value)]): Option[InstrumentSpecifics] = for {
    iaa       <- l.collectFirst { case ("iaa", ObjectValue(v)) => parseAngle(v) }.flatten
    focOffset <- l.collectFirst { case ("focusOffset", ObjectValue(v)) => parseDistance(v) }.flatten
    agName    <- l.collectFirst { case ("agName", StringValue(v)) => v }
    origin    <- l.collectFirst { case ("origin", ObjectValue(v)) => parseOrigin(v) }.flatten
  } yield InstrumentSpecifics(
    iaa,
    focOffset,
    agName,
    origin
  )

  def parseGuiderConfig(l: List[(String, Value)]): Option[GuiderConfig] = for {
    target   <- l.collectFirst { case ("target", ObjectValue(v)) => parseGuideTargetInput(v) }.flatten
    tracking <- l.collectFirst { case ("tracking", ObjectValue(v)) =>
                  parseTrackingInput(v)
                }.flatten
  } yield GuiderConfig(target, tracking)

  def parseRotatorConfig(l: List[(String, Value)]): Option[RotatorTrackConfig] = for {
    ipa  <- l.collectFirst { case ("ipa", ObjectValue(v)) => parseAngle(v) }.flatten
    mode <- l.collectFirst { case ("mode", EnumValue(v)) =>
              parseEnumerated[RotatorTrackingMode](v)
            }.flatten
  } yield RotatorTrackConfig(ipa, mode)

  def parseTcsConfigInput(l: List[(String, Value)]): Option[TcsConfig] = for {
    t   <- l.collectFirst { case ("sourceATarget", ObjectValue(v)) => parseTargetInput(v) }.flatten
    inp <- l.collectFirst { case ("instParams", ObjectValue(v)) =>
             parseInstrumentSpecificsInput(v)
           }.flatten
    oi  <-
      l.collectFirst { case ("oiwfs", ObjectValue(v)) => parseGuiderConfig(v) } match {
        case Some(None) => None
        case None       => Some(None)
        case x          => x
      }
    rc  <- l.collectFirst { case ("rotator", ObjectValue(v)) => parseRotatorConfig(v) }.flatten
    ins <- l.collectFirst { case ("instrument", EnumValue(v)) =>
             parseEnumerated[Instrument](v)
           }.flatten
  } yield TcsConfig(t, inp, oi, rc, ins)

  def parseProbeGuide(l: List[(String, Value)]): Option[ProbeGuide] = for {
    f <- l.collectFirst { case ("from", EnumValue(v)) => parseEnumerated[GuideProbe](v) }.flatten
    t <- l.collectFirst { case ("to", EnumValue(v)) => parseEnumerated[GuideProbe](v) }.flatten
  } yield ProbeGuide(f, t)

  def parseGuideConfig(l: List[(String, Value)]): Option[TelescopeGuideConfig] = {
    val m2: List[TipTiltSource] = l.collectFirst { case ("m2Inputs", ListValue(v)) =>
      v.collect { case EnumValue(v) => parseEnumerated[TipTiltSource](v) }.flattenOption
    }.orEmpty

    val m1: Option[M1Source] = l.collectFirst { case ("m1Input", EnumValue(v)) =>
      parseEnumerated[M1Source](v)
    }.flatten

    val coma = l.collectFirst { case ("m2Coma", BooleanValue(v)) => v }.exists(identity)

    val dayTimeMode = l.collectFirst { case ("daytimeMode", BooleanValue(v)) => v }.exists(identity)

    val probeGuide =
      l.collectFirst { case ("probeGuide", ObjectValue(v)) => parseProbeGuide(v) }.flatten

    l.collectFirst { case ("mountOffload", BooleanValue(v)) => v }
      .map { mount =>
        TelescopeGuideConfig(
          MountGuideOption.fromBoolean(mount),
          m1.map(M1GuideConfig.M1GuideOn(_)).getOrElse(M1GuideConfig.M1GuideOff),
          m2.isEmpty.fold(
            M2GuideConfig.M2GuideOff,
            M2GuideConfig.M2GuideOn(ComaOption.fromBoolean(coma && m1.isDefined), m2.toSet)
          ),
          Some(dayTimeMode),
          probeGuide
        )
      }
  }

}
