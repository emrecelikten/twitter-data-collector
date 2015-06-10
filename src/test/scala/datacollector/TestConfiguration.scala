package datacollector

import com.typesafe.config.{ ConfigFactory, Config }

/**
 * @author Emre Çelikten
 */
class TestConfiguration extends {
  override protected val conf: Config = {
    val configFile = sys.props.getOrElse("testConfiguration", "application-test.conf")
    ConfigFactory.load(configFile)
  }
} with ConfigurationModule
