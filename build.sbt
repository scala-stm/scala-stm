def projectVersion  = "0.11.0"
def mimaVersion     = "0.11.0"

// sonatype plugin requires that these are in global
ThisBuild / version      := projectVersion
ThisBuild / organization := "org.scala-stm"

lazy val root = crossProject(JVMPlatform, JSPlatform).in(file("."))
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    name                  := "scala-stm",
    mimaPreviousArtifacts := Set(organization.value %% name.value % mimaVersion)
  )
  .jvmSettings(
    crossScalaVersions    := Seq("3.0.0-M3", "2.13.4", "2.12.12", "2.11.12"),
  )
  .jsSettings(
    crossScalaVersions    := scalaVersion.value :: Nil,
  )

////////////////////
// basic settings //
////////////////////

lazy val deps = new {
  val test = new {
    val junit         = "4.13.1"
    val scalaTest     = "3.2.3"
    val scalaTestPlus = s"$scalaTest.0"
  }
}

lazy val commonSettings = Seq(
//  organization       := "org.scala-stm",
//  version            := projectVersion,
  description        := "A library for Software Transactional Memory in Scala",
  homepage           := Some(url("https://nbronson.github.com/scala-stm/")),
  licenses           := Seq("""BSD 3-Clause "New" or "Revised" License""" -> url("https://spdx.org/licenses/BSD-3-Clause")),
  scalaVersion       := "2.13.3",
  scalacOptions     ++= Seq("-deprecation", "-unchecked", "-feature", "-Xsource:2.13"),
  scalacOptions     ++= {
    if (scalaVersion.value.startsWith("2.11")) Nil else Seq("-Xlint:-unused,_")
  },
  javacOptions in (Compile, compile) ++= {
    val javaVersion = if (scalaVersion.value.startsWith("2.11")) "1.6" else "1.8"
    Seq("-source", javaVersion, "-target", javaVersion)
  },
  libraryDependencies ++= Seq(
    "org.scalatest"     %%% "scalatest"  % deps.test.scalaTest     % Test,
    "org.scalatestplus" %%  "junit-4-13" % deps.test.scalaTestPlus % Test,
    "junit"             %   "junit"      % deps.test.junit         % Test,
  ),
  // skip exhaustive tests
  testOptions += Tests.Argument("-l", "slow"),
  // test of TxnExecutor.transformDefault must be run by itself
  parallelExecution in Test := false,
  unmanagedSourceDirectories in Compile ++= {
    val sourceDirPl = (sourceDirectory in Compile).value
    val sourceDirSh = file(
      sourceDirPl.getPath.replace("/jvm/" , "/shared/").replace("/js/", "/shared/")
    )
    val sv = CrossVersion.partialVersion(scalaVersion.value)
    val (sub1, sub2) = sv match {
      case Some((2, n)) if n >= 13  => ("scala-2.13+", "scala-2.14-")
      case Some((3, _))             => ("scala-2.13+", "scala-2.14+")
      case _                        => ("scala-2.13-", "scala-2.14-")
    }
    Seq(sourceDirPl / sub1, sourceDirPl / sub2, sourceDirSh / sub1, sourceDirSh / sub2)
  },
  unmanagedSourceDirectories in Test ++= {
    val sourceDirPl = (sourceDirectory in Test).value
    val sourceDirSh = file(
      sourceDirPl.getPath.replace("/jvm/" , "/shared/").replace("/js/", "/shared/")
    )
    val sv = CrossVersion.partialVersion(scalaVersion.value)
    val (sub1, sub2) = sv match {
      case Some((2, n)) if n >= 13  => ("scala-2.13+", "scala-2.14-")
      case Some((3, _))             => ("scala-2.13+", "scala-2.14+")
      case _                        => ("scala-2.13-", "scala-2.14-")
    }
    Seq(sourceDirPl / sub1, sourceDirPl / sub2, sourceDirSh / sub1, sourceDirSh / sub2)
  },
)

////////////////
// publishing //
////////////////

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  developers := List(
    Developer(
      id    = "nbronson",
      name  = "Nathan Bronson",
      email = "ngbronson@gmail.com",
      url   = url("https://github.com/nbronson")
    ),
    Developer(
      id    = "sciss",
      name  = "Hanns Holger Rutz",
      email = "contact@sciss.de",
      url   = url("https://www.sciss.de")
    )
  ),
  scmInfo := {
    val h = "github.com"
    val a = s"scala-stm/${name.value}"
    Some(ScmInfo(url(s"https://$h/$a"), s"scm:git@$h:$a.git"))
  },
)

