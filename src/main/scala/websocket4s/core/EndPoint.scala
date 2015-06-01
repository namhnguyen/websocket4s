package websocket4s.core

import scala.concurrent.Future
import scala.concurrent.duration.Duration

////////////////////////////////////////////////////////////////////////////////
/**
 * Define a Web Socket EndPoint behaviours
 * Created by namnguyen on 5/23/15.
 */
trait EndPoint extends PushEndPoint {
  //----------------------------------------------------------------------------
  /**
   * callback when a message is received on this endpoint. This method is triggered
   * when other EndPoint tells this EndPoint what to do.
   *
   * @param f: a function which will be triggered when a message is received
   */
  def onMessageReceived(f:Option[Message=>Unit]):Unit
  //----------------------------------------------------------------------------
  /**
   * callback when a request is received on this endpoint. This method is triggered
   * when other EndPoint asks this EndPoint what to do and request a response.
   * This method should be non-blocking
   * @param f:Request=>Future[String] a function which will be triggered when a
   *         request is received
   * @return
   */
  def onRequestReceived(f:Option[Request=>Future[String]]):Unit
  //----------------------------------------------------------------------------
  /**
   * Tell the immediate EndPoint which is currently connect to the current EndPoint
   * a message
   * @param message
   */
  def tell(message:String):Unit
  //----------------------------------------------------------------------------
  /**
   * Ask the immediate EndPoint which is currently connect to the current EndPoint
   * and demand a Future Response
   * @param request
   * @return
   */
  def ask(request:String):Future[Response]
  //----------------------------------------------------------------------------
  /**
   * 
   * @param request
   * @param duration
   * @return
   */
  def ask(request:String,duration:Duration):Future[Response]
  //----------------------------------------------------------------------------
}
////////////////////////////////////////////////////////////////////////////////