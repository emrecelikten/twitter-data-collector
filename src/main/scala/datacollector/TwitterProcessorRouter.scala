package datacollector

import akka.actor.{ Actor, ActorPath, Props, Terminated }
import akka.event.Logging
import akka.routing.{ ActorRefRoutee, Router, SmallestMailboxRoutingLogic }

/**
 * Router for multiple TwitterProcessorActor workers. Receives processing tasks and delegates them to children.
 *
 * @author Emre Çelikten
 */
class TwitterProcessorRouter(saverActorPath: ActorPath, numWorkers: Int = 5) extends Actor {
  private var router = {
    val workers = Vector.fill(numWorkers) {
      val r = context.actorOf(TwitterProcessorActor.props(saverActorPath))
      context watch r
      ActorRefRoutee(r)
    }
    Router(SmallestMailboxRoutingLogic(), workers)
  }

  private implicit val loggingAdapter = Logging(context.system, this)
  Logger.info("ProcessorRouter ready with routees.")

  override def receive: Receive = {
    case msg: TweetMessage =>
      router.route(msg, sender())
    case Terminated(a) =>
      router = router.removeRoutee(a)
      val r = context.actorOf(TwitterProcessorActor.props(saverActorPath))
      context watch r
      router = router.addRoutee(r)
    case other =>
      Logger.warn(s"Invalid message received from $sender:\n$other")
  }
}

object TwitterProcessorRouter {
  def props(saverActorPath: ActorPath): Props =
    Props(new TwitterProcessorRouter(saverActorPath))
}
