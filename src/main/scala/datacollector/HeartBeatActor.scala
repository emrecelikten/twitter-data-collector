package datacollector

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.{ Props, ActorRef, ActorPath, Actor }
import akka.actor.Actor.Receive
import akka.event.Logging
import datacollector.HeartBeatActor.{ Start, HeartBeatRequest, HeartBeat }

import scala.concurrent.duration.FiniteDuration

/**
 * Logs download statistics for watched actors for every n hours.
 *
 * @author Emre Çelikten
 * @date   30/06/2015-14:24
 */
class HeartBeatActor(
    watchedActors: Map[ActorPath, String],
    configuration: ConfigurationModule,
    logger: LoggerModule
) extends Actor {
  implicit val loggingAdapter = Logging(context.system, this)
  implicit def ec = context.system.dispatcher

  // TODO: Point of failure: What happens if resolve fails?
  def scheduleHeartBeat(actor: ActorPath) = context.actorSelection(actor).resolveOne(FiniteDuration(30, TimeUnit.SECONDS)).map(ref => context.system.scheduler.scheduleOnce(configuration.heartBeatDuration, ref, HeartBeatRequest))

  override def receive: Receive = {
    case HeartBeat(numDownloads) =>
      watchedActors.get(sender().path) match {
        case Some(actorName) =>
          logger.info(s"$actorName reports $numDownloads downloads.", sendEmail = configuration.emailOnHeartBeat)
        case None => logger.warn(s"${sender()} could not be matched to a name, but reports $numDownloads downloads.")
      }

      scheduleHeartBeat(sender().path)
    case Start =>
      // Schedule heart beat messages for all actors that we watch
      watchedActors.foreach { case (path, _) => scheduleHeartBeat(path) }
      logger.info("Heart beat initialized.")
  }
}

object HeartBeatActor {
  def props(watchedActors: Map[ActorPath, String]): Props = Props(new HeartBeatActor(watchedActors, Configuration.configuration, Logger))

  sealed trait HeartBeatActorMessage

  case class HeartBeat(numDownloads: Long) extends HeartBeatActorMessage

  case object Start extends HeartBeatActorMessage

  case object HeartBeatRequest extends HeartBeatActorMessage
}