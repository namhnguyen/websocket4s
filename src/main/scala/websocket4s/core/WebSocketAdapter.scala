package websocket4s.core
////////////////////////////////////////////////////////////////////////////////
/**
 * WebSocketAdapter includes the common signature of all WebSocket
 * Created by namnguyen on 5/23/15.
 */
trait WebSocketAdapter {
  def push(dataFrame:String):Unit
  def listeners:WebSocketListeners
  def close():Unit
}
////////////////////////////////////////////////////////////////////////////////