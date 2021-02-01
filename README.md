[![Build Status](https://github.com/scala-stm/scala-stm/workflows/Scala%20CI/badge.svg?branch=main)](https://github.com/scala-stm/scala-stm/actions?query=workflow%3A%22Scala+CI%22)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.scala-stm/scala-stm_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.scala-stm/scala-stm_2.13)

# ScalaSTM
 
ScalaSTM is a lightweight software transactional memory for Scala,
inspired by the STMs in Haskell and Clojure. It was written by Nathan Bronson
and the Scala STM Expert Group, and it is published under a
BSD 3-Clause License.

ScalaSTM is available on the JVM for Scala 2.13, 2.12, 2.11, and Dotty (JVM), and on JS for Scala 2.13.
It is also possible to use the library from Java, see `JavaAPITests`.

You can use it in your sbt build file as follows:

```scala
libraryDependencies += "org.scala-stm" %% "scala-stm" % "0.11.0"
```

Use `%%%` for Scala.js.

Or in Maven:

```
<dependency>
  <groupId>org.scala-stm</groupId>
  <artifactId>scala-stm_2.13</artifactId>
  <version>0.11.0</version>
</dependency>
```

For download info and documentation see http://scala-stm.org

For the design of the library, see the paper

- Bronson, N. G., Chafi, H., \& Olukotun, K. [CCSTM: A Library-Based STM for Scala](http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.220.1995).
  _Proceedings of the First Annual Scala Workshop_. Lausanne, 2010
