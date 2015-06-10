package datacollector

import java.util.concurrent.LinkedBlockingQueue

import com.twitter.hbc.ClientBuilder
import com.twitter.hbc.core.endpoint.{ StatusesFilterEndpoint, StatusesSampleEndpoint }
import com.twitter.hbc.core.event.Event
import com.twitter.hbc.core.processor.StringDelimitedProcessor
import com.twitter.hbc.core.{ Client, Constants, HttpHosts }
import com.twitter.hbc.httpclient.auth.Authentication
import scala.collection.JavaConversions._

/**
 * Generates a Twitter HBC client given authentication and keywords.
 *
 * @author Emre Çelikten
 */

trait TwitterClientFactoryModule {
  def getClientAndQueues(
    authentication: Authentication,
    keywords: Option[List[String]]
  ): (Client, LinkedBlockingQueue[String], LinkedBlockingQueue[Event])
}

object TwitterClientFactory extends TwitterClientFactoryModule {
  def getClientAndQueues(
    authentication: Authentication,
    keywords: Option[List[String]]
  ): (Client, LinkedBlockingQueue[String], LinkedBlockingQueue[Event]) = {
    /** Set up your blocking queues: Be sure to size these properly based on expected TPS of your stream */
    val msgQueue = new LinkedBlockingQueue[String](Configuration.configuration.msgQueueSize)
    val eventQueue = new LinkedBlockingQueue[Event](Configuration.configuration.eventQueueSize)

    val hosebirdHosts = new HttpHosts(Constants.STREAM_HOST)
    val hosebirdEndpoint = keywords match {
      case Some(list) =>
        val ep = new StatusesFilterEndpoint()
        ep.trackTerms(list)
        ep
      case None => new StatusesSampleEndpoint()
    }

    val builder = new ClientBuilder()
      .name("TwitterClient")
      .hosts(hosebirdHosts)
      .authentication(authentication)
      .endpoint(hosebirdEndpoint)
      .processor(new StringDelimitedProcessor(msgQueue))
      .eventMessageQueue(eventQueue); // optional: use this if you want to process client events

    val hosebirdClient = builder.build()

    (hosebirdClient, msgQueue, eventQueue)
  }

}
