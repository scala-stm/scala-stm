def projectVersion  = "0.9.1"
def mimaVersion     = "0.9"

lazy val root = project.in(file("."))
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    name                  := "scala-stm",
    mimaPreviousArtifacts := Set(organization.value %% name.value % mimaVersion)
  )

////////////////////
// basic settings //
////////////////////

lazy val commonSettings = Seq(
  organization       := "org.scala-stm",
  version            := projectVersion,
  description        := "A library for Software Transactional Memory in Scala",
  scalaVersion       := "2.12.8",
  crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.0"),
  scalacOptions     ++= Seq("-deprecation", "-unchecked", "-feature", "-Xsource:2.13"),
  scalacOptions     ++= {
    if (scalaVersion.value.startsWith("2.11")) Nil else Seq("-Xlint:-unused,_")
  },
  javacOptions in (Compile, compile) ++= {
    val javaVersion = if (scalaVersion.value.startsWith("2.11")) "1.6" else "1.8"
    Seq("-source", javaVersion, "-target", javaVersion)
  },
  libraryDependencies += {
    "org.scalatest" %% "scalatest" % "3.0.8" % Test,
  },
  libraryDependencies ++= Seq(
    "junit"         % "junit"       % "4.12"      % Test
  ),
  // skip exhaustive tests
  testOptions += Tests.Argument("-l", "slow"),
  // test of TxnExecutor.transformDefault must be run by itself
  parallelExecution in Test := false,
  unmanagedSourceDirectories in Compile += {
    val sourceDir = (sourceDirectory in Compile).value
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, n)) if n >= 13 => sourceDir / "scala-2.13+"
      case _                       => sourceDir / "scala-2.13-"
    }
  }
)

////////////////
// publishing //
////////////////

lazy val publishSettings = Seq(
  homepage := Some(url("https://nbronson.github.com/scala-stm/")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/scala-stm/scala-stm"),
      "scm:git:git@github.com:scala-stm/scala-stm.git"
    )
  ),
  licenses := Seq("""BSD 3-Clause "New" or "Revised" License""" -> url("https://spdx.org/licenses/BSD-3-Clause")),
  developers := List(
    Developer(
      "nbronson",
      "Nathan Bronson",
      "ngbronson@gmail.com",
      url("https://github.com/nbronson")
    )
  ),
  publishMavenStyle := true,
  publishTo := {
    val base = "https://oss.sonatype.org"
    Some(if (isSnapshot.value)
      "snapshots" at s"$base/content/repositories/snapshots/"
    else
      "releases"  at s"$base/service/local/staging/deploy/maven2/"
    )
  },
  // exclude scalatest from the Maven POM
  pomPostProcess := { xi: scala.xml.Node =>
    import scala.xml._
    val badDeps = (xi \\ "dependency").filter {
      x => (x \ "artifactId").text != "scala-library"
    } .toSet
    def filt(root: Node): Node = root match {
      case x: Elem =>
        val ch = x.child.filter(!badDeps(_)).map(filt)
        Elem(x.prefix, x.label, x.attributes, x.scope, ch.isEmpty, ch: _*)

      case x => x
    }
    filt(xi)
  },
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
)
