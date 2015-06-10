package datacollector

import java.util.concurrent.LinkedBlockingQueue

import akka.actor.{ ActorSystem, Props }
import akka.event.LoggingAdapter
import akka.testkit.{ TestActorRef, TestKit, TestProbe }
import com.twitter.hbc.core.Client
import com.twitter.hbc.core.event.Event
import com.twitter.hbc.httpclient.auth.Authentication
import com.typesafe.config.ConfigFactory
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike }
import org.mockito.Mockito._
import org.mockito.Matchers._

import scala.concurrent.duration._

/**
 * @author Emre Çelikten
 */

class TwitterDownloaderActorSpec(_system: ActorSystem) extends TestKit(_system)
    with FlatSpecLike with MockitoSugar with BeforeAndAfterAll {

  def this() = this(ActorSystem("TwitterDownloaderActorTest", ConfigFactory.parseString(TestKitUsageSpec.config)))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  private class Context {
    val testConfiguration = new TestConfiguration
    val probe = TestProbe()
    val mockClient = mock[Client]
    val mockClientFactory = mock[TwitterClientFactoryModule]

    val msgQueue = new LinkedBlockingQueue[String]()
    val eventQueue = new LinkedBlockingQueue[Event]()

    val mockLogger = new TestLogger

    when(mockClientFactory.getClientAndQueues(any[Authentication], any[Option[List[String]]])).
      thenReturn(Tuple3(mockClient, msgQueue, eventQueue))

    val actor = TestActorRef(Props(new TwitterDownloaderActor(probe.ref.path, mockClientFactory, mockLogger, testConfiguration)))
  }

  "TwitterDownloaderActor" should "try to connect when Start message is received" in new Context {
    actor ! TwitterDownloaderActor.Start()

    awaitAssert(verify(mockClientFactory).getClientAndQueues(any[Authentication], any[Option[List[String]]]), 200.millis)
    awaitAssert(verify(mockClient).connect(), 200.millis)
  }

  it should "try to process messages in queue" in new Context {
    msgQueue.add("Foobar")

    actor ! TwitterDownloaderActor.Start()

    probe.expectMsg(TweetMessage("Foobar"))
  }

  it should "reschedule processing when queue is empty" in new Context {
    when(mockClient.isDone()).thenReturn(false)

    actor ! TwitterDownloaderActor.Start()

    system.scheduler.scheduleOnce(20.millis)(msgQueue.add("Foobar"))(system.dispatcher)

    within(95.millis, 130.millis) {
      probe.expectMsg(TweetMessage("Foobar"))
    }
  }

  it should "reschedule connection when there is a problem with the client" in new Context {
    when(mockClient.isDone()).thenReturn(true)

    actor ! TwitterDownloaderActor.Start()

    awaitAssert(verify(mockClient, times(1)).connect(), 20.millis, 5.millis)

    awaitAssert(verify(mockClient, times(2)).connect(), 5000.millis)
  }
}
