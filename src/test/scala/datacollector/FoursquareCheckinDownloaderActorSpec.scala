package datacollector

import akka.actor.{ Props, ActorSystem }
import akka.testkit.{ TestActorRef, TestKit }
import com.typesafe.config.ConfigFactory
import org.scalatest.{ Matchers, BeforeAndAfterAll, FlatSpecLike }
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{ JsValue, Json }
import scala.concurrent.duration._

import scala.concurrent.Await

/**
 * @author Emre Çelikten
 */
class FoursquareCheckinDownloaderActorSpec(_system: ActorSystem) extends TestKit(_system)
    with FlatSpecLike with MockitoSugar with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("TwitterDownloaderActorTest", ConfigFactory.parseString(TestKitUsageSpec.config)))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "FoursquareVenueActor" should "obtain access token from Foursquare correctly" in {
    val shortId = "fcP5m3yn7AL" // Sample short id from Foursquare

    val actor = TestActorRef(Props(new FoursquareCheckinDownloaderActor(new TestConfiguration)))
    val underlying: FoursquareCheckinDownloaderActor = actor.underlyingActor

    val f = underlying.retrieveCheckinData(shortId)

    val result = Json.parse(Await.result(f, 10.seconds))

    val expected = Json.parse("""{"meta":{"code":200},"notifications":[{"type":"notificationTray","item":{"unreadCount":0}}],"response":{"checkin":{"id":"548b64f0498e3784281ca137","createdAt":1418421488,"type":"checkin","timeZoneOffset":-300,"displayGeo":{"id":"72057594043056517","name":"New York, United States"},"exactContextLine":"New York, NY, United States","user":{"id":"620652","firstName":"David","lastName":"Hu","gender":"male","photo":{"prefix":"https:\/\/irs2.4sqi.net\/img\/user\/","suffix":"\/620652-0VWRX3NAVZNGP3Z0.jpg"},"canMessage":false},"venue":{"id":"4ef0e7cf7beb5932d5bdeb4e","name":"Foursquare HQ","contact":{"phone":"+13474940946","formattedPhone":"+1 347-494-0946","twitter":"foursquare","facebook":"80690156072","facebookUsername":"foursquare","facebookName":"Foursquare"},"location":{"address":"568 Broadway Fl 10","crossStreet":"at Prince St","lat":40.72412842453194,"lng":-73.99726510047911,"postalCode":"10012","cc":"US","city":"New York","state":"NY","country":"United States","formattedAddress":["568 Broadway Fl 10 (at Prince St)","New York, NY 10012","United States"]},"categories":[{"id":"4bf58dd8d48988d125941735","name":"Tech Startup","pluralName":"Tech Startups","shortName":"Tech Startup","icon":{"prefix":"https:\/\/ss3.4sqi.net\/img\/categories_v2\/shops\/technology_","suffix":".png"},"primary":true}],"verified":true,"stats":{"checkinsCount":51546,"usersCount":9491,"tipCount":296},"url":"https:\/\/foursquare.com","storeId":"HQHQHQHQ","saves":{"count":0,"groups":[]}},"source":{"name":"Swarm for iOS","url":"https:\/\/www.swarmapp.com"},"photos":{"count":0,"items":[]},"likes":{"count":0,"groups":[]},"like":false,"sticker":{"id":"53fe77e54b9048855b3648b6","name":"Lappy Toppy","image":{"prefix":"https:\/\/irs2.4sqi.net\/img\/sticker\/","sizes":[60,94,150,300],"name":"\/laptop_4d7599.png"},"stickerType":"unlockable","group":{"name":"collectible","index":29},"pickerPosition":{"page":1,"index":5},"teaseText":"You didn\u2019t get the memo?","unlockText":"You're totally not reading BuzzFeed all day. Here\u2019s a sticker for being so productive. Now back to that \u201cWhich Sticker Are You?\u201d quiz."},"isMayor":false,"score":{"total":6,"scores":[{"icon":"https:\/\/ss1.4sqi.net\/img\/points\/swarm-mayor.png","message":"11 check-ins at Offices in the past 2 months.","points":3},{"icon":"https:\/\/ss1.4sqi.net\/img\/points\/swarm-mayor.png","message":"10 check-ins at Foursquare HQ in the past 2 months.","points":3}]},"reasonCannotSeeComments":"notfriends","reasonCannotAddComments":"notfriends","objectLeaderboards":[]},"signature":"9xGOE_VKWgwWBGr4l_uwwKVKCng"}}""")
    assert((expected \ "response" \ "checkin" \ "id").as[String] === "548b64f0498e3784281ca137")

    assert((result \ "response" \ "checkin" \ "id").as[String] === (expected \ "response" \ "checkin" \ "id").as[String])
    assert(result \ "response" \ "checkin" \ "venue" \ "id" === (expected \ "response" \ "checkin" \ "venue" \ "id"))
  }
}
