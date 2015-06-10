package datacollector

/**
 * @author Emre Çelikten
 */
trait EmailerModule {
  def email(text: String): Unit
}

object Emailer extends EmailerModule {
  def email(text: String): Unit = {
    Logger.info(s"EMAILED $text")
  }
}
