package websocket4s.core

/**
 * Created by namnguyen on 5/24/15.
 */
trait WebSocketSubscriber{
  def unsubscribe():Unit
}
