package websocket4s.server

import java.util.concurrent.{TimeUnit, TimeoutException}

import akka.actor._
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import websocket4s.core._
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.HashMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Promise, Future}
import websocket4s.core.JsonUtils._
////////////////////////////////////////////////////////////////////////////////
/**
 * Most of the time ech ServerEndPoint has to represent a ClientEndPoint which
 * directly connect to it over Web Socket.
 *
 * When a ServerEndPoint is created, a new AKKA Actor also is created and register
 * to a centralized ActorRegister table.
 *
 * ServerEndPoint should have tags and the Tags should be registered to the ActorRegister too.
 * If there is no tags, the only way to push message from one ServerEndPoint to another
 * ServerEndPoint is to use its UID.
 *
 * EndPoint needs to keep track Asked Request using a table
 *
 * SeverEndPoint plays 2 roles: Router and Server
 *  - As a router it forwards messages which not purposely send to it to the destination: other
 *      actor or the client it is connecting
 *
 *  - As a Server, it report directly to its connected client
 *
 * Created by namnguyen on 5/24/15.
 */
final class ServerEndPoint
  (val webSocketAdapter: WebSocketAdapter,var tags:Set[String] = Set())
  ( implicit val actorSystem: ActorSystem
   ,implicit val actorRegister:ActorRegister
  ) extends EndPoint{ self =>
  //----------------------------------------------------------------------------
  private val logger = LoggerFactory.getLogger(this.getClass)
  private val timeOut = Duration(10,TimeUnit.SECONDS) //seconds
  private val timeoutException = new TimeoutException()
  private var proxyActorRef:Option[ActorRef] = None
  private val askTable = new TrieMap[String,Promise[Response]]()
  //used for storing ClientRequestAny Ids
  private val clientRequestAnySet = new TrieMap[String,String]()
  private var messageReceived:Option[(Message)=>Unit] = None
  private var requestReceived:Option[(Request)=>Future[String]] = None
  //----------------------------------------------------------------------------
  val subscriber = webSocketAdapter.listeners.subscribe(new WebSocketListener {
    override def onConnect(): Unit = {
      self.proxyActorRef = Some(self.actorSystem.actorOf(Props(new ProxyActor(self))))
      val id = ServerEndPoint.getActorPath(self.proxyActorRef.get)
      self.actorRegister
        .register(new ActorRegisterEntry(id = id,path = id,self.tags))
    }

    override def onClose(reason: String): Unit = {
      if (self.proxyActorRef.isDefined) {
        val id = ServerEndPoint.getActorPath(self.proxyActorRef.get)
        self.actorRegister.unregister(id)
        self.proxyActorRef.map(_ ! PoisonPill)
      }
    }

    override def receive(dataFrame: String): Unit = {
      try {
        val json = deserialize(dataFrame)
        val transportPackage = json.extract[TransportPackage]
        transportPackage.`type` match {
          case TransportPackage.Type.RouteResponse |
               TransportPackage.Type.RouteResponseAny=> {
            //clientResponse always based on to
            if (transportPackage.to.isDefined){
              val transportPackageWithFrom = transportPackage.copy(from=self.id)
              val toAddress = transportPackageWithFrom.to.get
              actorRegister.getEntry(toAddress).map(someEntry =>
                someEntry.map(entry =>
                  actorSystem.actorSelection(entry.path) ! serialize(transportPackageWithFrom)
                )
              )
            }else {
              logger.warn("Response does not have TO Address! Response won't be sent")
            }
          }

          case TransportPackage.Type.RouteRequestAny =>
            //when a ClientRequestAny is received by a websocket
            //a ProxyServer will store the requestId temporary and when a ClientResponseAny
            //package receieved by the Proxy, the first receiving message will be forwarded to the
            //client. A server can also timeout if it waits for too long. After timeout,
            //any received ClientResponseAny Package will be dropped
            if (transportPackage.id.isDefined){
              val packageId = transportPackage.id.get
              self.clientRequestAnySet += ((packageId,packageId))

              val scheduled = WebSocketSystem.scheduler.schedule(new Runnable {
                override def run(): Unit = self.clientRequestAnySet.remove(packageId)
              },timeOut.toMillis,TimeUnit.MILLISECONDS)

              if (transportPackage.to.isDefined||transportPackage.tags.isDefined){
                sendPackage(transportPackage)
              }else {
                self.clientRequestAnySet.remove(packageId)
                scheduled.cancel(false)
                logger.warn("Message/Request does not have To address or Tags ! Response won't be sent")
              }
            }else{
              logger.warn("Request Package without ID will be swallowed by the server!!!")
            }


          case TransportPackage.Type.RouteRequest =>
            if (transportPackage.id.isDefined)
              sendPackage(transportPackage)
            else
              logger.warn("Request Package without ID will be swallowed by the server!!!")

          case TransportPackage.Type.RouteMessage => sendPackage(transportPackage)

          case TransportPackage.Type.Message => self.runMessageReceived(transportPackage)

          case TransportPackage.Type.Request => self.runRequestReceived(transportPackage,fromClient = true)

          case TransportPackage.Type.Response => self.runResponseReceived(transportPackage)

        }
      }catch {
        case exc:Exception =>  logger.warn(exc.getMessage,exc)
      }
    }

    private def sendPackage(transportPackage: TransportPackage):Unit={
      if (transportPackage.to.isDefined){
        val toAddress = transportPackage.to.get
        val transportPackageWithFrom = transportPackage.copy(from = self.id)
        val serializedDataFrame = serialize(transportPackageWithFrom)
        actorRegister.getEntry(toAddress).map(someEntry =>
          someEntry.map(entry => actorSystem.actorSelection(entry.path) ! serializedDataFrame)
        )
      }else if (transportPackage.tags.isDefined){
        val tags = transportPackage.tags.get
        val transportPackageWithFrom = transportPackage.copy(from = self.id)
        val serializedDataFrame = serialize(transportPackageWithFrom)
        actorRegister.queryEntries(tags).map(entryList=>
          entryList.map(entry => actorSystem.actorSelection(entry.path) ! serializedDataFrame)
        )
      }else {
        logger.warn("Message/Request does not have To address or Tags ! Response won't be sent")
      }
    }
  })
  //----------------------------------------------------------------------------
  /**
   * Use WebSocketAdapter to serialize and push object to the client
   * @param any
   */
  def pushObject(any:AnyRef):Unit = webSocketAdapter.push(serialize(any))
  //----------------------------------------------------------------------------
  /**
   * Unique ID of a ServerEndPoint, we can use the Actor Absolute Path as ServerEndPoint
   * Unique ID.
   * @return
   */
  def id:Option[String] = proxyActorRef.map(ref => ServerEndPoint.getActorPath(ref)) //proxyActorRef.map(_.path.toString)
  //----------------------------------------------------------------------------
  /**
   * If EndPoint already connect to other EndPoint, it will update the actorRegister
   * table, otherwise, only update the current tags (in memory)
   * @param tags
   * @return
   */
  def changeTags(tags:Set[String]):Future[Boolean] =
    (if (id.isDefined)
      actorRegister.updateTags(id.get,tags).map(_.isDefined)
    else Future(true)).map(v=> { if (v) this.tags = tags
      v
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
    actorRegister.getEntry(id).map( someEntry=> {
      someEntry.map(entry => {
        val transportPackage = TransportPackage(
          from=self.id
          ,to=Some(entry.id)
          ,tags = None
          ,id = None
          ,data = message
          , `type` = TransportPackage.Type.Message)
        actorSystem.actorSelection(entry.path) ! serialize(transportPackage)
      })
    })
  }
  //----------------------------------------------------------------------------
  /**
   * Tell the immediate EndPoint which is currently connect to the current EndPoint
   * a message
   * @param message
   */
  override def tell(message: String): Unit = {
    val transportPackage = TransportPackage(id,None,None,None,message)
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
    actorRegister.queryEntries(tags).map( someEntries => {
      val transportPackage = TransportPackage(
        from=self.id
        ,to = None
        ,tags = Some(tags)
        ,id = None
        ,data = message
        , `type` = TransportPackage.Type.Message)
      val serializedData = serialize(transportPackage)
      for (entry <- someEntries)
        actorSystem.actorSelection(entry.path) ! serializedData
    })
  }
  //----------------------------------------------------------------------------
  /**
   * Use this function when this Endpoint knows exact the ID of other EndPoint to request
   * something and demand a Future Response.
   * This method will query ActorRegister by Id and send Message to the Actor
   * @param id
   * @param request
   * @return
   */
  override def ask(id: String, request: String,duration: Duration): Future[Response] = {
    val promise = Promise[Response]()
    timeoutPromises(duration.toMillis,TimeUnit.MILLISECONDS,promise)

    actorRegister.getEntry(id).map(someEntry => {
      if (someEntry.isDefined) {
        val requestId = WebSocketSystem.GUID.randomGUID
        askTable += ((requestId,promise))
        timeoutPromises(duration.toMillis,TimeUnit.MILLISECONDS,requestId)
        val entry = someEntry.get
        val transportPackage = TransportPackage(
          from = self.id
          , to = Some(id)
          , tags = None
          , id = Some(requestId)
          , data = request
          , `type` = TransportPackage.Type.Request)
        val serializedData = serialize(transportPackage)
        actorSystem.actorSelection(entry.path) ! serializedData
      } else {
        promise.tryFailure(new ActorIdNotFoundException(id))
      }
    })
    promise.future
  }
  //----------------------------------------------------------------------------
  override def ask(id: String, request: String): Future[Response] =
    ask(id,request,timeOut)
  //----------------------------------------------------------------------------
  /**
   * Use this function when this EndPoint want to ask other EndPoints something
   * and require all the Response to be returned
   * @param tags
   * @param request
   * @return
   */
  def askAllTags(tags: Set[String], request: String,duration:Duration): Future[List[Future[Response]]] = {
    val promise = Promise[List[Future[Response]]]()

    WebSocketSystem.scheduler.schedule(new Runnable {
      override def run(): Unit = promise.tryFailure(timeoutException)
    },duration.toMillis,TimeUnit.MILLISECONDS)

    actorRegister.queryEntries(tags).map(entryList=>{
      val listFuture = entryList.map(entry=>{
        val requestId = WebSocketSystem.GUID.randomGUID
        val entryPromise = Promise[Response]()
        askTable += ((requestId,entryPromise))
        timeoutPromises(duration.toMillis,TimeUnit.MILLISECONDS,requestId)
        val transportPackage = TransportPackage(
          from = self.id
          , to = None
          , tags = Some(tags)
          , id = Some(requestId)
          , data = request
          , `type` = TransportPackage.Type.Request)
        val serializedData = serialize(transportPackage)
        actorSystem.actorSelection(entry.path) ! serializedData
        entryPromise.future
      })
      promise.trySuccess(listFuture)
    })

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
  override def askTags(tags: Set[String], request: String,duration:Duration): Future[Response] = {
    val promise = Promise[Response]()
    val requestId = WebSocketSystem.GUID.randomGUID
    timeoutPromises(duration.toMillis,TimeUnit.MILLISECONDS,promise)
    actorRegister.queryEntries(tags).map(entryList => {
      val transportPackage = TransportPackage(
        from = self.id
        , to = None
        , tags = Some(tags)
        , id = Some(requestId)
        , data = request
        , `type` = TransportPackage.Type.Request)
      val serializedData = serialize(transportPackage)
      for (entry <- entryList)
        actorSystem.actorSelection(entry.path) ! serializedData
    })

    promise.future
  }
  //----------------------------------------------------------------------------
  override def askTags(tags: Set[String], request: String): Future[Response]
    = askTags(tags,request,timeOut)
  //----------------------------------------------------------------------------
  /**
   * Ask the immediate EndPoint which is currently connect to the current EndPoint
   * and demand a Future Response
   * @param request
   * @return
   */
  override def ask(request: String,duration:Duration): Future[Response] = {
    val requestId = WebSocketSystem.GUID.randomGUID
    val promise = Promise[Response]()
    askTable += ((requestId,promise))
    val transportPackage = TransportPackage(
      from=id
      ,to=None
      ,tags = None
      ,id = Some(requestId)
      ,data = request
      , `type` = TransportPackage.Type.Request)
    self.pushObject(transportPackage)
    timeoutPromises(duration.toMillis,TimeUnit.MILLISECONDS,requestId)
    promise.future
  }
  //----------------------------------------------------------------------------
  override def ask(request: String): Future[Response] =
    ask(request,timeOut)
  //----------------------------------------------------------------------------
  private def timeoutPromises(timeOut:Long,unit:TimeUnit,promises:Promise[Response]*):Unit =
    promises.map(promise=> {
      WebSocketSystem.scheduler.schedule(new Runnable {
        override def run(): Unit = promise.tryFailure(timeoutException)
      },timeOut,TimeUnit.SECONDS)
    })
  //----------------------------------------------------------------------------
  private def timeoutPromises(timeOut:Long,unit:TimeUnit,keys:String*):Unit =
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
  private def runRequestReceived(transportPackage: TransportPackage,fromClient:Boolean):Unit = {
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

      futureResult.onSuccess[Unit] {
        case r: String => {
          val transportPackage = TransportPackage(
            from = request.receiverId
            , to = request.senderId
            , tags = request.forTags
            , id = Some(request.id)
            , data = r
            , error = None
            , `type` = TransportPackage.Type.Response
          )
          if (fromClient) self.pushObject(transportPackage)
          else actorSystem.actorSelection(transportPackage.to.get) ! serialize(transportPackage)
        }
      }

      futureResult.onFailure[Unit] {
        case e: Throwable => {
          val transportPackage = TransportPackage(
            from = request.receiverId
            , to = request.senderId
            , tags = request.forTags
            , id = Some(request.id)
            , data = ""
            , error = Some(e.getMessage)
            , `type` = TransportPackage.Type.Response
          )
          logger.error(e.getMessage, e)
          if (fromClient) self.pushObject(transportPackage)
          else actorSystem.actorSelection(transportPackage.to.get) ! serialize(transportPackage)
        }
      }
    }
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
  ////////////////////////////////////////////////////////////////////////////////
  class ProxyActor(serverEndPoint: ServerEndPoint) extends Actor{
    private val logger = LoggerFactory.getLogger(this.getClass)
    //----------------------------------------------------------------------------
    override def receive = {
      case data: String =>
        try {
          val transportPackage = deserialize(data).extract[TransportPackage]
          transportPackage.`type` match {
            //happens only when a client receive a RouteRequest and reply with a RouteResponse
            case TransportPackage.Type.RouteResponse   |
                 TransportPackage.Type.RouteRequest    |
                 TransportPackage.Type.RouteRequestAny |
                 TransportPackage.Type.RouteMessage =>
              serverEndPoint.webSocketAdapter.push(data)

            case TransportPackage.Type.RouteResponseAny =>{
              if (transportPackage.id.isDefined) {
                val someId = serverEndPoint.clientRequestAnySet.remove(transportPackage.id.get)
                if (someId.isDefined){
                  serverEndPoint.webSocketAdapter.push(data)
                }else{
                  logger.info("Package is dropped because client request the first response only! OK")
                }
              }else{
                logger.warn("ClientResponseAny Package without ID will be swallowed by the server!!!")
              }
            }

            case TransportPackage.Type.Message =>
              serverEndPoint.runMessageReceived(transportPackage)

            case TransportPackage.Type.Request =>
              serverEndPoint.runRequestReceived(transportPackage,fromClient = false)

            case TransportPackage.Type.Response =>
              serverEndPoint.runResponseReceived(transportPackage)
          }
        } catch {
          case exc:Exception =>  logger.warn(exc.getMessage,exc)
        }
    }
    //----------------------------------------------------------------------------
  }
  //////////////////////////////////////////////////////////////////////////////
}
////////////////////////////////////////////////////////////////////////////////
object ServerEndPoint{
  def getServerPath()(implicit actorSystem:ActorSystem)=
    AkkaSystemExt(actorSystem).address

  def getActorPath(actorRef: ActorRef)(implicit actorSystem:ActorSystem):String =
    actorRef.path.toStringWithAddress(getServerPath())

}
////////////////////////////////////////////////////////////////////////////////
class AkkaSystemExtImpl(system:ExtendedActorSystem) extends Extension{
  def address = system.provider.getDefaultAddress
}
object AkkaSystemExt extends ExtensionKey[AkkaSystemExtImpl]
