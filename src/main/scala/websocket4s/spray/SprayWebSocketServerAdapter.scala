package websocket4s.spray

import akka.actor.{PoisonPill, ActorRef}
import spray.can.websocket.frame.{CloseFrame, TextFrame}
import websocket4s.core.{WebSocketListeners, WebSocketAdapter}
////////////////////////////////////////////////////////////////////////////////
/**
 * Created by namnguyen on 6/15/15.
 */
abstract class SprayWebSocketServerAdapter(conn:ActorRef)
  extends SimpleWebSocketComboWorker(conn) with WebSocketAdapter{

  private val _listeners = new WebSocketListeners()
  val closeFrame = CloseFrame()
  //Route will be implemented
  //override def route: Route = ???

  override def push(dataFrame: String): Unit = send(TextFrame(dataFrame))

  override def listeners: WebSocketListeners = this._listeners

  override def close(): Unit = {
    send(closeFrame)
    self ! PoisonPill
  }

  /** User-defined websocket handler. */
  override def websockets: Receive = ???
}
////////////////////////////////////////////////////////////////////////////////