import org.slf4j.LoggerFactory

/**
 * @author Emre Çelikten
 */
package object datacollector {
  case class TweetMessage(tweet: String)

  val logger = LoggerFactory.getLogger("data-collector")
}
