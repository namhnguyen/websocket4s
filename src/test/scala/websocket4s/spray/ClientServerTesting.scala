package websocket4s.spray

import akka.actor.{Props, ActorSystem, ActorRef}
import akka.io.Tcp
import akka.util.Timeout
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSuite}
import spray.routing._
import websocket4s.client.ClientEndPoint
import websocket4s.core.{ActorRegisterMemoryImpl, WebSocketListener}
import websocket4s.server.RoutingServerEndPoint
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
////////////////////////////////////////////////////////////////////////////////
/**
 * Created by namnguyen on 6/16/15.
 */
class ClientServerTesting  extends FunSuite with BeforeAndAfterAll with BeforeAndAfter{
  //----------------------------------------------------------------------------
  implicit val system = ActorSystem("on-spray-websocket-unit-test")
  implicit val registerTable = new ActorRegisterMemoryImpl()
  val serverAddress = "127.0.0.1"
  val serverPort = 13412
  var server:ActorRef = _
  //----------------------------------------------------------------------------
  override def beforeAll(): Unit ={
    println("Perform initialize Test Environment...")
    implicit val timeout = Timeout(2 second)
    val workerProps = (conn: ActorRef) => Props(new RouterAdapter(conn))
    val (sv, command) = WebSocketServer.start(serverAddress, serverPort, workerProps)
    val binding = Await.result(command, 2 second) match {
      case bound: Tcp.Bound => bound
      case other => throw new Exception(s"failed to bring up server $other")
    }
    server = sv
    val address = binding.localAddress
    println(s"Address [${address.getHostName}:${address.getPort}]")
  }
  //----------------------------------------------------------------------------
  override def afterAll(): Unit ={
    println("Clean up...")
    system.stop(server)
  }
  //----------------------------------------------------------------------------
  test("Test Client Connect to Server..."){
    val client = new ClientEndPoint(
      new SprayWebSocketClientAdapter(serverAddress, serverPort,"/",true))
    val requestMessage = "How are you?"
    val result = client.ask(requestMessage)
    assert("I am fine, thank you" === Await.result(result,Duration.Inf).data)
  }
  //----------------------------------------------------------------------------
  test("Test 1 Client sends multiple requests simultaneously but still receives correct responses..."){
    val clientNum = 100
    val client = new ClientEndPoint(
      new SprayWebSocketClientAdapter(serverAddress, serverPort,"/",true))
    val fr = (for (i <- 1 to clientNum) yield (i -> s"client $i send request")).par
    val responseList = fr.map(s=> (s._1,client.ask(s._2)))
    assert(responseList.size===clientNum)
    responseList.map { case (id,futureResponse) =>
      assert(s"Don't understand request - request data: [client $id send request]"
        ===Await.result(futureResponse,Duration.Inf).data)
    }
  }
  //----------------------------------------------------------------------------
  test("Test Client 1 send message to Client 2 using Route Message"){
    val client1 = new ClientEndPoint(
      new SprayWebSocketClientAdapter(serverAddress, serverPort,"/",true))
    val client2 = new ClientEndPoint(
      new SprayWebSocketClientAdapter(serverAddress, serverPort,"/",true))
    client1.tell("TAGS:CLIENT1,UI")
    client2.tell("TAGS:CLIENT2,UI")
    client2.onRequestReceived(Some{ case request =>
      Future{ s"client2 response to client1 request[${request.data}]"}
    })
    //make sure it changes
    Thread.sleep(500)
    val futureResponse = client1.askTags(Set("CLIENT2"),"How are you?")
    assert("client2 response to client1 request[How are you?]"
      ===Await.result(futureResponse,Duration.Inf).data)

  }
  //----------------------------------------------------------------------------
  //////////////////////////////////////////////////////////////////////////////
  class RouterAdapter(conn: ActorRef) extends SprayWebSocketServerAdapter(conn) {
    listeners.subscribe(new WebSocketListener {
      override def onConnect(): Unit = {}

      override def onClose(reason: String): Unit = {}

      override def receive(dataFrame: String): Unit = {}
    })

    val serverEndPoint = new RoutingServerEndPoint(this, Set("UI"))

    serverEndPoint.onMessageReceived(Some{
      implicit message => {
        //change tags can be implemented here
        println(message.data)
        if (message.data.startsWith("TAGS:")){
          val tagString = message.data.substring(5)
          val tagsToChange = tagString.split(",")
          serverEndPoint.changeTags(tagsToChange.toSet)
          println("Tag Changed: "+tagString)
        }

      }
    })

    serverEndPoint.onRequestReceived(Some {
      implicit request =>
        Future {
          request.data match {
            case "How are you?" => "I am fine, thank you"
            case t:String => s"Don't understand request - request data: [$t]"
          }
        }
    })

    override def route: Route = path("hello") {
      get {
        complete {
          <h1>Greetings!</h1>
        }
      }
    }
  }
  //////////////////////////////////////////////////////////////////////////////
}
////////////////////////////////////////////////////////////////////////////////