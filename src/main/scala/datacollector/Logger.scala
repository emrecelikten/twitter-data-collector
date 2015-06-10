package datacollector

import akka.event.LoggingAdapter
import org.slf4j.LoggerFactory

/**
 * @author Emre Çelikten
 */
trait LoggerModule {
  def debug(message: => String, sendEmail: Boolean = false)(implicit adapter: Perhaps[LoggingAdapter]): Unit

  def info(message: => String, sendEmail: Boolean = false)(implicit adapter: Perhaps[LoggingAdapter]): Unit

  def warn(message: => String, sendEmail: Boolean = true)(implicit adapter: Perhaps[LoggingAdapter]): Unit

  def error(message: => String, sendEmail: Boolean = true)(implicit adapter: Perhaps[LoggingAdapter]): Unit

  protected def log(message: => String, sendEmail: Boolean = false, loggingMethod: String => Unit): Unit
}

object Logger extends LoggerModule {
  protected val emailer: EmailerModule = Emailer

  val defaultLogger = LoggerFactory.getLogger("data-collector")

  def debug(message: => String, sendEmail: Boolean = false)(implicit adapter: Perhaps[LoggingAdapter]): Unit = {
    adapter.fold(log(message, sendEmail, defaultLogger.debug _))(a => log(message, sendEmail, a.debug _))
  }

  def info(message: => String, sendEmail: Boolean = false)(implicit adapter: Perhaps[LoggingAdapter]): Unit = {
    adapter.fold(log(message, sendEmail, defaultLogger.info _))(a => log(message, sendEmail, a.info _))
  }

  def warn(message: => String, sendEmail: Boolean = true)(implicit adapter: Perhaps[LoggingAdapter]): Unit = {
    adapter.fold(log(message, sendEmail, defaultLogger.warn _))(a => log(message, sendEmail, a.warning _))
  }

  def error(message: => String, sendEmail: Boolean = true)(implicit adapter: Perhaps[LoggingAdapter]): Unit = {
    adapter.fold(log(message, sendEmail, defaultLogger.error _))(a => log(message, sendEmail, a.error _))
  }

  protected def log(message: => String, sendEmail: Boolean = false, loggingMethod: String => Unit): Unit = {
    if (sendEmail && Configuration.configuration.emailsEnabled) {
      emailer.email(message)
    }

    loggingMethod(message)
  }
}
