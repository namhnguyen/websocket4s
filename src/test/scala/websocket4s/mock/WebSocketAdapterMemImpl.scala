package websocket4s.mock

import org.slf4j.LoggerFactory
import websocket4s.core.{WebSocketListeners, WebSocketAdapter}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.util.Random

////////////////////////////////////////////////////////////////////////////////
/**
 * Connect 2 WebSocketAdapterMem together
 * Created by namnguyen on 5/27/15.
 */
class WebSocketAdapterMemImpl
(
  val name:String
, val networkLatencyMin:Int=0
, val networkLatencyMax:Int=0
) extends WebSocketAdapter{

  val logger = LoggerFactory.getLogger(this.getClass)

  private val latencyRange = networkLatencyMax-networkLatencyMin
  private def randomLatency = networkLatencyMin+Random.nextInt(latencyRange+1)

  def connect(other:WebSocketAdapterMemImpl):Unit = {
    if (connectTo.isEmpty){
      connectTo = Some(other)
      other.connectTo = Some(this)
      val curLatency = randomLatency
      this.listeners.map(l =>
        Future{
          blocking {
            Thread.sleep(curLatency)
          }
          l.onConnect()
        }.onFailure{ case e => logger.error(e.getMessage,e)})
      other.listeners.map(l =>
        Future{
          blocking {
            Thread.sleep(curLatency)
          }
          l.onConnect()
        }.onFailure{ case e => logger.error(e.getMessage,e)})
    }else{
      throw new Exception("Connection is in use")
    }
  }
  //----------------------------------------------------------------------------
  override def push(dataFrame: String): Unit =
    connectTo.map(other => other.listeners.map(l =>
      Future{
        blocking {
          Thread.sleep(randomLatency)
        }
        l.receive(dataFrame)
      }.onFailure{ case e => logger.error(e.getMessage,e)}))
  //----------------------------------------------------------------------------
  override def listeners: WebSocketListeners = webSocketListeners
  //----------------------------------------------------------------------------
  override def close(): Unit = if (connectTo.isDefined) {
    val other = this.connectTo.get
    other.listeners.map(l => Future{
      blocking {
        Thread.sleep(randomLatency)
      }
      l.onClose("CLOSE")
    })
    this.listeners.map(l => Future{
      blocking {
        Thread.sleep(randomLatency)
      }
      l.onClose("CLOSE")
    })
    this.connectTo.get.connectTo = None //disconnect other endpoint
    this.connectTo = None

  }
  //----------------------------------------------------------------------------
  private var connectTo:Option[WebSocketAdapterMemImpl] = None
  private val webSocketListeners = new WebSocketListeners()
  //----------------------------------------------------------------------------

}
////////////////////////////////////////////////////////////////////////////////