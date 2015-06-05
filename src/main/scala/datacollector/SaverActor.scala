package datacollector

import java.io.{ BufferedWriter, File, FileOutputStream, OutputStreamWriter }
import java.util.zip.GZIPOutputStream

import akka.actor.{ Actor, Props }
import akka.event.Logging

/**
 * Saves received strings to gzipped files.
 *
 * @author Emre Çelikten
 */
class SaverActor(file: File) extends Actor {
  private val logger = Logging(context.system, this)

  private val writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(file))))
  logger.info(s"SaverActor ready, will save to ${file.toString}.")

  override def receive: Receive = {
    case str: String =>
      try {
        writer.write(str)
      } catch {
        case e: Exception =>
          logger.error(e.getStackTrace.mkString("\n"))
      }
    case other =>
      logger.warning(s"Invalid message received from ${sender()}:\n$other")
  }

  override def postStop(): Unit = {
    logger.info("Flushing output and closing stream.")
    writer.flush()
    writer.close()
  }
}

object SaverActor {
  def props(file: File): Props = Props(new SaverActor(file))
}
