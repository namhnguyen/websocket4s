package websocket4s.core

import scala.concurrent.Future

////////////////////////////////////////////////////////////////////////////////
/**
 * ActorRegister is a centralized place for one Actor to be able to discover
 * other Actor and send message to the other. There are different ways to search
 * for Actors, either by an actor unique identity or by query tags
 *
 * Created by namnguyen on 5/23/15.
 */
trait ActorRegister {
  //----------------------------------------------------------------------------
  /**
   * Register a new entry to an ActorRegister table. If an entry does not exist
   * it will be registered and the entry will be returned as part of the result.
   * Otherwise, None will be returned.
   * @param entry
   * @return
   */
  def register(entry:ActorRegisterEntry):Future[Option[ActorRegisterEntry]]
  //----------------------------------------------------------------------------
  /**
   * Unregister an entry from an ActorRegister table. If an entry does not exist
   * None will be returned, otherwise, a removed ActorRegisterEntry will be returned
   * @param id
   * @return
   */
  def unregister(id:String):Future[Option[ActorRegisterEntry]]
  //----------------------------------------------------------------------------
  /**
   *
   * @param entry
   * @return
   */
  def update(entry:ActorRegisterEntry):Future[Option[ActorRegisterEntry]]
  //----------------------------------------------------------------------------
  /**
   *
   * @param tags
   * @return
   */
  def updateTags(id:String,tags:Set[String]):Future[Option[ActorRegisterEntry]]
  //----------------------------------------------------------------------------
  /**
   *
   * @param id
   * @return
   */
  def getEntry(id:String):Future[Option[ActorRegisterEntry]]
  //----------------------------------------------------------------------------
  /**
   * Simple version, scalable version should use Observable
   * TODO: what if one change publish to million of actors who have the same tag
   *
   * @param tags
   * @return
   */
  def queryEntries(tags:Set[String]):Future[List[ActorRegisterEntry]]
  //----------------------------------------------------------------------------
}
////////////////////////////////////////////////////////////////////////////////