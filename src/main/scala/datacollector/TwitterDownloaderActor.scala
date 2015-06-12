package datacollector

import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }

import akka.actor.{ Actor, ActorPath, Props }
import akka.event.Logging
import com.twitter.hbc.core.Client
import com.twitter.hbc.core.event.Event
import datacollector.TwitterDownloaderActor._

import scala.concurrent.duration.FiniteDuration

/**
 * Contains a Twitter client and handles passing downloaded tweets to processors.
 *
 * @author Emre Çelikten
 */
class TwitterDownloaderActor(
    private val processorRouterPath: ActorPath,
    private val clientFactory: TwitterClientFactoryModule,
    private val logger: LoggerModule,
    private val configuration: ConfigurationModule
) extends Actor {
  private implicit val loggingAdapter = Logging(context.system, this)
  private var client: Client = _
  private var msgQueue: LinkedBlockingQueue[String] = _
  private var eventQueue: LinkedBlockingQueue[Event] = _
  private var started: Boolean = false
  private var lastQueueSizeWarningTime: Long = 0L
  private var count = 0L
  private var creation = System.currentTimeMillis()

  // Holds the keywords for filtering at the last client creation. We need this to be able to restart the client.
  private var lastKeywords: Option[List[String]] = _

  override def receive: Receive = {
    case Start(keywords) =>
      if (started) {
        logger.warn(s"Received a Start message when already started.")
      } else {
        start(keywords)
      }

      self ! Consume

    case Stop =>
      if (!started) {
        logger.warn("Received a Stop message while inactive.")
      } else {
        context stop self
      }

    case Restart =>
      if (!started) {
        logger.warn("Received a Restart message while inactive.")
      } else {
        stop()
        start(lastKeywords)
      }

    case Consume =>
      consume()

    case other =>
      logger.warn(s"Invalid message received from $sender:\n$other")

  }

  private def start(keywords: Option[List[String]]): Unit = {
    logger.info("Received start event.")
    val (client, msgQueue, eventQueue) = clientFactory.getClientAndQueues(configuration.twitterAuthentication, keywords)

    client.connect()

    this.client = client
    this.msgQueue = msgQueue
    this.eventQueue = eventQueue
    this.lastKeywords = keywords

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

  private def consume(): Unit = {
    if (!msgQueue.isEmpty) {
      // Warn if the queue is growing and growing, do the check per n secs
      if (msgQueue.size() > configuration.msgQueueWarningSize) {
        val curTime = System.currentTimeMillis()
        if (curTime - lastQueueSizeWarningTime > configuration.msgQueueWarningDuration) {
          logger.warn(s"Downloaded message queue size is growing dangerously: " + msgQueue.size(), configuration.emailOnQueueWarning)
          lastQueueSizeWarningTime = curTime
        }
      }

      val tweet = msgQueue.take()
      context.actorSelection(processorRouterPath) ! TweetMessage(tweet)
      self ! Consume
    } else {
      // Queue is empty, check if there is a problem with the client.
      if (client.isDone) {
        // We shouldn't be here, as our state is still in Consume. This means the client is somehow dead.
        // Try to restart the client after 30 seconds.
        logger.warn(s"Client seems to be inactive, restarting after ${configuration.reconnectSleepDuration} seconds...")
        context.system.scheduler.scheduleOnce(FiniteDuration(configuration.reconnectSleepDuration, TimeUnit.MILLISECONDS), self, Restart)(context.dispatcher)
      } else {
        // Client seems to be working, wait for 5 seconds, then check the queue again.
        context.system.scheduler.scheduleOnce(FiniteDuration(configuration.queueRecheckSleepDuration, TimeUnit.MILLISECONDS), self, Consume)(context.dispatcher)
      }
    }

  }

}

object TwitterDownloaderActor {

  def props(processorActorPath: ActorPath): Props = Props(new TwitterDownloaderActor(processorActorPath, TwitterClientFactory, Logger, Configuration.configuration))

  sealed trait DownloaderActorMessage

  case class Start(keywords: Option[List[String]] = None) extends DownloaderActorMessage

  case object Restart extends DownloaderActorMessage

  case object Consume extends DownloaderActorMessage

  case object Stop extends DownloaderActorMessage

}