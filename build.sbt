import scalariform.formatter.preferences.PreserveSpaceBeforeArguments

name := "FoursquareDataCollector"

version := "0.2-SNAPSHOT"

scalaVersion := "2.11.6"

libraryDependencies ++= List(
  "com.twitter" % "hbc-core" % "2.2.0", // Twitter API library
  "com.typesafe" % "config" % "1.2.1", // For reading configurations
  "ch.qos.logback" % "logback-classic" % "1.1.3", // Logging dependency, required
  "com.typesafe.akka" %% "akka-actor" % "2.3.11", // Akka library for actors
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.11", // Akka library for logging
  "io.spray" %% "spray-client" % "1.3.3", // HTTP client
  "com.typesafe.play" % "play-json_2.11" % "2.4.0", // JSON library
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test", // Testing framework
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % "test", // Akka testing library
  "org.mockito" % "mockito-core" % "2.0.13-beta" % "test" // Mocking library

)

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(PreserveSpaceBeforeArguments, true)
