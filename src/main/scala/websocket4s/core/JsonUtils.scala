package websocket4s.core

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.write
////////////////////////////////////////////////////////////////////////////////
/**
 * Put all Json Serializer / Deserializer into 1 place
 * Created by namnguyen on 3/4/15.
 */
object JsonUtils {
  implicit val format = org.json4s.DefaultFormats

  def serialize(any:AnyRef):String = write(any)
  def deserialize(input:String) = parse(input)
  def decompose(any:AnyRef):JValue = Extraction.decompose(any)
}
////////////////////////////////////////////////////////////////////////////////