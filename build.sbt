import scalariform.formatter.preferences.PreserveSpaceBeforeArguments

enablePlugins(JavaAppPackaging)

name := "FoursquareDataCollector"

version := "0.3-SNAPSHOT"

scalaVersion := "2.11.7"
mainClass in Compile := Some("datacollector.Application")

libraryDependencies ++= List(
  "com.twitter" % "hbc-core" % "2.2.0", // Twitter API library
  "com.typesafe" % "config" % "1.2.1", // For reading configurations
  "ch.qos.logback" % "logback-classic" % "1.1.3", // Logging dependency, required
  "com.typesafe.akka" %% "akka-actor" % "2.3.11", // Akka library for actors
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.11", // Akka library for logging
  "io.spray" %% "spray-client" % "1.3.3", // HTTP client
  "org.apache.commons" % "commons-email" % "1.4", // Emailer for errors
  "com.typesafe.play" % "play-json_2.11" % "2.4.0", // JSON library
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test", // Testing framework
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % "test", // Akka testing library
  "org.mockito" % "mockito-core" % "2.0.13-beta" % "test" // Mocking library

)

maintainer := "Emre Çelikten"
packageSummary := "Foursquare data collector"
packageDescription := "Collects data from Twitter streaming API for Swarm checkins."

mappings in Universal <++= (packageBin in Compile, sourceDirectory ) map { (_, src) =>
  // we are using the reference.conf as default application.conf
  // the user can override settings here
  val conf = src / "main" / "resources" / "application.conf"
  val logConf = src / "main" / "resources" / "logback.xml"

  Seq(logConf -> "conf/logback.xml", conf -> "conf/application.conf")
}

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(PreserveSpaceBeforeArguments, true)
