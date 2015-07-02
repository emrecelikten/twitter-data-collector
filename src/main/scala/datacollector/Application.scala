package datacollector

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import datacollector.TwitterSupervisorActor.{ Start, Stop }

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration

/**
 * Entry object. Starts the actor system and actors.
 * @author Emre Çelikten
 */
object Application {
  var actorSystem: ActorSystem = _

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

        println("Type exit to quit.")
        val waitingTime = Configuration.configuration.gracefulShutdownDuration
        implicit val timeout: Timeout = Timeout(waitingTime)

        for (ln <- io.Source.stdin.getLines()) {
          if (ln.compareTo("exit") == 0) {
            println("Exit command received from user, starting graceful stop.")
            val f = supervisorActor ? Stop
            Await.result(f, waitingTime)
            System.exit(0)
          }
        }
      }
    }
  }
}
