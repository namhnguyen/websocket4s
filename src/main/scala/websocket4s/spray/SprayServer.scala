package websocket4s.spray

import akka.actor.{ActorSystem, Props, ActorRef}
import akka.io.Tcp
import akka.util.Timeout
import spray.can.websocket.UpgradedToWebSocket
import spray.can.websocket.frame.TextFrame
import spray.routing.Route

import scala.concurrent._
import scala.concurrent.duration._

/**
 * Created by namnguyen on 6/12/15.
 */
object SprayServer {
  implicit val system = ActorSystem("on-spray-websocket")

  def main(args:Array[String]):Unit = {
    val workerProps = (conn: ActorRef) =>
      Props(classOf[WebSocketActor], conn)
    implicit val timeout = Timeout(2 second)
    val (server, command) = WebSocketServer.start("127.0.0.1", 8080, workerProps)
    val binding = Await.result(command, 2 second) match {
      case bound: Tcp.Bound => bound
      case other => throw new Exception(s"failed to bring up server $other")
    }
    val address = binding.localAddress
    println(address.getHostName)
    println(address.getPort)
  }
}

class WebSocketActor(conn:ActorRef) extends SimpleWebSocketComboWorker(conn){
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