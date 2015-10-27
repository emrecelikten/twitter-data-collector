package datacollector

import java.io.File

import akka.actor.{ ActorRef, ActorSystem }
import akka.util.Timeout
import datacollector.TwitterSupervisorActor.{ Start, Stop }

import scala.concurrent.duration._

/**
 * Entry object. Starts the actor system and actors.
 * @author Emre Çelikten
 */
object Application {
  var actorSystem: ActorSystem = _

  def shutdownEverything(supervisorActor: ActorRef): Unit = {
    supervisorActor ! Stop
    // TODO: Get this from conf
    actorSystem.awaitTermination(2.minutes)
    Logger.info("Shutting down Redis ActorSystem.")
  }

  def main(args: Array[String]) {
    if (!Configuration.init(sys.props.get("config").getOrElse("application.conf"))) {
      println("Error while initializing configuration. Exiting...")
    } else {
      val outputPath = new File(Configuration.configuration.outputPath)

      if (!outputPath.exists) {
        try {
          outputPath.mkdirs()
        } catch {
          case ex: Exception =>
            println("Error while creating directories for the output path. Please check file permissions. Exiting...\n" +
              Utils.getStackTraceString(ex))
        }
      } else if (!outputPath.canWrite) {
        println(s"Cannot write to the output path '$outputPath' specified at application configuration! Exiting...")
      } else {
        println("All is well regarding file permissions, starting up the system...")

        val keywords = Configuration.configuration.keywords

        actorSystem = ActorSystem("foursquare-data-collector")

        val supervisorActor = actorSystem.actorOf(TwitterSupervisorActor.props(outputPath, keywords), "supervisor")

        Logger.debug("Starting supervisor actor...")

        supervisorActor ! Start

        sys.addShutdownHook(shutdownEverything(supervisorActor))

        println("System active. Kill the process to initiate graceful shutdown. Forcibly killing the application (e.g. using kill -9) might cause data loss.")
      }
    }
  }
}

