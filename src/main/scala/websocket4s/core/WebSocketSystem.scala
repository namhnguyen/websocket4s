package websocket4s.core

import java.nio.ByteBuffer
import java.util.{Base64, UUID}
import java.util.concurrent.Executors

import scala.collection.mutable.StringBuilder
import scala.util.Random

/**
 * Created by namnguyen on 5/25/15.
 */
object WebSocketSystem {
  val cores =  Runtime.getRuntime.availableProcessors
  val scheduler = Executors.newScheduledThreadPool(cores)
  object GUID {
    //----------------------------------------------------------------------------
    def fromUUID(id:UUID):String = {
      val bb = ByteBuffer.wrap(new Array[Byte](16))
      bb.putLong(id.getMostSignificantBits)
      bb.putLong(id.getLeastSignificantBits)
      val str = Base64.getEncoder.encodeToString(bb.array())
      val length = str.length
      val builder = new StringBuilder(length)
      for(i <- 0 until length-2){
        val c = str.charAt(i)
        c match {
          case '/' => builder.append('_')
          case '+' => builder.append('-')
          case _ => builder.append(c)
        }
      }
      builder.toString()
      //.replaceFirst("==","").replace('/','_').replace('+','-')
    }
    //----------------------------------------------------------------------------
    def toUUID(id:String):UUID = {
      val builder = new StringBuilder(id.length+2)
      for (c <- id){
        c match {
          case '-' => builder.append('+')
          case '_' => builder.append('/')
          case _ => builder.append(c)
        }
      }
      builder.append("==")
      val input = builder.toString()
      val bytes = Base64.getDecoder
        .decode(input) //id.replace('-','+').replace('_','/')+"==")
      val bb = ByteBuffer.wrap(bytes)
      new UUID(bb.getLong,bb.getLong)
    }
    //----------------------------------------------------------------------------
    def randomGUID:String = fromUUID(UUID.randomUUID())
    //----------------------------------------------------------------------------
    def randomLongGUID(length:Int):String =
      String.valueOf(random.alphanumeric.take(length).toArray)
    //----------------------------------------------------------------------------

    private val random = Random
  }
}
