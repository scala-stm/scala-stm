
name               := "scala-stm"
organization       := "org.scala-stm"
version            := "0.9.0+2.13.0-M4"
def mimaVersion     = "0.8"
scalaVersion       := "2.12.4"
crossScalaVersions := Seq("2.11.12", "2.12.4", "2.13.0-M4")
scalacOptions     ++= Seq("-deprecation", "-feature")

javacOptions in (Compile, compile) ++= {
  val javaVersion = if (scalaVersion.value.startsWith("2.11")) "1.6" else "1.8"
  Seq("-source", javaVersion, "-target", javaVersion)
}

libraryDependencies += {
  val v = if (scalaVersion.value == "2.13.0-M4") "3.0.6-SNAP1" else "3.0.5"
  "org.scalatest" %% "scalatest" % v % Test
}

libraryDependencies += ("junit" % "junit" % "4.12" % Test)

unmanagedSourceDirectories in Compile += {
  val sourceDir = (sourceDirectory in Compile).value
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, n)) if n >= 13 => sourceDir / "scala-2.13+"
    case _                       => sourceDir / "scala-2.13-"
  }
}

mimaPreviousArtifacts := Set(organization.value %% name.value % mimaVersion)

// skip exhaustive tests
testOptions += Tests.Argument("-l", "slow")

// test of TxnExecutor.transformDefault must be run by itself
parallelExecution in Test := false

////////////////////
// publishing

homepage := Some(url("https://nbronson.github.com/scala-stm/"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/scala-stm/scala-stm"),
    "scm:git:git@github.com:scala-stm/scala-stm.git"
  )
)

licenses := Seq("""BSD 3-Clause "New" or "Revised" License""" -> url("https://spdx.org/licenses/BSD-3-Clause"))

developers := List(
  Developer(
    "nbronson",
    "Nathan Bronson",
    "ngbronson@gmail.com",
    url("https://github.com/nbronson")
  )
)

publishMavenStyle := true

publishTo := {
    val base = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at base + "content/repositories/snapshots/")
    else
      Some("releases" at base + "service/local/staging/deploy/maven2/")
  }

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
  }

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
