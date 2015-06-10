package datacollector

import java.io.{ BufferedWriter, File, FileOutputStream, OutputStreamWriter }
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.zip.GZIPOutputStream

import akka.actor.{ Actor, Props }
import akka.event.Logging

/**
 * Saves received strings to gzipped files.
 *
 * @author Emre Çelikten
 */
class SaverActor(val filePrefix: String, val configuration: ConfigurationModule) extends Actor {
  private implicit val loggingContext = Logging(context.system, this)

  private val df = new SimpleDateFormat("yy.MM.dd-HH.mm.ss")
  var (file, writer) = createWriter()
  Logger.info(s"SaverActor ready, will save to ${file.toString}.")

  override def receive: Receive = {
    case str: String =>
      try {
        writer.write(str)
      } catch {
        case ex: Exception =>
          Logger.error(s"Error while writing to file $file.\n" + Utils.getStackTraceString(ex))

          try {
            writer.close()
            throw ex
          } catch {
            case ex: Exception =>
              Logger.error(s"Error while closing the writer after an error while writing to file $file!\n" + Utils.getStackTraceString(ex))
              throw ex
          }
      }
    case other =>
      Logger.warn(s"Invalid message received from ${sender()}:\n$other")
  }

  override def postStop(): Unit = {
    Logger.info("Flushing output and closing stream.")
    writer.flush()
    writer.close()
  }

  def createWriter(): (File, BufferedWriter) = {
    val file = new File(configuration.outputPath, filePrefix + "." + df.format(Calendar.getInstance().getTime) + ".gz")
    (file, new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(file)))))
  }
}

object SaverActor {
  def props(filePrefix: String): Props = Props(new SaverActor(filePrefix, Configuration.configuration))
}
