package websocket4s

import akka.actor.{Props, Actor, ActorSystem}
import com.typesafe.config.{ConfigValueFactory, ConfigFactory}
import websocket4s.server.ServerEndPoint

/**
 * Created by namnguyen on 5/22/15.
 */
object Main extends App{
  println("Main Object of Websocket4s...")
  val configString =
    """
      |akka {
      |  actor {
      |    provider = "akka.remote.RemoteActorRefProvider"
      |  }
      |  remote {
      |    transport = "akka.remote.netty.NettyRemoteTransport"
      |    netty.tcp {
      |      hostname = "127.0.0.1"
      |      #      hostname = ${public-hostname}
      |      port = 2552
      |    }
      |  }
      |}
    """.stripMargin

  val config =  ConfigFactory.parseString(configString)
    .withFallback(ConfigFactory.load())

  val hostname = config.getString("akka.remote.netty.tcp.hostname")
  val port = config.getInt("akka.remote.netty.tcp.port")
  val akkaSystem = "akka-test"

  implicit val system = {
    var nextPort = port
    var system:Option[ActorSystem] = None
    var notOk = true
    while(notOk) {
      try {
        system = Some(ActorSystem.create(akkaSystem
          , config.withValue("akka.remote.netty.tcp.port",ConfigValueFactory.fromAnyRef(nextPort))))
        notOk = false
      } catch {
        case exc: Exception => nextPort = nextPort + 1
      }
    }
    system.get
  }

  val actor1 = system.actorOf(Props[ActorA])
  println(ServerEndPoint.getServerPath())
  println(ServerEndPoint.getActorPath(actor1))
}

class ActorA extends Actor {
  override def receive = {
    case s:String => println(s)
  }
}