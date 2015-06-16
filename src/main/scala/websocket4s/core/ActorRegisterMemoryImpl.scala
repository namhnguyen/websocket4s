package websocket4s.core

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
////////////////////////////////////////////////////////////////////////////////
/**
 * Implementation of ActorRegisterMemory Implementation
 * Created by namnguyen on 5/28/15.
 */
class ActorRegisterMemoryImpl() extends ActorRegister{
  //----------------------------------------------------------------------------
  private val register = TrieMap[String,ActorRegisterEntry]()
  //----------------------------------------------------------------------------
  /**
   * Register a new entry to an ActorRegister table. If an entry does not exist
   * it will be registered and the entry will be returned as part of the result.
   * Otherwise, None will be returned.
   * @param entry
   * @return
   */
  override def register(entry: ActorRegisterEntry): Future[Option[ActorRegisterEntry]] =
    Future{ register.put(entry.id,entry) }
  //----------------------------------------------------------------------------
  /**
   *
   * @param tags
   * @return
   */
  override def updateTags(id: String, tags: Set[String]): Future[Option[ActorRegisterEntry]] =
    Future {
      this.synchronized({
        register.get(id).map(e => {
          val newEntry = e.copy(tags = tags)
          register.update(id,newEntry)
          newEntry
        })
      })
    }
  //----------------------------------------------------------------------------
  /**
   *
   * @param entry
   * @return
   */
  override def update(entry: ActorRegisterEntry): Future[Option[ActorRegisterEntry]] =
    Future {
      register.replace(entry.id,entry)
    }
  //----------------------------------------------------------------------------
  /**
   *
   * @param id
   * @return
   */
  override def getEntry(id: String): Future[Option[ActorRegisterEntry]] = Future{
    register.get(id)
  }
  //----------------------------------------------------------------------------
  /**
   * Simple version, scalable version should use Observable
   * TODO: what if one change publish to million of actors who have the same tag
   *
   * @param tags
   * @return
   */
  override def queryEntries(tags: Set[String]): Future[List[ActorRegisterEntry]] = Future{
    register.filter{ case (key,entry) => entry.tags.exists(p => tags.contains(p)) }
      .map { case (key,entry) => entry}.toList
  }
  //----------------------------------------------------------------------------
  /**
   * Unregister an entry from an ActorRegister table. If an entry does not exist
   * None will be returned, otherwise, a removed ActorRegisterEntry will be returned
   * @param id
   * @return
   */
  override def unregister(id: String): Future[Option[ActorRegisterEntry]] = Future{
    register.remove(id)
  }
  //----------------------------------------------------------------------------
  def clearAll():Unit = {
    register.clear()
  }
  //----------------------------------------------------------------------------
  def printAll(): Unit ={
    register.map { case (k,v) => println(v)}
  }

  def size = register.size
}
////////////////////////////////////////////////////////////////////////////////