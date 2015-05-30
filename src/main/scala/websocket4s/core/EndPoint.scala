package websocket4s.core

import scala.concurrent.Future
import scala.concurrent.duration.Duration

////////////////////////////////////////////////////////////////////////////////
/**
 * Define a Web Socket EndPoint behaviours
 * Created by namnguyen on 5/23/15.
 */
trait EndPoint {
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
   * When this EndPoint want to push a message to other EndPoints whose tags are
   * given in the Tag Set
   * @param tags
   * @param message
   */
  def tellTags(tags:Set[String],message:String):Unit
  //----------------------------------------------------------------------------
  /**
   * When this EndPoint want to ask something from other EndPoints whose tags are
   * given in the Tag Set, only the first response from any EndPoint will be returned
   * @param tags
   * @param request
   * @return
   */
  def askTags(tags:Set[String],request:String):Future[Response]
  def askTags(tags:Set[String],request:String,duration: Duration):Future[Response]
  //----------------------------------------------------------------------------
  //Remove because this is not applicable for Client EndPoint
//  /**
//   * Use this function when this EndPoint want to ask other EndPoints something
//   * and require all the Response to be returned
//   * @param tags
//   * @param request
//   * @return
//   */
//  def askAllTags(tags:Set[String],request:String):Future[List[Future[Response]]]
  //----------------------------------------------------------------------------
  /**
   * Use this function when this EndPoint knows exact the ID of other EndPoint to tell
   * a message to
   * @param id
   * @param message
   */
  def tell(id:String,message:String):Unit
  //----------------------------------------------------------------------------
  /**
   * Use this function when this Endpoint knows exact the ID of other EndPoint to request
   * something and demand a Future Response
   * @param id
   * @param request
   * @return
   */
  def ask(id:String,request:String):Future[Response]
  //----------------------------------------------------------------------------
  def ask(id:String,request:String,duration:Duration):Future[Response]
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
  def ask(request:String,duration:Duration):Future[Response]
  //----------------------------------------------------------------------------

}
////////////////////////////////////////////////////////////////////////////////