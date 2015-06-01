package websocket4s.core

/**
 * Created by namnguyen on 5/24/15.
 */
case class ActorRegisterEntry
(
  id:String
  ,tags:Set[String]
  ,createTime:Long = System.currentTimeMillis()
)
