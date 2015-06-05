package datacollector

import com.twitter.hbc.httpclient.auth.{ Authentication, OAuth1 }
import com.typesafe.config.ConfigFactory

/**
 * Loads and provides application configuration.
 *
 * @author Emre Çelikten
 */
object Configuration {
  lazy val conf = ConfigFactory.load("application-emre.conf")
  lazy val authentication: Authentication = new OAuth1(consumerKey, consumerSecret, accessToken, accessSecret)

  private lazy val consumerKey = conf.getString("twitter.consumer.key")
  private lazy val consumerSecret = conf.getString("twitter.consumer.secret")
  private lazy val accessToken = conf.getString("twitter.access.token")
  private lazy val accessSecret = conf.getString("twitter.access.secret")
}
