package datacollector

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.event.{ LoggingReceive, Logging }
import org.apache.commons.mail.{ DefaultAuthenticator, SimpleEmail }

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

/**
 * @author Emre Çelikten
 * @date   12/06/2015-02:04
 */

class EmailerActor extends Actor {
  private implicit val loggingAdapter = Logging(context.system, this)
  private val logger: LoggerModule = Logger

  // Holds messages to be sent with the next email
  val queue = new mutable.Queue[String]
  var lastSent: Long = 0L
  var sendPending: Boolean = false
  val interval = 10 * 60 * 1000 // TODO: Make this configurable

  override def receive: Receive = {
    case text: String =>
      queue enqueue text
      if (!sendPending) {
        sendPending = true
        val timeUntilNext: Long = Math.max(0L, lastSent + interval - System.currentTimeMillis())
        logger.info(s"Email scheduled for after ${timeUntilNext / 1000} seconds.")
        context.system.scheduler.scheduleOnce(
          FiniteDuration(timeUntilNext, TimeUnit.MILLISECONDS),
          self, EmailerActor.DoSend
        )(context.dispatcher)
      }
    case EmailerActor.DoSend =>
      doSend()
    case other =>
      logger.warn(s"Invalid message received from $sender:\n$other")
  }

  def doSend(): Unit = {
    val builder = new StringBuilder("LOGS:\n\n\n===============================================\n\n\n")

    while (queue.nonEmpty) {
      builder.append(queue.dequeue()).append("\n\n\n===============================================\n\n\n")
    }

    val email = new SimpleEmail()
    email.setHostName(Configuration.configuration.emailSmtpHost)
    email.setSmtpPort(Configuration.configuration.emailSmtpPort)
    email.setAuthenticator(new DefaultAuthenticator(Configuration.configuration.emailUsername, Configuration.configuration.emailPassword))
    email.setSSLOnConnect(true)
    email.setFrom(Configuration.configuration.emailUsername)
    email.setSubject("Data Collector report")
    email.setMsg(builder.toString())

    Configuration.configuration.emailAddresses.foreach(e => email.addTo(e))

    try {
      email.send()

      lastSent = System.currentTimeMillis()
      sendPending = false
      logger.info(s"Email sent.")
    } catch {
      case e: Exception => logger.warn("Failed to send email.\n" + Utils.getStackTraceString(e)) // TODO: Does nothing for now, reschedule sending
    }

  }
}

object EmailerActor {

  case object DoSend
}