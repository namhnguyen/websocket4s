package websocket4s

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSuite}
import org.slf4j.LoggerFactory
import websocket4s.core.ActorRegisterMemoryImpl

////////////////////////////////////////////////////////////////////////////////
/**
 * Created by namnguyen on 5/29/15.
 */
class PerformanceTest extends FunSuite with BeforeAndAfterAll with BeforeAndAfter{
  private val logger = LoggerFactory.getLogger(this.getClass)
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
      |      port = 2559
      |    }
      |  }
      |}
    """.stripMargin
  val config =  ConfigFactory.parseString(configString)
    .withFallback(ConfigFactory.load())

  val hostname = config.getString("akka.remote.netty.tcp.hostname")
  val port = config.getInt("akka.remote.netty.tcp.port")
  val akkaSystem = "akka-performance-test"
  implicit val actorSystem = ActorSystem.create(akkaSystem,config)
  implicit val actorRegister = new ActorRegisterMemoryImpl()
  //----------------------------------------------------------------------------
  test("Load Test..."){
    //LoadTest can simulate Network latency and allows us to test performance
    //as closed as real environment
    
  }
  //----------------------------------------------------------------------------
  override def afterAll(): Unit ={
    println("Clean up Test Environment...")
    Thread.sleep(500)
    println("------------ Actor Register ------------")
    actorRegister.printAll()
    println("------------ End Of Actor Register -----")
    actorSystem.shutdown()
  }
  //----------------------------------------------------------------------------
}
////////////////////////////////////////////////////////////////////////////////