// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.server.tcs

import eu.timepit.refined.types.string.NonEmptyString
import lucuma.core.util.NewType

object TcsTop extends NewType[NonEmptyString]
type TcsTop = TcsTop.Type
object Pwfs1Top extends NewType[NonEmptyString]
type Pwfs1Top = Pwfs1Top.Type
object Pwfs2Top extends NewType[NonEmptyString]
type Pwfs2Top = Pwfs2Top.Type
object OiwfsTop extends NewType[NonEmptyString]
type OiwfsTop = OiwfsTop.Type
object AgTop extends NewType[NonEmptyString]
type AgTop = AgTop.Type
