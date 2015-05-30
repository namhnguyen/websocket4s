package websocket4s.core

/**
 * Created by namnguyen on 5/23/15.
 */
case class Message(data:String
                   ,senderId:Option[String]
                   ,receiverId:Option[String]
                   ,forTags:Option[Set[String]]
                   )
