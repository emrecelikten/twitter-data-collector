package datacollector

/**
 * @author Emre Çelikten
 */
object Utils {
  def getStackTraceString(ex: Exception): String = {
    new StringBuilder().append(ex.getMessage).append("\n").append(ex.getStackTrace.mkString("\n")).toString
  }

}
