def projectVersion  = "0.11.0"
def mimaVersion     = "0.11.0"

lazy val root = crossProject(JVMPlatform, JSPlatform).in(file("."))
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    name                  := "scala-stm",
    mimaPreviousArtifacts := Set(organization.value %% name.value % mimaVersion)
  )
  .jvmSettings(
    crossScalaVersions    := Seq("3.0.0-M1", "2.13.4", "2.12.12", "2.11.12"),
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
  organization       := "org.scala-stm",
  version            := projectVersion,
  description        := "A library for Software Transactional Memory in Scala",
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
    def filter(root: Node): Node = root match {
      case x: Elem =>
        val ch = x.child.filter(!badDeps(_)).map(filter)
        Elem(x.prefix, x.label, x.attributes, x.scope, ch.isEmpty, ch: _*)

      case x => x
    }
    filter(xi)
  },
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
)
