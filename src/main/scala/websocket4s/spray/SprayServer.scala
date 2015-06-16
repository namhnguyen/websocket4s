package websocket4s.spray

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props, ActorRef}
import akka.io.Tcp
import akka.util.Timeout
import spray.can.websocket.UpgradedToWebSocket
import spray.can.websocket.frame.TextFrame
import spray.routing.Route
import websocket4s.client.ClientEndPoint
import websocket4s.core.{WebSocketSystem, WebSocketListener, ActorRegisterMemoryImpl}
import websocket4s.server.RoutingServerEndPoint
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
////////////////////////////////////////////////////////////////////////////////
/**
 * Created by namnguyen on 6/12/15.
 */
object SprayServer {

  implicit val system = ActorSystem("on-spray-websocket")
  implicit val registerTable = new ActorRegisterMemoryImpl()
  ////////////////////////////////////////////////////////////////////////////////
  def main(args: Array[String]): Unit = {
    runServer()
    //runClient()
  }
  ////////////////////////////////////////////////////////////////////////////////
  def runServer(): Unit ={
    val workerProps = (conn: ActorRef) =>
      Props(classOf[RouterAdapter], conn)
    implicit val timeout = Timeout(2 second)
    val (server, command) = WebSocketServer.start("127.0.0.1", 8080, workerProps)
    val binding = Await.result(command, 2 second) match {
      case bound: Tcp.Bound => bound
      case other => throw new Exception(s"failed to bring up server $other")
    }
    val address = binding.localAddress
    println(address.getHostName)
    println(address.getPort)
    Thread.sleep(1000)
  }
  ////////////////////////////////////////////////////////////////////////////////
  def runClient(): Unit = {
    val client = new ClientEndPoint(new SprayWebSocketClientAdapter("127.0.0.1", 8080,"/",true))
    client.webSocketAdapter.listeners.subscribe(new WebSocketListener {
      override def onConnect(): Unit = println("Client Connect")

      override def onClose(reason: String): Unit = println("Client Close")

      override def receive(dataFrame: String): Unit = println(s"Client receives [$dataFrame]")
    })
    Thread.sleep(1000)
    WebSocketSystem.scheduler.scheduleWithFixedDelay(new Runnable {
      override def run(): Unit = {
        val futureResponse = client.ask("how are you??")
        futureResponse.onSuccess{ case response => println(response.data) }
      }
    },0,1000,TimeUnit.MILLISECONDS)
    //client.webSocketAdapter.close()
  }

  ////////////////////////////////////////////////////////////////////////////////
  class RouterAdapter(conn: ActorRef) extends SprayWebSocketServerAdapter(conn) {
    listeners.subscribe(new WebSocketListener {
      override def onConnect(): Unit = println("Server open connection")

      override def onClose(reason: String): Unit = println("Server close connection")

      override def receive(dataFrame: String): Unit = println(s"Server receives [$dataFrame]")
    })
    val serverEndPoint = new RoutingServerEndPoint(this, Set("UI"))
//    val schedule = WebSocketSystem.scheduler.schedule(new Runnable {
//      override def run(): Unit = serverEndPoint.webSocketAdapter.close()
//    },5,TimeUnit.SECONDS)

    serverEndPoint.onMessageReceived(Some{
      implicit message => println(message.data)
    })

    serverEndPoint.onRequestReceived(Some {
      implicit request =>
      Future {
        s"Reply from Server for: ${request.data}"
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

  ////////////////////////////////////////////////////////////////////////////////
  class WebSocketActor(conn: ActorRef) extends SimpleWebSocketComboWorker(conn) {
    override def route: Route = path("hello") {
      get {
        complete {
          <h1>Greetings!</h1>
        }
      }
    }

    /** User-defined websocket handler. */
    override def websockets: Receive = {
      case UpgradedToWebSocket => println("connection upgrade")
      case TextFrame(v) => send(TextFrame(v))
    }
  }

  ////////////////////////////////////////////////////////////////////////////////
}