package websocket4s.server

import akka.actor._
////////////////////////////////////////////////////////////////////////////////
/**
 * Created by namnguyen on 6/1/15.
 */
object RoutingActorSystem{
  def getServerPath()(implicit actorSystem:ActorSystem)=
    AkkaSystemExt(actorSystem).address

  def getActorPath(actorRef: ActorRef)(implicit actorSystem:ActorSystem):String =
    actorRef.path.toStringWithAddress(getServerPath())

}
////////////////////////////////////////////////////////////////////////////////
class AkkaSystemExtImpl(system:ExtendedActorSystem) extends Extension{
  def address = system.provider.getDefaultAddress
}
////////////////////////////////////////////////////////////////////////////////
object AkkaSystemExt extends ExtensionKey[AkkaSystemExtImpl]
////////////////////////////////////////////////////////////////////////////////