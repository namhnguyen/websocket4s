package websocket4s.core

import scala.collection.mutable

/**
 * Created by namnguyen on 5/24/15.
 */
class WebSocketListeners extends Iterable[WebSocketListener]{

  private val hashSet = new mutable.HashSet[WebSocketListener]

  def subscribe(listener: WebSocketListener):WebSocketSubscriber = {
    hashSet += listener
    new Subscriber(listener,hashSet)
  }

  def unsubscribe(listener:WebSocketListener):Unit = {
    hashSet -= listener
  }

  def unsubscribeAll():Unit = {
    hashSet.clear()
  }

  override def iterator: Iterator[WebSocketListener] = hashSet.iterator

  private class Subscriber(listener: WebSocketListener,hashSet: mutable.HashSet[WebSocketListener]) extends WebSocketSubscriber{
    override def unsubscribe(): Unit = hashSet -= listener
  }

}
