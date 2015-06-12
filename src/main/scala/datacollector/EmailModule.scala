package datacollector

import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }

import akka.actor.Actor
import org.apache.commons.mail.{ DefaultAuthenticator, SimpleEmail }

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

/**
 * @author Emre Çelikten
 */
trait EmailerModule {
  def email(text: String): Unit
}

object Emailer extends EmailerModule {
  val path = Application.actorSystem.actorSelection("/user/supervisor/emailer")

  def email(text: String): Unit = {
    path ! text
  }

}
