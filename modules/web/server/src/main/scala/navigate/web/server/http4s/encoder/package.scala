// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.web.server.http4s

import ch.qos.logback.classic.spi.ILoggingEvent
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.*
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import lucuma.core.util.Timestamp
import navigate.server.tcs.GuideState
import navigate.server.tcs.GuidersQualityValues

package object encoder {

  given Encoder[ILoggingEvent] = e =>
    Json.obj(
      "timestamp" -> Timestamp.fromInstant(e.getInstant).getOrElse(Timestamp.Min).asJson,
      "level"     -> e.getLevel.toString.asJson,
      "thread"    -> e.getThreadName.asJson,
      "message"   -> e.getFormattedMessage.asJson
    )

  given Encoder[GuideState] = s => {
    val m2fields: List[(String, Json)] = s.m2Guide match {
      case M2GuideConfig.M2GuideOff               => List.empty
      case M2GuideConfig.M2GuideOn(coma, sources) =>
        List(
          "m2Inputs" -> sources.asJson,
          "m2Coma"   -> coma.asJson
        )
    }
    val m1field: List[(String, Json)]  = s.m1Guide match {
      case M1GuideConfig.M1GuideOff        => List.empty
      case M1GuideConfig.M1GuideOn(source) => List("m1Input" -> source.asJson)
    }

    Json.fromFields(
      m2fields ++ m1field ++ List(
        "mountOffload"  -> s.mountOffload.asJson,
        "p1Integrating" -> s.p1Integrating.asJson,
        "p2Integrating" -> s.p2Integrating.asJson,
        "oiIntegrating" -> s.oiIntegrating.asJson
      )
    )
  }

  given Encoder[GuidersQualityValues.GuiderQuality] = s =>
    Json.obj(
      "flux"             -> s.flux.asJson,
      "centroidDetected" -> s.centroidDetected.asJson
    )

  given Encoder[GuidersQualityValues] = s =>
    Json.obj(
      "pwfs1" -> s.pwfs1.asJson,
      "pwfs2" -> s.pwfs2.asJson,
      "oiwfs" -> s.oiwfs.asJson
    )

}
