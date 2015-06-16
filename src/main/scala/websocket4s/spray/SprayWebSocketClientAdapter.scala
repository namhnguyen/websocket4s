package websocket4s.spray

import java.util.concurrent.TimeUnit

import akka.actor._
import spray.can.websocket.UpgradedToWebSocket
import spray.can.websocket.frame.{TextFrame, CloseFrame}
import websocket4s.core.{WebSocketSystem, WebSocketListener, WebSocketListeners, WebSocketAdapter}
import websocket4s.spray.BufferActor.RemoveConnection
import scala.concurrent.duration._


////////////////////////////////////////////////////////////////////////////////
/**
 * Created by namnguyen on 6/15/15.
 */
class SprayWebSocketClientAdapter
(
  host:String,port:Int,path:String = "/"
  ,var retry:Boolean = false
)(implicit actorSystem:ActorSystem)
  extends WebSocketAdapter{ self =>
  //----------------------------------------------------------------------------
  private val _retryDelay = Duration(5,TimeUnit.SECONDS)
  private var _purposelyClose = false
  private val _listeners = new WebSocketListeners()
  private val _webSocketListener = listeners.subscribe(new WebSocketListener{
    //--------------------------------------------------------------------------
    override def onConnect(): Unit = {  } //client connect
    //--------------------------------------------------------------------------
    override def onClose(reason: String): Unit = {
      //client close, if the close is not purposely checked, the adapter should
      //retry
      //schedule to retry (no Thread.sleep to avoid blocking)
      if (!_purposelyClose){
        //retry
        //the _client in postStop state and should be closed eventually.
        bufferActor ! RemoveConnection
        WebSocketSystem.scheduler.schedule(new Runnable {
          override def run(): Unit = {
            _client = actorSystem.actorOf(
                Props(classOf[ClientWorker],host,port,path,self))
          }
        },_retryDelay.toMillis,TimeUnit.MILLISECONDS)
      }
    }
    //--------------------------------------------------------------------------
    override def receive(dataFrame: String): Unit = {   }
    //--------------------------------------------------------------------------
  })

  //connection starts here
  val bufferActor = actorSystem.actorOf(Props[BufferActor])
  private var _client = actorSystem.actorOf(
    Props(classOf[ClientWorker],host,port,path,this))
  //----------------------------------------------------------------------------
  override def push(dataFrame: String): Unit =
    bufferActor ! TextFrame(dataFrame)
  //----------------------------------------------------------------------------
  override def listeners: WebSocketListeners = _listeners
  //----------------------------------------------------------------------------
  override def close(): Unit = {
    _purposelyClose = true
    _client ! CloseFrame()
  }
  //----------------------------------------------------------------------------
}
////////////////////////////////////////////////////////////////////////////////
object BufferActor {
  object RemoveConnection
}
////////////////////////////////////////////////////////////////////////////////
class BufferActor extends Actor {
  private var connection:Option[ActorRef] = None

  override def receive = {
    case conn:ActorRef => this.connection = Some(conn)
    case textFrame:TextFrame =>
      if (connection.isDefined)
        connection.get ! textFrame
      else
        self ! textFrame
    case RemoveConnection => this.connection = None
  }
}
////////////////////////////////////////////////////////////////////////////////
class ClientWorker(host:String,port:Int,path:String,adapter: SprayWebSocketClientAdapter)
  extends WebSocketClient(host,port,path){
  val closeFrame = CloseFrame()
  //----------------------------------------------------------------------------
  override def websockets: Receive = {
    case UpgradedToWebSocket => {
      //send the connection to the adapter.bufferActor to handle sending messages
      adapter.bufferActor ! this.connection
      for (listener <- adapter.listeners) listener.onConnect()
    }
    case c:CloseFrame => connection ! c
    case s:String => for (listener <- adapter.listeners) listener.receive(s)
    case t:TextFrame => {
      val text = t.payload.utf8String
      for (listener <- adapter.listeners) listener.receive(text)
    }
  }
  //----------------------------------------------------------------------------
  override def postStop():Unit = {
    super.postStop()
    adapter.bufferActor ! RemoveConnection
    for (listener <- adapter.listeners)
      listener.onClose("WebSocket Server Connection Kill")
    //connection ! closeFrame
  }
  //----------------------------------------------------------------------------
}
////////////////////////////////////////////////////////////////////////////////