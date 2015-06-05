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

  val system = ActorSystem("foursquare-data-collector")

  def main(args: Array[String]) {
    val outputPath = new File(Configuration.conf.getString("outputPath"))

    if (!outputPath.exists) {
      try {
        outputPath.mkdirs()
      } catch {
        case ex: Exception => logger.error("Error while creating directories for the output path.\n" + ex.getStackTrace.mkString)
      }
    }

    if (!outputPath.canWrite) {
      logger.error(s"Cannot write to the output path '$outputPath' specified at application configuration! Exiting...")
    } else {
      logger.info("All is well regarding file permissions, starting up the system...")

      val keywords = List("swarmapp com c")

      val supervisorActor = system.actorOf(TwitterSupervisorActor.props(outputPath, Some(keywords)))

      logger.info("Starting supervisor actor...")

      supervisorActor ! Start

      println("Type exit to quit.")
      val waitingTime = FiniteDuration(120, TimeUnit.SECONDS)
      implicit val timeout: Timeout = Timeout(waitingTime)

      for (ln <- io.Source.stdin.getLines()) {
        if (ln.compareTo("exit") == 0) {
          logger.info("Exit command received from user, starting graceful stop.")
          val f = supervisorActor ? Stop
          Await.result(f, waitingTime)
          System.exit(0)
        }
      }
    }
  }
}
