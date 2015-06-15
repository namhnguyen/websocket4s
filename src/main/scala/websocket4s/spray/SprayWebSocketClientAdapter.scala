package websocket4s.spray

import spray.can.websocket.UpgradedToWebSocket
import spray.can.websocket.frame.{TextFrame, CloseFrame}
import websocket4s.core.{WebSocketListeners, WebSocketAdapter}
////////////////////////////////////////////////////////////////////////////////
/**
 * Created by namnguyen on 6/15/15.
 */
abstract class SprayWebSocketClientAdapter(host:String,port:Int,path:String = "/")
  extends WebSocketClient(host,port,path) with WebSocketAdapter{

  private val _listeners = new WebSocketListeners()
  private val Close = CloseFrame()
  //----------------------------------------------------------------------------
  override def websockets: Receive = {
    case UpgradedToWebSocket => for ( listener <- listeners) listener.onConnect()
    case c:CloseFrame => connection ! c
    case s:String => for (listener <- listeners) listener.receive(s)

  }
  //----------------------------------------------------------------------------
  override def push(dataFrame: String): Unit = connection ! TextFrame(dataFrame)
  //----------------------------------------------------------------------------
  override def listeners: WebSocketListeners = _listeners
  //----------------------------------------------------------------------------
  override def close(): Unit = connection ! Close
  //----------------------------------------------------------------------------
  override def postStop():Unit = {
    super.postStop()
    for (listener <- listeners) listener.onClose("WebSocket Server Connection Kill")
  }
  //----------------------------------------------------------------------------
}
////////////////////////////////////////////////////////////////////////////////