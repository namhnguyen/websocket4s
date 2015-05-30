package websocket4s.client

import java.util.concurrent.{TimeoutException, TimeUnit}
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import websocket4s.core.JsonUtils._
import websocket4s.core._
import scala.collection.concurrent.TrieMap
import scala.concurrent.{Promise, Future}
import scala.concurrent.ExecutionContext.Implicits.global
////////////////////////////////////////////////////////////////////////////////
/**
 * Created by namnguyen on 5/24/15.
 */
final class ClientEndPoint(webSocketAdapter: WebSocketAdapter) extends EndPoint{
  self =>
  //----------------------------------------------------------------------------
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val askTable = new TrieMap[String,Promise[Response]]()
  //used for storing ClientRequestAny Ids
  private val timeOut = 10 //seconds
  private val timeoutException = new TimeoutException()
  private var messageReceived:Option[(Message)=>Unit] = None
  private var requestReceived:Option[(Request)=>Future[String]] = None
  //----------------------------------------------------------------------------
  private val subscriber = webSocketAdapter.listeners.subscribe(
    new WebSocketListener {

    override def onConnect(): Unit = {}

    override def onClose(reason: String): Unit = {}

    override def receive(dataFrame: String): Unit = {
      try {
        val json = deserialize(dataFrame)
        val transportPackage = json.extract[TransportPackage]
        transportPackage.`type` match {

          case TransportPackage.Type.Message |
               TransportPackage.Type.RouteMessage => {
            runMessageReceived(transportPackage)
          }

          case TransportPackage.Type.Request      |
               TransportPackage.Type.RouteRequest |
               TransportPackage.Type.RouteRequestAny =>
            if (transportPackage.id.isDefined) {
              if (transportPackage.from.isDefined) {
                self.runRequestReceived(transportPackage)
              } else {
                logger.error("Request Does not have from address to reply to!!!")
              }
            } else
              logger.error("Request Does not have Id!!!")

          case TransportPackage.Type.Response      |
               TransportPackage.Type.RouteResponse |
               TransportPackage.Type.RouteResponseAny =>
            if (transportPackage.id.isDefined)
              self.runResponseReceived(transportPackage)
            else
              logger.error("Response message does not have ID cannot complete a promise!!!")
        }

      }catch {
        case exc:Exception =>  logger.warn(exc.getMessage,exc)
      }
    }

  })
  //----------------------------------------------------------------------------
  /**
   * callback when a message is received on this endpoint. This method is triggered
   * when other EndPoint tells this EndPoint what to do.
   *
   * @param f: a function which will be triggered when a message is received
   */
  override def onMessageReceived(f: Option[(Message) => Unit]): Unit =
    messageReceived = f
  //----------------------------------------------------------------------------
  /**
   * callback when a request is received on this endpoint. This method is triggered
   * when other EndPoint asks this EndPoint what to do and request a response.
   * This method should be non-blocking
   * @param f:Request=>Future[String] a function which will be triggered when a
   *         request is received
   * @return
   */
  override def onRequestReceived(f: Option[(Request) => Future[String]]): Unit =
    requestReceived = f
  //----------------------------------------------------------------------------
  /**
   * Use this function when this EndPoint knows exact the ID of other EndPoint to tell
   * a message to
   * @param id
   * @param message
   */
  override def tell(id: String, message: String): Unit = {
    val transportPackage = TransportPackage(
      None,Some(id),None,None,message,None,TransportPackage.Type.RouteMessage)
    this.pushObject(transportPackage)
  }
  //----------------------------------------------------------------------------
  /**
   * Tell the immediate EndPoint which is currently connect to the current EndPoint
   * a message
   * @param message
   */
  override def tell(message: String): Unit = {
    val transportPackage = TransportPackage(
      None,None,None,None,message,None,TransportPackage.Type.Message)
    this.pushObject(transportPackage)
  }
  //----------------------------------------------------------------------------
  /**
   * When this EndPoint want to push a message to other EndPoints whose tags are
   * given in the Tag Set
   * @param tags
   * @param message
   */
  override def tellTags(tags: Set[String], message: String): Unit = {
    val transportPackage = TransportPackage(
      None,None,Some(tags),None,message,None,TransportPackage.Type.RouteMessage)
    this.pushObject(transportPackage)
  }
  //----------------------------------------------------------------------------
  /**
   * Use this function when this Endpoint knows exact the ID of other EndPoint to request
   * something and demand a Future Response
   * @param id
   * @param request
   * @return
   */
  override def ask(id: String, request: String): Future[Response] = {
    val promise = Promise[Response]()

    val requestId = WebSocketSystem.GUID.randomGUID
    askTable += ((requestId,promise))
    timeoutKeys(timeOut,TimeUnit.SECONDS,requestId)
    val transportPackage = TransportPackage(
        from = None
      , to = Some(id)
      , tags = None
      , id = Some(requestId)
      , data = request
      , `type` = TransportPackage.Type.RouteRequest)
    pushObject(transportPackage)
    promise.future
  }
  //----------------------------------------------------------------------------
  /**
   * Ask the immediate EndPoint which is currently connect to the current EndPoint
   * and demand a Future Response
   * @param request
   * @return
   */
  override def ask(request: String): Future[Response] = {
    val promise = Promise[Response]()
    val requestId = WebSocketSystem.GUID.randomGUID
    askTable += ((requestId,promise))
    timeoutKeys(timeOut,TimeUnit.SECONDS,requestId)
    val transportPackage = TransportPackage(
       from=None
      ,to=None
      ,tags = None
      ,id = Some(requestId)
      ,data = request
      ,`type` = TransportPackage.Type.Request)
    self.pushObject(transportPackage)
    promise.future
  }
  //----------------------------------------------------------------------------
  /**
   * When this EndPoint want to ask something from other EndPoints whose tags are
   * given in the Tag Set, only the first response from any EndPoint will be returned
   * @param tags
   * @param request
   * @return
   */
  override def askTags(tags: Set[String], request: String): Future[Response] = {
    val promise = Promise[Response]()
    val requestId = WebSocketSystem.GUID.randomGUID
    askTable += ((requestId,promise))
    timeoutKeys(timeOut,TimeUnit.SECONDS,requestId)
    val transportPackage = TransportPackage(
      from=None
      ,to=None
      ,tags = Some(tags)
      ,id = Some(requestId)
      ,data = request
      ,`type` = TransportPackage.Type.RouteRequestAny)
    self.pushObject(transportPackage)
    promise.future
  }
  //----------------------------------------------------------------------------

  /**
   * Use WebSocketAdapter to serialize and push object to the client
   * @param any
   */
  def pushObject(any:AnyRef):Unit = webSocketAdapter.push(serialize(any))
  //----------------------------------------------------------------------------
  private def timeoutKeys(timeOut:Int,unit:TimeUnit,keys:String*):Unit =
    keys.map(key => {
      askTable.get(key).map( promise =>{
        WebSocketSystem.scheduler.schedule(new Runnable {
          override def run(): Unit = {
            self.askTable -= key
            promise.tryFailure(timeoutException)
          }
        },timeOut,TimeUnit.SECONDS)
      })
    })
  //----------------------------------------------------------------------------
  private def runRequestReceived(transportPackage: TransportPackage):Unit = {
    if (requestReceived.isDefined) {
      val f = requestReceived.get
      val request = Request(
          id = transportPackage.id.get
        , data = transportPackage.data
        , senderId = transportPackage.from
        , receiverId = transportPackage.to
        , forTags = transportPackage.tags
      )

      val futureResult = f(request)
      val responseType = if(transportPackage.`type`==TransportPackage.Type.RouteRequestAny)
        TransportPackage.Type.RouteResponseAny
      else if (transportPackage.`type`==TransportPackage.Type.RouteRequest)
        TransportPackage.Type.RouteResponse
      else TransportPackage.Type.Response

      futureResult.onSuccess[Unit] {
        case r: String => {
          val respondTransportPackage = TransportPackage(
            from = request.receiverId
            , to = request.senderId
            , tags = request.forTags
            , id = Some(request.id)
            , data = r
            , error = None
            , `type` = responseType
          )
          pushObject(respondTransportPackage)
        }
      }

      futureResult.onFailure[Unit] {
        case e: Throwable => {
          val respondTransportPackage = TransportPackage(
            from = request.receiverId
            , to = request.senderId
            , tags = request.forTags
            , id = Some(request.id)
            , data = ""
            , error = Some(e.getMessage)
            , `type` = responseType
          )
          logger.error(e.getMessage, e)
          pushObject(respondTransportPackage)
        }
      }
    }
  }
  //----------------------------------------------------------------------------
  private def runMessageReceived(transportPackage: TransportPackage):Unit =
    if (messageReceived.isDefined) {
      val f = messageReceived.get
      val message = Message(
        data = transportPackage.data
        , senderId = transportPackage.from
        , receiverId = transportPackage.to
        , forTags = transportPackage.tags
      )
      f(message)
    }
  //----------------------------------------------------------------------------
  private def runResponseReceived(transportPackage: TransportPackage):Unit = {
    //if a response is received, it needs to match the id in the ask
    //table and complete a promise
    val requestId = transportPackage.id.get
    val somePromise = askTable.remove(requestId)
    if (somePromise.isDefined){
      val promise = somePromise.get
      val response = Response(
        ok = transportPackage.error.isEmpty
        , data = transportPackage.data
        , endPointId = transportPackage.from
        , exception = transportPackage.error.map(err => new Exception(err))
      )
      if (response.ok)
        promise.trySuccess(response)
      else
        promise.tryFailure(response.exception.get)
    }
  }
  //----------------------------------------------------------------------------
}
////////////////////////////////////////////////////////////////////////////////