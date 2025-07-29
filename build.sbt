ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .settings(
    name := "FlinkLite"
  )
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.6.20",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.20"
)
libraryDependencies += "com.lihaoyi" %% "ujson" % "3.1.3"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream-kafka" % "3.0.1",
  "com.lihaoyi" %% "upickle" % "3.1.3"
)
libraryDependencies ++= Seq(
  "io.prometheus" % "simpleclient" % "0.16.0",
  "io.prometheus" % "simpleclient_hotspot" % "0.16.0",
  "io.prometheus" % "simpleclient_httpserver" % "0.16.0"
)
libraryDependencies += "org.apache.kafka" % "kafka-clients" % "3.5.1"
