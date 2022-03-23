// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package engage.web.client.model

import cats.Eq
import lucuma.core.util.Enumerated
import monocle.macros.Lenses
import engage.common.FixedLengthBuffer
import engage.model.security.UserDetails
import engage.web.client.model.SectionVisibilityState._
import engage.web.client.circuit.UserLoginFocus
import monocle.Getter
import monocle.Lens

sealed trait SoundSelection extends Product with Serializable

object SoundSelection {
  case object SoundOn  extends SoundSelection
  case object SoundOff extends SoundSelection

  /** @group Typeclass Instances */
  implicit val SoundSelectionEnumerated: Enumerated[SoundSelection] =
    Enumerated.of(SoundOn, SoundOff)

  def flip: SoundSelection => SoundSelection = {
    case SoundSelection.SoundOn  => SoundSelection.SoundOff
    case SoundSelection.SoundOff => SoundSelection.SoundOn
  }
}

/**
 * UI model, changes here will update the UI
 */
@Lenses
final case class EngageUIModel(
  navLocation:  Pages.EngagePages,
  user:         Option[UserDetails],
  displayNames: Map[String, String],
  loginBox:     SectionVisibilityState,
  globalLog:    GlobalLog,
  sound:        SoundSelection,
  firstLoad:    Boolean
)

object EngageUIModel {
  val Initial: EngageUIModel = EngageUIModel(
    Pages.Root,
    None,
    Map.empty,
    SectionClosed,
    GlobalLog(FixedLengthBuffer.unsafeFromInt(500), SectionClosed),
    SoundSelection.SoundOn,
    firstLoad = true
  )

  val userLoginFocus: Lens[EngageUIModel, UserLoginFocus] =
    Lens[EngageUIModel, UserLoginFocus](m => UserLoginFocus(m.user, m.displayNames))(n =>
      a => a.copy(user = n.user, displayNames = n.displayNames)
    )

  implicit val eq: Eq[EngageUIModel] =
    Eq.by(x =>
      (x.navLocation, x.user, x.displayNames, x.loginBox, x.globalLog, x.sound, x.firstLoad)
    )

  val displayNameG: Getter[EngageUIModel, Option[String]] =
    Getter[EngageUIModel, Option[String]](x => x.user.flatMap(r => x.displayNames.get(r.username)))

}
