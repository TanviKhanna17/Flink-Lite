package model
import upickle.default._

case class Event(id: Int, timestamp: Long, message: String)
object Event {
  implicit val rw: ReadWriter[Event] = macroRW
}
