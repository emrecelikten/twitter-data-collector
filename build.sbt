name := "FoursquareDataCollector"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.6"

libraryDependencies ++= List(
  "com.twitter" % "hbc-core" % "2.2.0", // Twitter API library
  "com.typesafe" % "config" % "1.2.1", // For reading configurations
  "org.slf4j" % "slf4j-simple" % "1.7.12", // Logging dependency, required
  "com.typesafe.akka" %% "akka-actor" % "2.3.11", // Akka library for actors
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test" // Testing
)

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(preserveSpaceBeforeArguments, true)
