package websocket4s.spray

import akka.actor.{ActorSystem, ActorRef}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSuite}
import spray.routing._
import websocket4s.core.{ActorRegisterMemoryImpl, WebSocketListener}
import websocket4s.server.RoutingServerEndPoint
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

////////////////////////////////////////////////////////////////////////////////
/**
 * Created by namnguyen on 6/16/15.
 */
class ClientServerTesting  extends FunSuite with BeforeAndAfterAll with BeforeAndAfter{
  //----------------------------------------------------------------------------
  implicit val system = ActorSystem("on-spray-websocket-unit-test")
  implicit val registerTable = new ActorRegisterMemoryImpl()
  //----------------------------------------------------------------------------
  override def beforeAll(): Unit ={
    println("Perform initialize Test Environment...")

  }
  //----------------------------------------------------------------------------
  override def afterAll(): Unit ={
    println("Clean up...")

  }
  //////////////////////////////////////////////////////////////////////////////
  class RouterAdapter(conn: ActorRef) extends SprayWebSocketServerAdapter(conn) {
    listeners.subscribe(new WebSocketListener {
      override def onConnect(): Unit = println("Server open connection")

      override def onClose(reason: String): Unit = println("Server close connection")

      override def receive(dataFrame: String): Unit = println(s"Server receives [$dataFrame]")
    })
    val serverEndPoint = new RoutingServerEndPoint(this, Set("UI"))
    serverEndPoint.onMessageReceived(Some{
      implicit message => println(message.data)
    })
    serverEndPoint.onRequestReceived(Some {
      implicit request =>
        Future {
          s"Reply from Server for: [${request.data}]"
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