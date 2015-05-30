package websocket4s.core

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
}
