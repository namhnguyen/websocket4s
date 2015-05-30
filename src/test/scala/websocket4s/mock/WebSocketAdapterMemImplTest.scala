package websocket4s.mock

import org.scalatest._
import websocket4s.core.WebSocketListener

/**
 * Created by namnguyen on 5/28/15.
 */
class WebSocketAdapterMemImplTest extends FunSuite {
  //----------------------------------------------------------------------------
  test("Test Mock WebSocketAdapterMemoryImpl"){
    val clientEndPoint = new WebSocketAdapterMemImpl("Client")
    val serverEndPoint = new WebSocketAdapterMemImpl("Server")

    @volatile
    var clientConnected:Boolean = false
    @volatile
    var serverConnected:Boolean = false
    @volatile
    var serverMessage:Option[String] = None
    @volatile
    var clientClosed =false
    @volatile
    var serverClosed = false

    clientEndPoint.listeners.subscribe(new WebSocketListener {
      override def onConnect(): Unit = {
        clientConnected = true
      }

      override def onClose(reason: String): Unit = {
        clientClosed = true
        println(s"${clientEndPoint.name} Closed with reason [$reason]")
      }

      override def receive(dataFrame: String): Unit = { }
    })

    serverEndPoint.listeners.subscribe(new WebSocketListener {
      override def onConnect(): Unit = {
        serverConnected = true
      }

      override def onClose(reason: String): Unit = {
        serverClosed = true
        println(s"${serverEndPoint.name} Closed with reason [$reason]")
      }

      override def receive(dataFrame: String): Unit = {
        serverMessage = Some(dataFrame)
      }
    })
    val sentMessage = "Hello World"
    clientEndPoint.connect(serverEndPoint)
    clientEndPoint.push(sentMessage)
    Thread.sleep(10)
    assert(clientConnected)
    assert(serverConnected)
    assert(serverMessage.isDefined)
    assert(sentMessage===serverMessage.get)

    clientEndPoint.close() //async
    Thread.sleep(10)
    assert(clientClosed)
    assert(serverClosed)
  }
  //----------------------------------------------------------------------------
}
