package datacollector

import java.util.concurrent.TimeUnit

import com.twitter.hbc.httpclient.auth.{ Authentication, OAuth1 }
import com.typesafe.config.{ Config, ConfigFactory }

import scala.collection.JavaConversions._
import scala.util.matching.Regex

/**
 * Loads and provides application configuration.
 *
 * @author Emre Çelikten
 */
trait ConfigurationModule {
  protected val conf: Config

  val twitterAuthentication: Authentication = {
    val consumerKey: String = safeLoad(conf.getString, "twitter.consumer.key")
    val consumerSecret: String = safeLoad(conf.getString, "twitter.consumer.secret")
    val accessToken: String = safeLoad(conf.getString, "twitter.access.token")
    val accessSecret: String = safeLoad(conf.getString, "twitter.access.secret")

    new OAuth1(consumerKey, consumerSecret, accessToken, accessSecret)
  }

  val reconnectSleepDuration: Long = safeLoad(conf.getDuration(_, TimeUnit.MILLISECONDS), "twitter.duration.sleep.reconnect")
  val queueRecheckSleepDuration: Long = safeLoad(conf.getDuration(_, TimeUnit.MILLISECONDS), "twitter.duration.sleep.queueRecheck")
  val gracefulShutdownDuration: Long = safeLoad(conf.getDuration(_, TimeUnit.MILLISECONDS), "twitter.duration.gracefulShutdown")
  val msgQueueWarningDuration: Long = safeLoad(conf.getDuration(_, TimeUnit.MILLISECONDS), "twitter.duration.queueWarning")

  val keywords: Option[List[String]] = {
    val elems = safeLoad(conf.getStringList, "twitter.keywords").toList
    if (elems.isEmpty) None else Some(elems)
  }

  val filterRegex: Regex = safeLoad(conf.getString, "twitter.filterRegex").r // Slightly dangerous, might fail at runtime

  val msgQueueSize: Int = safeLoad(conf.getInt, "twitter.size.msgQueue")
  val eventQueueSize: Int = safeLoad(conf.getInt, "twitter.size.eventQueue")
  val msgQueueWarningSize: Int = safeLoad(conf.getInt, "twitter.size.queueWarning")

  val emailsEnabled: Boolean = safeLoad(conf.getBoolean, "twitter.enableEmails")

  protected def safeLoad[T](getM: String => T, configurationKey: String): T = {
    Option(getM(configurationKey)).getOrElse {
      throw new RuntimeException(s"Error while reading configuration: Missing configuration key for '$configurationKey'!")
    }
  }

  val foursquareClientId: String = safeLoad(conf.getString, "foursquare.client.id")
  val foursquareClientSecret: String = safeLoad(conf.getString, "foursquare.client.secret")
  val foursquareAccessToken: String = safeLoad(conf.getString, "foursquare.access.token")
  val foursquareResolveUrl: String = safeLoad(conf.getString, "foursquare.resolveUrl")

  val outputPath: String = safeLoad(conf.getString, "outputPath")
}

object Configuration {
  final var configuration: ConfigurationModule = _

  def init(configPath: String = "application.conf"): Boolean = {
    try {
      configuration = new {
        override protected val conf: Config = ConfigFactory.load(configPath)
      } with ConfigurationModule
      true
    } catch {
      case ex: Exception =>
        println(s"Error while loading given application configuration at $configPath.\n" + Utils.getStackTraceString(ex))
        false
    }
  }
}
