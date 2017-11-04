---
layout: default
title: Snapshot releases
---

There's currently no SNAPSHOT release, as all changes on master have
been incorporated into the 0.8 release.

Snapshot releases deployed to the `oss.sonatype.org` repository are
fully tested and functional, but may have changing APIs.

SBT
---

Tell `sbt` about a dependency on ScalaSTM by adding a library dependency
to your `build.sbt` file (or a Scala build file). You will also need to
add a resolver that can find the `oss.sonatype.org` snapshot repository.

{% highlight scala %}
libraryDependencies += ("org.scala-stm" %% "scala-stm" % "0.8-SNAPSHOT")

resolvers += ("snapshots" at
    "https://oss.sonatype.org/content/repositories/snapshots")
{% endhighlight %}

`sbt update` will download the latest `scala-stm` snapshot JAR and make
it available for building.

Maven2 {#maven}
------

The ScalaSTM snapshot dependency for your `pom.xml` is

{% highlight xml %}
<dependencies>
  <dependency>
    <groupId>org.scala-stm</groupId>
    <artifactId>scala-stm_2.10</artifactId>
    <version>0.8-SNAPSHOT</version>
  </dependency>
</dependencies>
{% endhighlight %}

Make sure you have the `oss.sonatype.org` snapshot repository available

{% highlight xml %}
<repositories>
  <repository>
    <id>oss.sonatype.org.snapshots</id>
    <name>OSS Sonatype Snapshot Repository</name>
    <url>https://oss.sonatype.org/content/repositories/snapshots</url>
    <snapshots/>
  </repository>
</repositories>
{% endhighlight %}