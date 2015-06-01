package websocket4s.core

import scala.concurrent.Future
import scala.concurrent.duration.Duration
////////////////////////////////////////////////////////////////////////////////
/**
 * Created by namnguyen on 6/1/15.
 */
trait PushEndPoint {
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
  //----------------------------------------------------------------------------
  /**
   *
   * @param tags
   * @param request
   * @param duration
   * @return
   */
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
  /**
   *
   * @param id
   * @param request
   * @param duration
   * @return
   */
  def ask(id:String,request:String,duration:Duration):Future[Response]
  //----------------------------------------------------------------------------

}
////////////////////////////////////////////////////////////////////////////////