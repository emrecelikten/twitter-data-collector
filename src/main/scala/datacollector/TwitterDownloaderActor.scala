package datacollector

import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }

import akka.actor.{ Actor, ActorPath, Props }
import com.twitter.hbc.core.Client
import com.twitter.hbc.core.event.Event
import datacollector.TwitterDownloaderActor._
import akka.event.Logging

import scala.concurrent.duration.FiniteDuration

/**
 * Contains a Twitter client and handles passing downloaded tweets to processors.
 *
 * @author Emre Çelikten
 */
class TwitterDownloaderActor(processorRouterPath: ActorPath) extends Actor {
  private val logger = Logging(context.system, this)
  private var client: Client = _
  private var msgQueue: LinkedBlockingQueue[String] = _
  private var eventQueue: LinkedBlockingQueue[Event] = _
  private var started: Boolean = false
  private var lastQueueSizeWarningTime: Long = 0L
  private var count = 0L
  private var creation = System.currentTimeMillis()

  override def receive: Receive = {
    case Start(keywords) =>
      if (started) {
        logger.warning(s"Received a Start message when already started.")
      } else {
        start(keywords)
      }

      self ! Consume

    case Consume =>
      if (!msgQueue.isEmpty) {
        // Warn if the queue is growing and growing, do the check per 120 secs
        if (msgQueue.size() > 200) {
          val curTime = System.currentTimeMillis()
          if (curTime - lastQueueSizeWarningTime > 120000) {
            logger.warning(s"Downloaded message queue size is growing dangerously: " + msgQueue.size())
            lastQueueSizeWarningTime = curTime
          }
        }

        val tweet = msgQueue.take()
        context.actorSelection(processorRouterPath) ! TweetMessage(tweet)
        self ! Consume
      } else {
        // Queue is empty, sleep for 5 seconds, then check again
        context.system.scheduler.scheduleOnce(FiniteDuration(5, TimeUnit.SECONDS), self, Consume)(context.dispatcher)
      }

    case Stop =>
      if (!started) {
        logger.warning("Received a Stop message while inactive.")
      } else {
        context stop self
      }

    case other =>
      logger.warning(s"Invalid message received from $sender:\n$other")

  }

  private def start(keywords: Option[List[String]]): Unit = {
    logger.info("Received start event.")
    val (client, msgQueue, eventQueue) = TwitterClientFactory.getClientAndQueues(Configuration.authentication, keywords)

    client.connect()

    this.client = client
    this.msgQueue = msgQueue
    this.eventQueue = eventQueue

    started = true
  }

  override def postStop(): Unit = {
    stop()
    super.postStop()
  }

  private def stop(): Unit = {
    logger.info("Received stop event, starting to consume remaining messages.")
    // Consume remaining messages
    client.stop(2000)

    while (!msgQueue.isEmpty) {
      val tweet = msgQueue.take()
      context.actorSelection("../processor-router") ! TweetMessage(tweet)
    }
    logger.info("Consumption of remaining messages are complete.")

    started = false
  }
}

object TwitterDownloaderActor {

  def props(processorActorPath: ActorPath): Props = Props(new TwitterDownloaderActor(processorActorPath))

  sealed trait DownloaderActorMessage

  case class Start(keywords: Option[List[String]] = None) extends DownloaderActorMessage

  case object Consume extends DownloaderActorMessage

  case object Stop extends DownloaderActorMessage

}