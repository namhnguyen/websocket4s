package websocket4s.core

/**
 * Created by namnguyen on 5/23/15.
 */
case class Response(ok:Boolean,data:String,endPointId:Option[String],exception: Option[Exception])
