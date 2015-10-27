package datacollector

import akka.actor.{ Actor, ActorPath, Props }
import akka.event.Logging

import scala.util.matching.Regex

/**
 * Processes received tweets. Sends tweets to be saved before and after processing.
 *
 * @author Emre Çelikten
 */
class TwitterProcessorActor(saverActorPath: ActorPath) extends Actor {
  private implicit val loggingAdapter = Logging(context.system, this)

  Logger.info("ProcessorActor is online.")

  override def receive: Receive = {
    case TweetMessage(tweet) =>
      if (TwitterProcessorActor.regex.findFirstIn(tweet).nonEmpty) {
        context.actorSelection(saverActorPath) ! tweet
      } else Logger.debug(s"Invalid tweet received:\n$tweet")
    case other =>
      Logger.warn(s"Invalid message received from $sender:\n$other")
  }
}

object TwitterProcessorActor {
  val regex: Regex = """"expanded_url":"http[s ]:\\/\\/www.swarmapp.com\\/c\\/\w+"""".r

  def props(saverActorPath: ActorPath): Props =
    Props(new TwitterProcessorActor(saverActorPath))
}