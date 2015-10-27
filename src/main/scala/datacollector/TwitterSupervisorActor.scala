package datacollector

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.SupervisorStrategy.{ Stop, Escalate, Resume, Restart }
import akka.actor.{ OneForOneStrategy, Actor, Props }
import akka.event.Logging
import akka.pattern.gracefulStop

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A supervisor actor for maintaining the whole operation. Allows graceful stop and error handling.
 *
 * @author Emre Çelikten
 */
class TwitterSupervisorActor(outputPath: File, keywords: Option[List[String]]) extends Actor {
  private val logger = Logging(context.system, this)

  private val saverActor = {
    val filePrefix = "twitter-raw"
    context.actorOf(SaverActor.props(filePrefix), "twitter-raw-saver")
  }

  private val processorRouter = context.actorOf(TwitterProcessorRouter.props(saverActor.path), "processor-router")
  private val downloaderActor = context.actorOf(TwitterDownloaderActor.props(processorRouter.path), "downloader")
  private val emailerActor = context.actorOf(Props[EmailerActor], "emailer")
  private val heartbeatActor = context.actorOf(HeartBeatActor.props(Map(downloaderActor.path -> "TwitterDownloaderActor")))

  override def receive: Receive = {
    case TwitterSupervisorActor.Start =>
      logger.info(s"Supervisor received start event.")
      downloaderActor ! TwitterDownloaderActor.Start(keywords)
      heartbeatActor ! HeartBeatActor.Start

    case TwitterSupervisorActor.Stop =>
      logger.info(s"Supervisor received stop event. Shutting down downloader...")

      val downloaderFuture = gracefulStop(downloaderActor, FiniteDuration(30, TimeUnit.SECONDS), TwitterDownloaderActor.Stop)
      Await.ready(downloaderFuture, FiniteDuration(35, TimeUnit.SECONDS))

      logger.info("Shutting down processors...")
      val processorFuture = gracefulStop(processorRouter, FiniteDuration(60, TimeUnit.SECONDS))
      Await.ready(processorFuture, FiniteDuration(65, TimeUnit.SECONDS))

      logger.info("Shutting down savers...")
      val saverFuture = gracefulStop(saverActor, FiniteDuration(30, TimeUnit.SECONDS))

      Await.ready(saverFuture, FiniteDuration(35, TimeUnit.SECONDS))

      // Attempt to shutdown ActorSystem
      logger.info("Attempting to shut down actor system...")
      context.system.shutdown()

    case TwitterSupervisorActor.WriteError(ex) =>
    // TODO: Handle

    case other =>
      logger.warning(s"Invalid message received from ${sender()}:\n$other")
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5 minute) {
      case _: Exception => Restart
    }
}

object TwitterSupervisorActor {

  def props(outputPath: File, keywords: Option[List[String]] = None): Props = Props(new TwitterSupervisorActor(outputPath, keywords))

  sealed trait SupervisorMessage

  case class WriteError(ex: Exception) extends SupervisorMessage

  case object Start extends SupervisorMessage

  case object Stop extends SupervisorMessage

}