import Settings.Libraries
import Settings.Plugins
import Common._

lazy val attoVersion                 = "0.8.0"
lazy val catsVersion                 = "2.1.1"
lazy val collCompatVersion           = "2.1.6"
lazy val kindProjectorVersion        = "0.10.3"
lazy val monocleVersion              = "2.0.5"
lazy val catsTestkitScalaTestVersion = "1.0.1"
lazy val scalaJavaTimeVersion        = "2.0.0"
lazy val jtsVersion                  = "0.0.9"
lazy val svgdotjsVersion             = "0.0.1"

inThisBuild(Seq(
  homepage := Some(url("https://github.com/gemini-hlsw/engage")),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % kindProjectorVersion),
  Global / onChangedBuildSource := ReloadOnSourceChanges
) ++ gspPublishSettings)

// Gemini repository
ThisBuild / resolvers ++= Seq(
  "Gemini Repository".at("https://github.com/gemini-hlsw/maven-repo/raw/master/releases"),
  "JCenter".at("https://jcenter.bintray.com/")
)


publish / skip := true

lazy val epics = project
  .in(file("modules/epics"))
  .settings(
    name := "epics",
    libraryDependencies ++= Seq(
      Libraries.Cats.value,
      Libraries.CatsEffect.value,
      Libraries.Mouse.value,
      Libraries.Fs2,
      Libraries.EpicsCA,
      Libraries.EpicsJCA % Test,
      Libraries.LucumaCore.value
    ) ++ Libraries.MUnit.value,
    Test / parallelExecution := false
  )
