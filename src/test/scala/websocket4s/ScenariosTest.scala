package websocket4s

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSuite}
import org.slf4j.LoggerFactory
import websocket4s.client.ClientEndPoint
import websocket4s.core.{Response, Request, Message, ActorRegisterMemoryImpl}
import websocket4s.mock.WebSocketAdapterMemImpl
import websocket4s.server.ServerEndPoint
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration.Duration

////////////////////////////////////////////////////////////////////////////////
/**
 * Created by namnguyen on 5/28/15.
 */
class ScenariosTest extends FunSuite with BeforeAndAfterAll with BeforeAndAfter{
  private val logger = LoggerFactory.getLogger(this.getClass)
  val configString =
    """
      |akka {
      |  actor {
      |    provider = "akka.remote.RemoteActorRefProvider"
      |  }
      |  remote {
      |    transport = "akka.remote.netty.NettyRemoteTransport"
      |    netty.tcp {
      |      hostname = "127.0.0.1"
      |      port = 2558
      |    }
      |  }
      |}
    """.stripMargin
  val config =  ConfigFactory.parseString(configString)
    .withFallback(ConfigFactory.load())

  val hostname = config.getString("akka.remote.netty.tcp.hostname")
  val port = config.getInt("akka.remote.netty.tcp.port")
  val akkaSystem = "akka-unit-test"
  implicit val actorSystem = ActorSystem.create(akkaSystem,config)
  implicit val actorRegister = new ActorRegisterMemoryImpl()
  //----------------------------------------------------------------------------
  override def beforeAll(): Unit ={
    println("Perform initialize Test Environment...")

  }
  //----------------------------------------------------------------------------
  before{
    //clean up before each test
    println("Before")
  }
  //----------------------------------------------------------------------------
  after {
    //clean up after each test
    println("After")
  }
  //----------------------------------------------------------------------------
  test("Test a client asks server directly (No Routing)..."){
    println("Test a client asks server directly (No Routing)...")
    val clientAdapter = new WebSocketAdapterMemImpl("Client")
    val serverAdapter = new WebSocketAdapterMemImpl("Server")
    val clientEndPoint = new ClientEndPoint(clientAdapter)
    val serverEndPoint = new ServerEndPoint(serverAdapter,Set("BOARD:1125"))
    val sentMessage = "Hello World"
    val testPromise = Promise[Boolean]()
    serverEndPoint.onMessageReceived(Some {
      implicit message => {
        testPromise.success(sentMessage == message.data)
        assert(sentMessage === message.data)
      }
    })

    serverEndPoint.onRequestReceived(Some{
      implicit request => Future{
        //println(s"Server receives request [${request.data}]")
        blocking {
          Thread.sleep(2)
        }
        s"I got your question [${request.data}] here is my answer OOOOPS !!!"
      }
    })
    clientAdapter.connect(serverAdapter)
    clientEndPoint.tell(sentMessage)
    val testPromiseFuture = testPromise.future
    assert(Await.result(testPromiseFuture,Duration.Inf)===true)

    val questionNum = 1000

    val fList = for(i <- 1 to questionNum) yield {
      val ask = s"Hey wsup $i"
      clientEndPoint.ask(ask).map(res => {
        val equal =
          res.data==s"I got your question [$ask] here is my answer OOOOPS !!!"
        equal
      })
    }
    val futureBooleanList = Future.sequence(fList)
    val booleanList = Await.result(futureBooleanList,Duration.Inf)
    println(s"  --> Total Questions Asked: $questionNum")
    assert(booleanList.size===questionNum)
    assert(!booleanList.contains(false))
    clientAdapter.close()
    //actorRegister.printAll()
  }
  //----------------------------------------------------------------------------
  test("Test Routing a client communicate to other client using Tags..."){
    println("Test Routing a client communicate to other client using Tags...")
    val client1Tag = "BOARD:1125"
    val client2Tag = "UI:BOARD:1125"
    val clientAdapter1 = new WebSocketAdapterMemImpl("Client1")
    val serverAdapter1 = new WebSocketAdapterMemImpl("Server1")
    val clientEndPoint1 = new ClientEndPoint(clientAdapter1)
    val serverEndPoint1 = new ServerEndPoint(serverAdapter1,Set(client1Tag))

    val clientAdapter2 = new WebSocketAdapterMemImpl("Client2")
    val serverAdapter2 = new WebSocketAdapterMemImpl("Server2")
    val clientEndPoint2 = new ClientEndPoint(clientAdapter2)
    val serverEndPoint2 = new ServerEndPoint(serverAdapter2,Set(client2Tag))

    val client1MessageReceivedPromise = Promise[Message]()
    val client1RequestReceivedPromise = Promise[Request]()
    val client2MessageReceivedPromise = Promise[Message]()
    val client2RequestReceivedPromise = Promise[Request]()
    val client1ResponseReceivedPromise = Promise[Response]()
    val client2ResponseReceivedPromise = Promise[Response]()

    clientEndPoint1.onMessageReceived(Some {
      case msg =>  {
        println(s"Client 1 receives Message: ${msg.data} & tags: ${msg.forTags.toString}")
        client1MessageReceivedPromise.trySuccess(msg)
      }
    })

    clientEndPoint1.onRequestReceived( Some{
      case req => {
        client1RequestReceivedPromise.trySuccess(req)
        println(s"Client 1 receives Request: ${req.data} & tags: ${req.forTags.toString}")
        Future{
          s"Client 1 responds [${req.data}]"
        }
      }
    })

    clientEndPoint2.onMessageReceived(Some{
      case msg =>  {
        client2MessageReceivedPromise.trySuccess(msg)
        println(s"Client 2 receives Message: ${msg.data} & tags: ${msg.forTags.toString}")
      }
    })

    clientEndPoint2.onRequestReceived( Some{
      case req => {
        client2RequestReceivedPromise.trySuccess(req)
        println(s"Client 2 receives Request: ${req.data} & tags: ${req.forTags.toString}")
        Future{
          s"Client 2 responds [${req.data}]"
        }
      }
    })

    clientAdapter1.connect(serverAdapter1)
    clientAdapter2.connect(serverAdapter2)
    val client1Message = "Hello World From EndPoint 1"
    val client2Message = "Hello World From EndPoint 2"
    clientEndPoint1.tellTags(Set("UI:BOARD:1125"),client1Message)
    clientEndPoint2.tellTags(Set("BOARD:1125"), client2Message)
    val client2ReceivedMessage = Await.result(client2MessageReceivedPromise.future,Duration.Inf)
    val client1ReceivedMessage = Await.result(client1MessageReceivedPromise.future,Duration.Inf)
    assert(client2ReceivedMessage.data===client1Message)
    assert(client2ReceivedMessage.forTags===Some(serverEndPoint2.tags))
    assert(client1ReceivedMessage.data===client2Message)
    assert(client1ReceivedMessage.forTags===Some(serverEndPoint1.tags))

//    clientEndPoint2.tellTags(Set("BOARD:1122"),"No one will receive the message")

    val client1Request = "Client1: How are you doing?"
    val client2Request = "Client2: How are you doing?"

    clientEndPoint1.askTags(Set(client2Tag),client1Request).map(r=>{
      client1ResponseReceivedPromise.trySuccess(r)
      println(s"Client 1 Received: ${r.data} with endPointId ${r.endPointId}")

    })
    clientEndPoint2.askTags(Set(client1Tag),client2Request).map(r=>{
      client2ResponseReceivedPromise.trySuccess(r)
      println(s"Client 2 Received: ${r.data} with endPointId ${r.endPointId}")
    })

    val client1ReceivedResponse = Await.result(client1ResponseReceivedPromise.future,Duration.Inf)
    val client2ReceivedResponse = Await.result(client2ResponseReceivedPromise.future,Duration.Inf)

    assert(client1ReceivedResponse.data===s"Client 2 responds [$client1Request]")
    assert(client1ReceivedResponse.endPointId.isDefined)
    assert(client1ReceivedResponse.endPointId === serverEndPoint2.id)
    assert(client2ReceivedResponse.data===s"Client 1 responds [$client2Request]")
    assert(client2ReceivedResponse.endPointId.isDefined)
    assert(client2ReceivedResponse.endPointId === serverEndPoint1.id)
    clientAdapter1.close()
    clientAdapter2.close()
  }
  //----------------------------------------------------------------------------
  test("Test Using Exact ID of 1 end point to send message to other end point..."){
    val clientAdapter1 = new WebSocketAdapterMemImpl("Client1")
    val serverAdapter1 = new WebSocketAdapterMemImpl("Server1")
    val clientEndPoint1 = new ClientEndPoint(clientAdapter1)
    val serverEndPoint1 = new ServerEndPoint(serverAdapter1)

    val clientAdapter2 = new WebSocketAdapterMemImpl("Client2")
    val serverAdapter2 = new WebSocketAdapterMemImpl("Server2")
    val clientEndPoint2 = new ClientEndPoint(clientAdapter2)
    val serverEndPoint2 = new ServerEndPoint(serverAdapter2)
    val endPoint1MessageReceivedPromise = Promise[Message]()

    clientEndPoint1.onMessageReceived(Some{
      case msg => endPoint1MessageReceivedPromise.trySuccess(msg)
    })

    val endPoint2Message = "Hello World"
    clientAdapter1.connect(serverAdapter1)
    clientAdapter2.connect(serverAdapter2)
    Thread.sleep(1)
    val id = serverEndPoint1.id.get
    clientEndPoint2.tell(id,endPoint2Message)
    val endPoint1ReceivedMessage = Await.result(endPoint1MessageReceivedPromise.future,Duration.Inf)
    assert(endPoint1ReceivedMessage.data===endPoint2Message)
    clientAdapter1.close()
    clientAdapter2.close()
  }
  //----------------------------------------------------------------------------
  test("Test Using Exact ID of 1 end point to ask request to other end point..."){
    val clientAdapter1 = new WebSocketAdapterMemImpl("Client1")
    val serverAdapter1 = new WebSocketAdapterMemImpl("Server1")
    val clientEndPoint1 = new ClientEndPoint(clientAdapter1)
    val serverEndPoint1 = new ServerEndPoint(serverAdapter1)

    val clientAdapter2 = new WebSocketAdapterMemImpl("Client2")
    val serverAdapter2 = new WebSocketAdapterMemImpl("Server2")
    val clientEndPoint2 = new ClientEndPoint(clientAdapter2)
    val serverEndPoint2 = new ServerEndPoint(serverAdapter2)
    val endPoint1RequestReceivedPromise = Promise[Request]()

    clientEndPoint1.onRequestReceived(Some{
      case req => {
        endPoint1RequestReceivedPromise.trySuccess(req)
        Future{ "EndPoint1 replies: " }
      }
    })
    val endPoint2Message = "End Point 2 asks ???"
    clientAdapter1.connect(serverAdapter1)
    clientAdapter2.connect(serverAdapter2)
    Thread.sleep(1)
    val id = serverEndPoint1.id.get
    val futureResponse = clientEndPoint2.ask(id,endPoint2Message)
    val endPoint1ReceivedRequest = Await.result(endPoint1RequestReceivedPromise.future,Duration.Inf)
    val response = Await.result(futureResponse,Duration.Inf)

    assert(endPoint1ReceivedRequest.data===endPoint2Message)
    assert(response.data ==="EndPoint1 replies: ")
    clientAdapter1.close()
    clientAdapter2.close()

  }
  //----------------------------------------------------------------------------
  test("Test send messages to many EndPoints with same tag..."){
    val endPoint1Tag1 = "DEVICE/SN001"
    val endPoint1Tag2 = "DEVICE"
    val endPoint1Tag3 = "UI"

    val endPoint2Tag1 = "UI/DEVICE/SN001"
    val endPoint2Tag2 = "UI"

    val endPoint3Tag1 = "UI/DEVICE/SN002"
    val endPoint3Tag2 = "UI"

    val clientAdapter1 = new WebSocketAdapterMemImpl("Client1")
    val serverAdapter1 = new WebSocketAdapterMemImpl("Server1")
    val clientEndPoint1 = new ClientEndPoint(clientAdapter1)
    val serverEndPoint1 = new ServerEndPoint(serverAdapter1
      ,Set(endPoint1Tag1,endPoint1Tag2,endPoint1Tag3))
    val endPoint1MessageReceivedPromise = Promise[Message]()
    clientEndPoint1.onMessageReceived(Some{
      case msg => endPoint1MessageReceivedPromise.trySuccess(msg)
    })

    val clientAdapter2 = new WebSocketAdapterMemImpl("Client2")
    val serverAdapter2 = new WebSocketAdapterMemImpl("Server2")
    val clientEndPoint2 = new ClientEndPoint(clientAdapter2)
    val serverEndPoint2 = new ServerEndPoint(serverAdapter2
      ,Set(endPoint2Tag1,endPoint2Tag2))
    val endPoint2MessageReceivedPromise = Promise[Message]()
    clientEndPoint2.onMessageReceived(Some{
      case msg => endPoint2MessageReceivedPromise.trySuccess(msg)
    })

    val clientAdapter3 = new WebSocketAdapterMemImpl("Client2")
    val serverAdapter3 = new WebSocketAdapterMemImpl("Server2")
    val clientEndPoint3 = new ClientEndPoint(clientAdapter3)
    val serverEndPoint3 = new ServerEndPoint(serverAdapter3
      ,Set(endPoint3Tag1,endPoint3Tag2))
    val endPoint3MessageReceivedPromise = Promise[Message]()
    clientEndPoint3.onMessageReceived(Some{
      case msg => endPoint3MessageReceivedPromise.trySuccess(msg)
    })

    clientAdapter1.connect(serverAdapter1)
    clientAdapter2.connect(serverAdapter2)
    clientAdapter3.connect(serverAdapter3)
    val clientEndPoint1Message = "Hello ALL UI!!!"
    clientEndPoint1.tellTags(Set("UI"),clientEndPoint1Message)

    val endPoint1ReceivedMessage=Await.result(
      endPoint1MessageReceivedPromise.future,Duration.Inf)
    val endPoint2ReceivedMessage=Await.result(
      endPoint2MessageReceivedPromise.future,Duration.Inf)
    val endPoint3ReceivedMessage=Await.result(
      endPoint3MessageReceivedPromise.future,Duration.Inf)

    //self received
    assert(endPoint1ReceivedMessage.data===clientEndPoint1Message)
    assert(endPoint2ReceivedMessage.data===clientEndPoint1Message)
    assert(endPoint3ReceivedMessage.data===clientEndPoint1Message)

    clientAdapter1.close()
    clientAdapter2.close()
    clientAdapter3.close()
  }
  //----------------------------------------------------------------------------
  test("Test Change Tags and publish..."){

  }
  //----------------------------------------------------------------------------
  override def afterAll(): Unit ={
    println("Clean up Test Environment...")
    Thread.sleep(500)
    println("------------ Actor Register ------------")
    actorRegister.printAll()
    println("------------ End Of Actor Register -----")
    actorSystem.shutdown()
  }
  //----------------------------------------------------------------------------
}
////////////////////////////////////////////////////////////////////////////////