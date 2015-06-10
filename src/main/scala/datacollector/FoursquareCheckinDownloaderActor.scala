package datacollector

import akka.actor.Actor
import akka.actor.Actor.Receive
import datacollector.FoursquareCheckinDownloaderActor.Download
import spray.http.HttpRequest

import spray.http._
import spray.httpx.encoding.{ Gzip, Deflate }
import spray.client.pipelining._

import scala.concurrent.Future

/**
 * @author Emre Çelikten
 */
class FoursquareCheckinDownloaderActor(val configuration: ConfigurationModule) extends Actor {
  // TODO: Set a separate dispatcher for spray requests
  import context.dispatcher
  val pipeline: HttpRequest => Future[String] =
    encode(Gzip) ~>
      sendReceive ~>
      decode(Deflate) ~>
      unmarshal[String]

  override def receive: Receive = {
    case Download(shortId) => retrieveCheckinData(shortId) // TODO: Pipe to processor
    case _ =>
  }

  def retrieveCheckinData(shortId: String): Future[String] = {
    val url = new StringBuilder(configuration.foursquareResolveUrl).
      append("?shortId=").append(shortId).
      append("&oauth_token=").append(configuration.foursquareAccessToken).
      append("&v=20150610&m=swarm").toString()

    pipeline(Get(url))
  }

}

object FoursquareCheckinDownloaderActor {
  sealed trait FoursquareCheckinActorMessage

  case class Download(shortId: String) extends FoursquareCheckinActorMessage
}
