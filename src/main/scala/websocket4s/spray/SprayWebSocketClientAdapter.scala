package websocket4s.spray

import akka.actor.{Props, ActorSystem, ActorRef}
import spray.can.websocket.UpgradedToWebSocket
import spray.can.websocket.frame.{TextFrame, CloseFrame}
import websocket4s.core.{WebSocketListeners, WebSocketAdapter}
////////////////////////////////////////////////////////////////////////////////
/**
 * Created by namnguyen on 6/15/15.
 */
class SprayWebSocketClientAdapter(host:String,port:Int,path:String = "/")(implicit actorSystem:ActorSystem)
  extends WebSocketAdapter{

  private val _listeners = new WebSocketListeners()
  private val Close = CloseFrame()
  var _connection:ActorRef  = _
  val client = actorSystem.actorOf(Props(classOf[ClientWorker],host,port,path,this))
  //----------------------------------------------------------------------------
  override def push(dataFrame: String): Unit = _connection ! TextFrame(dataFrame)
  //----------------------------------------------------------------------------
  override def listeners: WebSocketListeners = _listeners
  //----------------------------------------------------------------------------
  override def close(): Unit = _connection ! Close
  //----------------------------------------------------------------------------
}
////////////////////////////////////////////////////////////////////////////////
class ClientWorker(host:String,port:Int,path:String = "/",adapter: SprayWebSocketClientAdapter)
  extends WebSocketClient(host,port,path){

  override def websockets: Receive = {
    case UpgradedToWebSocket => {
      adapter._connection = this.connection
      for (listener <- adapter.listeners) listener.onConnect()
    }
    case c:CloseFrame => connection ! c
    case s:String => for (listener <- adapter.listeners) listener.receive(s)
  }

  override def postStop():Unit = {
    super.postStop()
    for (listener <- adapter.listeners) listener.onClose("WebSocket Server Connection Kill")
  }
}
////////////////////////////////////////////////////////////////////////////////