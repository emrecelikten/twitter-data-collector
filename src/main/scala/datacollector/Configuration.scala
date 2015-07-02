package datacollector

import java.time.Duration
import java.util.concurrent.TimeUnit

import com.twitter.hbc.httpclient.auth.{ Authentication, OAuth1 }
import com.typesafe.config.{ Config, ConfigFactory }

import scala.collection.JavaConversions._
import scala.concurrent.duration.{ FiniteDuration }
import scala.util.Try
import scala.util.matching.Regex

/**
 * Loads and provides application configuration.
 *
 * @author Emre Çelikten
 */
trait ConfigurationModule {
  protected val conf: Config

  protected def safeLoad[T](getM: String => T, configurationKey: String): T = {
    Try(getM(configurationKey)).getOrElse {
      throw new RuntimeException(s"Error while reading configuration: Missing configuration key for '$configurationKey'!")
    }
  }

  // Ugly
  implicit protected def duration2FiniteDuration(duration: Duration): FiniteDuration = FiniteDuration(duration.toMillis, TimeUnit.MILLISECONDS)

  val twitterAuthentication: Authentication = {
    val consumerKey: String = safeLoad(conf.getString, "twitter.consumer.key")
    val consumerSecret: String = safeLoad(conf.getString, "twitter.consumer.secret")
    val accessToken: String = safeLoad(conf.getString, "twitter.access.token")
    val accessSecret: String = safeLoad(conf.getString, "twitter.access.secret")

    new OAuth1(consumerKey, consumerSecret, accessToken, accessSecret)
  }

  val reconnectSleepDuration: FiniteDuration = safeLoad(conf.getDuration(_), "twitter.duration.sleep.reconnect")
  val reconnectLongSleepDuration: FiniteDuration = safeLoad(conf.getDuration(_), "twitter.duration.sleep.reconnectLong")
  val queueRecheckSleepDuration: FiniteDuration = safeLoad(conf.getDuration(_), "twitter.duration.sleep.queueRecheck")
  val msgQueueWarningDuration: FiniteDuration = safeLoad(conf.getDuration(_), "twitter.duration.queueWarning")

  val gracefulShutdownDuration: FiniteDuration = safeLoad(conf.getDuration(_), "duration.gracefulShutdown")
  val heartBeatDuration: FiniteDuration = safeLoad(conf.getDuration(_), "duration.heartBeat")
  val emailOnHeartBeat: Boolean = safeLoad(conf.getBoolean(_), "email.emailOnHeartBeat")

  val keywords: Option[List[String]] = {
    val elems = safeLoad(conf.getStringList, "twitter.keywords").toList
    if (elems.isEmpty) None else Some(elems)
  }

  val filterRegex: Regex = safeLoad(conf.getString, "twitter.filterRegex").r // Slightly dangerous, might fail at runtime

  val msgQueueSize: Int = safeLoad(conf.getInt, "twitter.size.msgQueue")
  val eventQueueSize: Int = safeLoad(conf.getInt, "twitter.size.eventQueue")
  val msgQueueWarningSize: Int = safeLoad(conf.getInt, "twitter.size.queueWarning")
  val emailOnQueueWarning: Boolean = safeLoad(conf.getBoolean, "email.emailOnQueueWarning")

  val foursquareClientId: String = safeLoad(conf.getString, "foursquare.client.id")
  val foursquareClientSecret: String = safeLoad(conf.getString, "foursquare.client.secret")
  val foursquareAccessToken: String = safeLoad(conf.getString, "foursquare.access.token")
  val foursquareResolveUrl: String = safeLoad(conf.getString, "foursquare.resolveUrl")

  val outputPath: String = safeLoad(conf.getString, "outputPath")

  val emailsEnabled: Boolean = safeLoad(conf.getBoolean, "email.enableEmails")
  val emailAddresses: List[String] = safeLoad(conf.getStringList, "email.addresses").toList
  val emailSmtpHost: String = safeLoad(conf.getString, "email.smtp.host")
  val emailSmtpPort: Int = safeLoad(conf.getInt, "email.smtp.port")
  val emailUsername: String = safeLoad(conf.getString, "email.username")
  val emailPassword: String = safeLoad(conf.getString, "email.password")
}

object Configuration {
  final var configuration: ConfigurationModule = _

  def init(configPath: String): Boolean = {
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
