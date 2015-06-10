package datacollector

import akka.event.LoggingAdapter

/**
 * @author Emre Çelikten
 */
class TestLogger extends LoggerModule {
  override def debug(message: => String, sendEmail: Boolean)(implicit adapter: Perhaps[LoggingAdapter]): Unit = {}

  override def warn(message: => String, sendEmail: Boolean)(implicit adapter: Perhaps[LoggingAdapter]): Unit = {}

  override protected def log(message: => String, sendEmail: Boolean, loggingMethod: (String) => Unit): Unit = println(message)

  override def error(message: => String, sendEmail: Boolean)(implicit adapter: Perhaps[LoggingAdapter]): Unit = println(message)

  override def info(message: => String, sendEmail: Boolean)(implicit adapter: Perhaps[LoggingAdapter]): Unit = {}
}
