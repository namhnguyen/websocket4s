package websocket4s.server

import java.util.concurrent.{TimeoutException, TimeUnit}
import akka.actor.{Props, Actor, ActorSystem}
import org.slf4j.LoggerFactory
import websocket4s.core._
import scala.collection.concurrent.TrieMap
import scala.concurrent.{Promise, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
////////////////////////////////////////////////////////////////////////////////
/**
 * ServerPush is used by a server to push data to any other ClientEndPoint.
 * ServerPush can be used by REST Service in order to communicate with ClientEndPoint in
 * short period of time (no open connection).
 *
 * ServerPush cannot be used to control other ServerEndPoint
 *
 * Created by namnguyen on 6/1/15.
 */
class ServerPush()
( implicit val actorSystem: ActorSystem
, implicit val actorRegister:ActorRegister
) extends PushEndPoint {
  //----------------------------------------------------------------------------
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val timeOut = Duration(10,TimeUnit.SECONDS) //seconds
  private val timeoutException = new TimeoutException()
  private val askTable = new TrieMap[String,Promise[Response]]()
  private lazy val actorRef = actorSystem.actorOf(Props(new PushActor(this)))
  private lazy val id = RoutingActorSystem.getActorPath(actorRef)
  //----------------------------------------------------------------------------
  /**
   * the current ServerPush object will broadcast message to all other ClientEndPoints
   * whose tags are given with a given message. ClientEndPoints will receive a message
   * not a RoutingServerEndPoints that route message to the ClientEndPoints
   * @param tags
   * @param message
   */
  override def tellTags(tags: Set[String], message: String): Unit =
    actorRegister.queryEntries(tags).map(someEntries => {
      val transportPackage = TransportPackage(
        from=Some(this.id)
      , to = None
      , tags = Some(tags)
      , id = None
      , data = message
      , `type` = TransportPackage.Type.RouteMessage
      )
      for(entry <- someEntries if !entry.id.equals(this.id))
        actorSystem.actorSelection(entry.id) ! TransportPackage.encodeForActor(transportPackage)
    })

  //----------------------------------------------------------------------------
  /**
   * Use this function when this EndPoint knows exact the ID of other EndPoint to tell
   * a message to
   * @param id
   * @param message
   */
  override def tell(id: String, message: String): Unit = {
    val transportPackage = TransportPackage(
      from = Some(this.id)
      , to = Some(id)
      , tags = None
      , id = None
      , data = message
      , `type` = TransportPackage.Type.RouteMessage)
    actorSystem.actorSelection(transportPackage.to.get) ! TransportPackage.encodeForActor(transportPackage)
  }
  //----------------------------------------------------------------------------
  /**
   * Use this function when this Endpoint knows exact the ID of other EndPoint to request
   * something and demand a Future Response
   * @param id
   * @param request
   * @return
   */
  override def ask(id: String, request: String): Future[Response] =
    ask(id,request,timeOut)
  //----------------------------------------------------------------------------
  /**
   *
   * @param id
   * @param request
   * @param duration
   * @return
   */
  override def ask(id: String, request: String, duration: Duration): Future[Response] = {
    val promise = Promise[Response]()
    val requestId = WebSocketSystem.GUID.randomGUID
    askTable += ((requestId, promise))
    timeoutPromises(duration.toMillis, TimeUnit.MILLISECONDS, requestId)
    val transportPackage = TransportPackage(
      from = Some(this.id)
      , to = Some(id)
      , tags = None
      , id = Some(requestId)
      , data = request
      , `type` = TransportPackage.Type.RouteRequest)
    actorSystem.actorSelection(id) ! TransportPackage.encodeForActor(transportPackage)
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
  override def askTags(tags: Set[String], request: String): Future[Response] =
    askTags(tags,request,timeOut)
  //----------------------------------------------------------------------------
  /**
   *
   * @param tags
   * @param request
   * @param duration
   * @return
   */
  override def askTags(tags: Set[String], request: String, duration: Duration): Future[Response] = {
    val promise = Promise[Response]()
    val requestId = WebSocketSystem.GUID.randomGUID
    askTable += ((requestId,promise))
    timeoutPromises(duration.toMillis,TimeUnit.MILLISECONDS,requestId)
    actorRegister.queryEntries(tags).map(entryList => {
      val transportPackage = TransportPackage(
        from = Some(this.id)
        , to = None
        , tags = Some(tags)
        , id = Some(requestId)
        , data = request
        , `type` = TransportPackage.Type.RouteRequest)
      val serializedData = TransportPackage.encodeForActor(transportPackage)
      if (transportPackage.from.isDefined){ //from should always be defined, and should never be in the entry
        val senderId = transportPackage.from.get
        for (entry <- entryList if !entry.id.equals(senderId))
          actorSystem.actorSelection(entry.id) ! serializedData
      } else {
        for (entry <- entryList)
          actorSystem.actorSelection(entry.id) ! serializedData
      }
    })
    promise.future
  }
  //----------------------------------------------------------------------------

  private def timeoutPromises(timeOut:Long,unit:TimeUnit,keys:String*):Unit =
    keys.map(key => {
      askTable.get(key).map( promise =>{
        WebSocketSystem.scheduler.schedule(new Runnable {
          override def run(): Unit = {
            ServerPush.this.askTable -= key
            promise.tryFailure(timeoutException)
          }
        },timeOut,unit)
      })
    })
  //----------------------------------------------------------------------------
  private def routeResponseReceived(transportPackage: TransportPackage):Unit = {
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
  //////////////////////////////////////////////////////////////////////////////
  class PushActor(serverPush: ServerPush) extends Actor {
    private val logger = LoggerFactory.getLogger(this.getClass)
    //--------------------------------------------------------------------------
    override def receive = {
      case data:String =>
        try {
          val transportPackage = TransportPackage.decodeForActor(data)
          transportPackage.`type` match {
            case TransportPackage.Type.RouteResponse =>
              serverPush.routeResponseReceived(transportPackage)
          }
        } catch {
          case exc:Exception =>  logger.warn(exc.getMessage,exc)
        }
    }
    //--------------------------------------------------------------------------
  }
  //////////////////////////////////////////////////////////////////////////////
  //----------------------------------------------------------------------------
}
////////////////////////////////////////////////////////////////////////////////