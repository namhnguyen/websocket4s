package websocket4s.core

import websocket4s.core.JsonUtils._
////////////////////////////////////////////////////////////////////////////////
/**
 * Created by namnguyen on 5/25/15.
 */
case class TransportPackage
  (
    from:Option[String]
  , to:Option[String]
  , tags:Option[Set[String]]
  , id:Option[String] //requestId only applicable to Request/Response
  , data:String //data
  , error:Option[String] = None
  , `type`:String = TransportPackage.Type.Message //package Type
  )
////////////////////////////////////////////////////////////////////////////////
object TransportPackage{
  object Type{
    val Message = "M"
    val Request = "R"
    val Response = "P"
    val RouteRequest = "RR"
    val RouteRequestAny = "RRA"
    val RouteResponse = "RP"
    val RouteResponseAny = "RPA"
    val RouteMessage = "RM"
  }
  //----------------------------------------------------------------------------
  def encodeForSocket(transportPackage: TransportPackage):String = {
    serialize(transportPackage)
  }
  //----------------------------------------------------------------------------
  def decodeForSocket(dataFrame:String):TransportPackage = {
    deserialize(dataFrame).extract[TransportPackage]
  }
  //----------------------------------------------------------------------------
  def encodeForActor(transportPackage: TransportPackage):String = {
    serialize(transportPackage)
  }
  //----------------------------------------------------------------------------
  def decodeForActor(data:String):TransportPackage = {
    deserialize(data).extract[TransportPackage]
  }
  //----------------------------------------------------------------------------
}
////////////////////////////////////////////////////////////////////////////////