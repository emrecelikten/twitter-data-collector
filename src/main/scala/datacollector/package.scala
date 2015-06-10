import akka.event.LoggingAdapter
import org.slf4j.LoggerFactory

/**
 * @author Emre Çelikten
 */
package object datacollector {
  // Optional implicit trick: http://missingfaktor.blogspot.com/2013/12/optional-implicit-trick-in-scala.html
  case class Perhaps[E](value: Option[E]) {
    def fold[F](ifAbsent: => F)(ifPresent: E => F): F = {
      value.fold(ifAbsent)(ifPresent)
    }
  }

  implicit def perhaps[E](implicit ev: E = null): Perhaps[E] = {
    Perhaps(Option(ev))
  }

  case class TweetMessage(tweet: String)
}
