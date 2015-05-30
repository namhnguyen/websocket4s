package websocket4s.core

/**
 * Created by namnguyen on 5/24/15.
 */
trait WebSocketListener {
  def onConnect():Unit
  def onClose(reason:String):Unit
  def receive(dataFrame:String):Unit
}
