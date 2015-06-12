package datacollector

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, Props }
import akka.event.Logging
import akka.pattern.gracefulStop
import datacollector.TwitterSupervisorActor.{ Start, Stop, WriteError }

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration

/**
 * A supervisor actor for maintaining the whole operation. Allows graceful stop and error handling.
 *
 * @author Emre Çelikten
 */
class TwitterSupervisorActor(outputPath: File, keywords: Option[List[String]]) extends Actor {
  private val logger = Logging(context.system, this)

  private val unprocessedSaverActor = {
    val filePrefix = "twitter-raw"
    context.actorOf(SaverActor.props(filePrefix), "twitter-raw-saver")
  }

  private val processedSaverActor = {
    val filePrefix = "twitter-processed"
    context.actorOf(SaverActor.props(filePrefix), "processed-saver")
  }

  private val processorRouter = context.actorOf(TwitterProcessorRouter.props(unprocessedSaverActor.path, processedSaverActor.path), "processor-router")
  private val downloaderActor = context.actorOf(TwitterDownloaderActor.props(processorRouter.path), "downloader")
  private val emailerActor = context.actorOf(Props[EmailerActor], "emailer")

  override def receive: Receive = {
    case Start =>
      logger.info(s"Supervisor received start event.")
      downloaderActor ! TwitterDownloaderActor.Start(keywords)

    case Stop =>
      logger.info(s"Supervisor received stop event. Shutting down downloader...")

      val downloaderFuture = gracefulStop(downloaderActor, FiniteDuration(30, TimeUnit.SECONDS), TwitterDownloaderActor.Stop)
      Await.result(downloaderFuture, FiniteDuration(35, TimeUnit.SECONDS))

      logger.info("Shutting down processors...")
      val processorFuture = gracefulStop(processorRouter, FiniteDuration(60, TimeUnit.SECONDS))
      Await.result(processorFuture, FiniteDuration(65, TimeUnit.SECONDS))

      logger.info("Shutting down savers...")
      val saverFuture = gracefulStop(unprocessedSaverActor, FiniteDuration(30, TimeUnit.SECONDS))

      Await.result(saverFuture, FiniteDuration(35, TimeUnit.SECONDS))

      // Attempt to shutdown ActorSystem
      logger.info("Attempting to shut down actor system...")
      context.system.shutdown()

      sender() ! Stop

    case WriteError(ex) =>

    case other =>
      logger.warning(s"Invalid message received from $sender:\n$other")

  }
}

object TwitterSupervisorActor {

  def props(outputPath: File, keywords: Option[List[String]] = None): Props = Props(new TwitterSupervisorActor(outputPath, keywords))

  sealed trait SupervisorMessage

  case class WriteError(ex: Exception) extends SupervisorMessage

  case object Start extends SupervisorMessage

  case object Stop extends SupervisorMessage

}