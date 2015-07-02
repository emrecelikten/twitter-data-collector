package datacollector

import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }

import akka.actor.{ Actor, ActorPath, Props, Stash }
import akka.event.Logging
import com.twitter.hbc.core.Client
import com.twitter.hbc.core.event.{ EventType, Event }
import datacollector.TwitterDownloaderActor._

import scala.concurrent.duration._

/**
 * Contains a Twitter client and handles passing downloaded tweets to processors.
 *
 * @author Emre Çelikten
 */
class TwitterDownloaderActor(
    val processorRouterPath: ActorPath,
    val clientFactory: TwitterClientFactoryModule,
    val logger: LoggerModule,
    val configuration: ConfigurationModule
) extends Actor with Stash {
  implicit val loggingAdapter = Logging(context.system, this)
  var client: Client = _
  var msgQueue: LinkedBlockingQueue[String] = _
  var eventQueue: LinkedBlockingQueue[Event] = _
  var started: Boolean = false
  var lastQueueSizeWarningTime: Long = 0L
  var lastRestart: Long = 0L
  var creation = System.currentTimeMillis()

  // Holds the keywords for filtering at the last client creation. We need this to be able to restart the client.
  var lastKeywords: Option[List[String]] = _

  override def receive: Receive = activeReceive

  def activeReceive: Receive = {
    case Start(keywords) =>
      if (started) {
        logger.warn(s"Received a Start message while already started.")
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
        logger.warn("Received a Restart.")
        consumeAndReportEventQueue()
        stop()
        lastRestart = System.currentTimeMillis()
        start(lastKeywords)

        self ! Consume
      }

    case RecheckQueue =>
      recheckQueue()

    case Consume =>
      consume()

    case HeartBeatActor.HeartBeatRequest =>
      sender() ! HeartBeatActor.HeartBeat(client.getStatsTracker.getNumMessages)

    case other =>
      logger.warn(s"Invalid message received from $sender:\n$other")
  }

  def sleeperReceive: Receive = {
    case Stop =>
      if (!started) {
        logger.warn("Received a Stop message while inactive.")
      } else {
        context stop self
      }
    case WakeUp =>
      logger.warn("Waking up.")
      context.unbecome()
      self ! RecheckQueue
      unstashAll()
    case m =>
      logger.debug(s"Received $m while sleeping!")
      stash()
  }

  def start(keywords: Option[List[String]]): Unit = {
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

  def stop(): Unit = {
    logger.info("Received stop event, starting to consume remaining messages.")

    // Null checks are for cases where this actor is stopped before even it gets started, prevents NPEs
    if (client != null) client.stop(10000)

    // Consume remaining messages
    if (msgQueue != null) {
      while (!msgQueue.isEmpty) {
        val tweet = msgQueue.take()
        context.actorSelection("../processor-router") ! TweetMessage(tweet)
      }
      logger.info("Consumption of remaining messages are complete.")
    } else {
      logger.warn("Message queue was null.")
    }

    started = false
  }

  def consume(): Unit = {
    consumeAndReportEventQueue()

    if (!msgQueue.isEmpty) {
      // Warn if the queue is growing and growing, do the check per n secs
      if (msgQueue.size > configuration.msgQueueWarningSize) {
        val curTime = System.currentTimeMillis()
        if (curTime - lastQueueSizeWarningTime > configuration.msgQueueWarningDuration.toMillis) {
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
        // This means the client is somehow dead.
        logger.warn(s"Client seems to have problems, restarting after ${configuration.reconnectSleepDuration.toSeconds} seconds...")

        scheduleRestart(configuration.reconnectSleepDuration)
      } else {
        // Client reports to be still working, wait for some seconds, then check the queue again.
        context.system.scheduler.scheduleOnce(
          configuration.queueRecheckSleepDuration,
          self,
          RecheckQueue
        )(context.dispatcher)
      }
    }
  }

  /**
   * Checks current message queue size, then restarts the client if it is zero.
   *
   * This is used for handling silent failures, e.g. client is reporting that it is doing fine, but is receiving
   * no messages from the stream.
   */
  def recheckQueue(): Unit = {
    if (msgQueue.isEmpty) {
      // Something is wrong, we are not downloading tweets for some reason 
      // since the number of elements in queue did not change.
      logger.warn("Client seems to be inactive silently, restarting.")
      scheduleRestart(0.millis) // Schedule a restart immediately
    } else {
      self ! Consume
    }
  }

  /**
   * Tries to restart the client.
   *
   * If the client was restarted already recently, put the actor to sleep for some time so we don't "spam" with
   * connection requests.
   *
   * @param afterMillis time after which a restart operation will start
   */
  def scheduleRestart(afterMillis: FiniteDuration): Unit = {
    if (System.currentTimeMillis() - lastRestart <= configuration.reconnectLongSleepDuration.toMillis) {
      logger.warn("There was recent restart already, entering long sleep before trying again.")

      context.become(sleeperReceive)

      // Schedule a wake-up event
      context.system.scheduler.scheduleOnce(
        configuration.reconnectLongSleepDuration,
        self,
        WakeUp
      )(context.dispatcher)
    } else {
      context.system.scheduler.scheduleOnce(
        afterMillis,
        self,
        Restart
      )(context.dispatcher)
    }
  }

  def consumeAndReportEventQueue(): Unit = {
    // TODO: Potentially dangerous approach, might want to put a limit here
    while (!eventQueue.isEmpty) {
      val event = eventQueue.take()
      if (reportedEvents contains event.getEventType) {
        logger.warn(s"Twitter client sent event: ${event.getEventType} - ${event.getMessage}\n" + Option(event.getUnderlyingException).map(Utils.getStackTraceString).getOrElse(""))
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

  case object RecheckQueue extends DownloaderActorMessage

  case object Stop extends DownloaderActorMessage

  case object WakeUp extends DownloaderActorMessage

  // Event types received from Twitter client which we will report on logs
  private val reportedEvents = Set(EventType.DISCONNECTED, EventType.HTTP_ERROR, EventType.STOPPED_BY_ERROR, EventType.CONNECTION_ERROR)

}