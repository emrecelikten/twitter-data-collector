package datacollector

import java.util.concurrent.LinkedBlockingQueue

import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ TestActorRef, TestKit, TestProbe }
import com.twitter.hbc.core.Client
import com.twitter.hbc.core.event.Event
import com.twitter.hbc.httpclient.auth.Authentication
import com.typesafe.config.ConfigFactory
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest._
import org.scalatest.mock.MockitoSugar

import scala.concurrent.duration._

/**
 * @author Emre Çelikten
 */

class TwitterDownloaderActorSpec(_system: ActorSystem) extends TestKit(_system)
    with FlatSpecLike with MockitoSugar with Matchers with BeforeAndAfterAll {

  // TODO: These tests are nasty, and they won't probably work on another computer due to timing issues.

  def this() = this(ActorSystem("TwitterDownloaderActorTest", ConfigFactory.parseString(TestKitUsageSpec.config)))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  implicit val ec = system.dispatcher

  private class Fixture {
    val probe = TestProbe()

    val testConfiguration = new TestConfiguration

    val mockClient = mock[Client]
    val mockClientFactory = mock[TwitterClientFactoryModule]

    val msgQueue = new LinkedBlockingQueue[String]()
    val eventQueue = new LinkedBlockingQueue[Event]()

    val mockLogger = new TestLogger

    when(mockClientFactory.getClientAndQueues(any[Authentication], any[Option[List[String]]])).
      thenReturn(Tuple3(mockClient, msgQueue, eventQueue))

    val actor = TestActorRef[TwitterDownloaderActor](Props(new TwitterDownloaderActor(probe.ref.path, mockClientFactory, mockLogger, testConfiguration)))
    val underlying = actor.underlyingActor
  }

  def withFixture(test: Fixture => Any): Unit = {
    val f = new Fixture

    test(f)

    system.stop(f.actor)
  }

  def withinExpected[T](expected: FiniteDuration)(f: => T): T = within(expected - 30.millis, expected + 50.millis)(f)

  "TwitterDownloaderActor" should "try to connect when Start message is received" in withFixture { f =>
    f.actor ! TwitterDownloaderActor.Start()

    awaitAssert(verify(f.underlying.clientFactory).getClientAndQueues(any[Authentication], any[Option[List[String]]]), 50.millis)
    awaitAssert(verify(f.underlying.client).connect(), 20.millis)
  }

  it should "restart properly" in withFixture { f =>
    f.underlying.msgQueue = f.msgQueue
    f.underlying.eventQueue = f.eventQueue
    f.underlying.started = true

    f.actor ! TwitterDownloaderActor.Restart

    system.scheduler.scheduleOnce(20.millis) {
      f.msgQueue.add("Foobar")
    }

    f.probe.expectMsg(TweetMessage("Foobar"))
    f.underlying.msgQueue.isEmpty shouldEqual true
  }

  it should "try to process messages in queue" in withFixture { f =>
    f.underlying.msgQueue = f.msgQueue
    f.msgQueue.add("Foobar")

    f.actor ! TwitterDownloaderActor.Start()

    f.probe.expectMsg(TweetMessage("Foobar"))
  }

  it should "try to process remaining messsages in queue on stopping" in withFixture { f =>
    f.underlying.msgQueue = f.msgQueue

    (1 to 50).foreach(i => f.msgQueue.put("Foobar" + i))

    f.msgQueue.size() shouldEqual 50

    f.underlying.stop()

    f.msgQueue.size() shouldEqual 0
  }

  it should "reschedule processing when queue is empty" in withFixture { f =>
    when(f.mockClient.isDone).thenReturn(false)

    system.scheduler.scheduleOnce(f.testConfiguration.queueRecheckSleepDuration - 10.millis) {
      f.msgQueue.add("Foobar")
    }

    f.actor ! TwitterDownloaderActor.Start()

    withinExpected(f.testConfiguration.queueRecheckSleepDuration) {
      f.probe.expectMsg(TweetMessage("Foobar"))
    }
  }

  it should "schedule reconnection when there is a problem with the client" in withFixture { f =>
    when(f.mockClient.isDone).thenReturn(true)

    f.actor ! TwitterDownloaderActor.Start()

    system.scheduler.scheduleOnce(f.testConfiguration.reconnectSleepDuration - 100.millis) {
      when(f.mockClient.isDone).thenReturn(false)
    }

    // Wait until connection
    awaitAssert(verify(f.mockClient, times(1)).connect(), f.testConfiguration.reconnectSleepDuration, 5.millis)

    Thread.sleep(f.testConfiguration.reconnectSleepDuration.toMillis + 20)

    verify(f.mockClient, times(2)).connect() // Connect for restart

    Thread.sleep(f.testConfiguration.reconnectSleepDuration.toMillis + 20)

    verify(f.mockClient, times(2)).connect() // Make sure we reconnect only once
  }

  it should "schedule reconnection when there seems to be a silent problem in downloading" in withFixture { f =>
    when(f.mockClient.isDone).thenReturn(false)

    system.scheduler.scheduleOnce(f.testConfiguration.queueRecheckSleepDuration + 50.millis) {
      f.msgQueue.add("Foobar")
    }

    f.actor ! TwitterDownloaderActor.Start()

    withinExpected(f.testConfiguration.queueRecheckSleepDuration * 2) {
      // Connect for restart
      awaitAssert(verify(f.mockClient, times(2)).connect(), f.testConfiguration.queueRecheckSleepDuration * 2 + 20.millis, 5.millis)

      f.probe.expectMsg(TweetMessage("Foobar"))
    }
  }

  it should "sleep and wake up properly when there seems to be a silent problem in downloading" in withFixture { f =>
    when(f.mockClient.isDone).thenReturn(false)

    // Recheck once (wait), reconnect, recheck again (wait), try to reconnect, go to sleep (wait)
    val sleepDur = f.testConfiguration.queueRecheckSleepDuration * 2 + f.testConfiguration.reconnectLongSleepDuration

    system.scheduler.scheduleOnce(sleepDur + 50.millis) {
      f.msgQueue.add("Foobar")
    }

    f.actor ! TwitterDownloaderActor.Start()

    // There is a race here, I wrote the test such that the message is being expected during first recheck afer waking up
    withinExpected(sleepDur + f.testConfiguration.queueRecheckSleepDuration) {
      // Connect for restart
      awaitAssert(verify(f.mockClient, times(2)).connect(), sleepDur, 50.millis)

      f.probe.expectMsg(TweetMessage("Foobar"))
    }
  }

}
