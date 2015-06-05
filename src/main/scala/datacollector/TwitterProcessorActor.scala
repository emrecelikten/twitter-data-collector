package datacollector

import akka.actor.{ Actor, ActorPath, Props }
import akka.event.Logging

import scala.util.matching.Regex

/**
 * Processes received tweets. Sends tweets to be saved before and after processing.
 *
 * @author Emre Çelikten
 */
class TwitterProcessorActor(unprocessedSaverActorPath: ActorPath, processedSaverActorPath: ActorPath) extends Actor {
  private val logger = Logging(context.system, this)

  logger.info("ProcessorActor is online.")

  override def receive: Receive = {
    case TweetMessage(tweet) =>
      if (TwitterProcessorActor.regex.findFirstIn(tweet).nonEmpty) {
        context.actorSelection(unprocessedSaverActorPath) ! tweet
        // TODO: Process?
      } else logger.warning("Invalid tweet received.")
    case other =>
      logger.warning(s"Invalid message received from $sender:\n$other")
  }
}

object TwitterProcessorActor {
  val regex: Regex = """"expanded_url":"http[s ]:\\/\\/www.swarmapp.com\\/c\\/\w+"""".r

  def props(unprocessedSaverActorPath: ActorPath, processedSaverActorPath: ActorPath): Props =
    Props(new TwitterProcessorActor(unprocessedSaverActorPath, processedSaverActorPath))
}