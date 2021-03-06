package websocket4s.spray

import akka.actor._
import akka.event.LoggingReceive
import akka.io._
import akka.pattern.ask
import akka.util._
import scala.concurrent.Future
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket._
import spray.can.websocket.frame._
import spray.http._
import spray.io.{ ClientSSLEngineProvider, ServerSSLEngineProvider }
import spray.routing._


/**
 * This file is from simple-spray-websockets
 *
 * Spawns and registers WebSocket-capable worker actors with each new
 * HTTP connection. A convenience is provided in the companion to
 * create a new web socket server.
 *
 * Caveats:
 *
 * 1. Not possible to use IO(Http) and IO(Uhttp) in an ActorSystem:
 *    https://github.com/wandoulabs/spray-websocket/issues/44
 *
 * 2. An upgrade request on any path is valid (not restricted):
 *    https://github.com/wandoulabs/spray-websocket/issues/67
 *
 * 3. Backpressure is implemented as a hack, because wandoulabs
 *    didn't consider this to be an important feature.
 *    https://github.com/wandoulabs/spray-websocket/issues/68
 *
 * @param workerFactory taking an HTTP connection actor and returning
 *                      the Props for a `WebSocketServerWorker`
 *                      implementation. `WebSocketComboWorker` is
 *                      provided to simplify the implementation of
 *                      REST/WebSocket servers. Note that a new actor
 *                      is created to serve every request (so there is
 *                      no need to apply the netaporter pattern in
 *                      Spray Route rules).
 */
class WebSocketServer(workerFactory: ActorRef => Props) extends Actor with ActorLogging {
  def receive = LoggingReceive {
    case Http.Connected(remoteAddress, localAddress) =>
      val connection = sender()

      val worker = context.actorOf(
        workerFactory(connection),
        // enforces one connection per host/port combo (this might be
        // too restrictive for some magical router setups)
        s"${remoteAddress.getHostName}:${remoteAddress.getPort()}"
      )
      connection ! Http.Register(worker)
  }
}
object WebSocketServer {
  /**
   * Starts an HTTP server as a child of the implicit context,
   * spawning a worker actor (implementing `WebSocketServerWorker`)
   * for every incoming connection.
   *
   * @param interface to bind to.
   * @param port to bind to (0 will attempt to find a free port).
   * @param workerProps as defined in the `WebSocketServer` docs.
   * @param name of the server actor.
   * @param context the supervisor.
   * @param timeout for receiving the binding (the binding may
   *        still succeed even if this future times out).
   * @return the server actor and a future containing the binding.
   */
  def start(
             interface: String,
             port: Int,
             workerProps: ActorRef => Props,
             name: String = "uhttp"
             )(implicit
               context: ActorRefFactory,
               system: ActorSystem,
               ssl: ServerSSLEngineProvider,
               timeout: Timeout): (ActorRef, Future[Tcp.Event]) = {
    val serverProps = Props(classOf[WebSocketServer], workerProps)
    val server = context.actorOf(serverProps, name)

    val binding = IO(UHttp) ? Http.Bind(server, interface, port)
    (server, binding.mapTo[Tcp.Event])
  }
}

/**
 * Abstract actor that makes an HTTP connection request to the
 * provided location and upgrades to WebSocket when connected.
 *
 * NOTE: this uses a Stash so needs a dequeue mailbox.
 */
abstract class WebSocketClient(
                                host: String,
                                port: Int,
                                path: String = "/",
                                ssl: Boolean = false
                                ) extends WebSocketClientWorker with Stash {
  override def preStart(): Unit = {
    import context.system

    IO(UHttp) ! Http.Connect(host, port, sslEncryption = ssl)
  }

  // WORKAROUND:
  // https://github.com/wandoulabs/spray-websocket/issues/70
  // (no need to copy the rest of the file because they otherwise
  // handle stashing)
  override def receive = handshaking orElse closeLogic orElse stashing
  def stashing: Receive = {
    case _ => stash()
  }

  val WebSocketUpgradeHeaders = List(
    HttpHeaders.Connection("Upgrade"),
    HttpHeaders.RawHeader("Upgrade", "websocket"),
    HttpHeaders.RawHeader("Sec-WebSocket-Version", "13"),
    // base64 encoding of the WebSocket GUID
    // see http://tools.ietf.org/html/rfc6455
    // 258EAFA5-E914-47DA-95CA-C5AB0DC85B11
    HttpHeaders.RawHeader("Sec-WebSocket-Key", "x3JJHMbDL1EzLkh9GBhXDw=="),
    HttpHeaders.RawHeader("Sec-WebSocket-Extensions", "permessage-deflate")
  )

  def userHeaders: List[HttpHeader] = Nil

  def upgradeRequest = HttpRequest(
    HttpMethods.GET, Uri.from(path=path),
    HttpHeaders.Host(host, port) :: WebSocketUpgradeHeaders ::: userHeaders
  )

  // workaround upstream naming convention
  final def businessLogic: Receive = websockets
  def websockets: Receive
}

/** Ack received in response to WebSocketComboWorker.sendWithAck */
object Ack extends Tcp.Event with spray.io.Droppable

/**
 * Provides a UHTTP-enabled worker for an HTTP connection with the
 * ability to deal with both REST and WebSocket messages.
 *
 * Mostly copied from `WebSocketServerWorker` with workarounds for
 * acking and stashing.
 */
abstract class WebSocketComboWorker(
                                     val serverConnection: ActorRef
                                     ) extends Actor with ActorLogging with Stash {
  import spray.can.websocket

  // headers may be useful for authentication and such
  var headers: List[HttpHeader] = _
  //Store URI in case each connection can connect to different URI
  var path: String = _
  private var maskingKey: Array[Byte] = _

  // from upstream
  def closeLogic: Receive = {
    case ev: Http.ConnectionClosed =>
      context.stop(self)
  }

  // from upstream plus unstashAll() and setting maskingKey
  def handshaking: Receive = {
    case websocket.HandshakeRequest(state) =>
      state match {
        case wsFailure: websocket.HandshakeFailure =>
          sender() ! wsFailure.response
        case wsContext: websocket.HandshakeContext =>
          headers = wsContext.request.headers
          path = wsContext.request.uri.authority.host.address
          // https://github.com/wandoulabs/spray-websocket/issues/69
          maskingKey = Array.empty[Byte]
          //perform authentication/authorization here if needed
          sender() ! UHttp.UpgradeServer(websocket.pipelineStage(self, wsContext), wsContext.response)
      }

    case UHttp.Upgraded =>
      log.info(s"Upgraded with $headers")

      context.become(websockets orElse closeLogic)
      self ! websocket.UpgradedToWebSocket
      unstashAll()
  }

  // our rest logic PLUS https://github.com/wandoulabs/spray-websocket/issues/70
  def receive = handshaking orElse rest orElse closeLogic orElse stashing
  def stashing: Receive = {
    case _ => stash()
  }

  /** User-defined websocket handler. */
  def websockets: Receive

  /**
   * User-defined REST handler.
   *
   * Defined as a `Receive` for performance (to allow sharing of
   * `Route` between sessions).
   */
  def rest: Receive

  /**
   * Wandoulabs' WebSocket implementation doesn't support ack/nack-ing
   * on the CommandFrame level. But it is possible to drop down to TCP
   * messages by duplicating their FrameRendering logic in the
   * ServerWorker actor. Here we construct a payload that is ignored
   * by the ConnectionManager which requests an Ack.
   *
   * A similar pattern could be applied for non-Text Frames and NACK
   * based writing, in order to skip the FrameRendering logic.
   *
   * For more information, see
   * https://groups.google.com/d/msg/akka-user/ckUJ9wlltuc/h37ZRCkAA6cJ
   */
  def sendWithAck(frame: TextFrame): Unit = {
    serverConnection ! Tcp.Write(FrameRender.render(frame, maskingKey), Ack)
  }

  // from upstream
  def send(frame: Frame) {
    serverConnection ! FrameCommand(frame)
  }
}

/**
 * Convenient, but not performance optimal, implementation of
 * `WebSocketComboWorker` that recreates a Spray `Route` for every
 * connection.
 */
abstract class SimpleWebSocketComboWorker(conn: ActorRef)
  extends WebSocketComboWorker(conn) with HttpService {

  implicit def actorRefFactory: ActorRefFactory = context
  implicit def settings: RoutingSettings = RoutingSettings.default(actorRefFactory)
  implicit def handler: ExceptionHandler = ExceptionHandler.default
  final def rest: Receive = runRoute(route)

  def route: Route
}
