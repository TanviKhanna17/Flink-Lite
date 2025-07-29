package time

import model.Event
import watermark.WatermarkManager

import scala.collection.mutable

class SessionWindowManager(
                            sessionGapMillis: Long = 5000,
                            allowedLatenessMillis: Long = 5000
                          ) {
  // A session is a group of close-together events
  case class Session(start: Long, var end: Long, var events: List[Int])

  private val sessions = mutable.ListBuffer[Session]()
  private val watermarkManager = new WatermarkManager(allowedLatenessMillis)

  private def format(ts: Long): String = {
    val date = new java.text.SimpleDateFormat("HH:mm:ss")
    date.format(new java.util.Date(ts))
  }

  def process(event: Event): List[String] = {
    val eventTime = event.timestamp
    val watermark = watermarkManager.update(eventTime)
    val now = System.currentTimeMillis()

    println(f"\n📩 [Session] Event received: id=${event.id}, time=${format(eventTime)}, arrival=${format(now)}")

    val flushed = mutable.ListBuffer[String]()

    if (eventTime <= watermark + allowedLatenessMillis) {
      // Try to find an existing session to add this event to
      val maybeSession = sessions.find(s => eventTime >= s.start && eventTime <= s.end + sessionGapMillis)

      maybeSession match {
        case Some(session) =>
          session.events = event.id :: session.events
          session.end = math.max(session.end, eventTime)

        case None =>
          // Create new session
          sessions += Session(eventTime, eventTime, List(event.id))
      }
    } else {
      println(s"❌ Dropped (too late): Event at ${format(eventTime)}, watermark = ${format(watermark)}")
    }

    // Flush expired sessions (those that ended before watermark)
    val toFlush = sessions.filter(_.end < watermark)
    toFlush.foreach { s =>
      val count = s.events.size
      val maxId = s.events.max
      val avgId = s.events.sum.toDouble / count
      flushed += f"[Session ${format(s.start)} - ${format(s.end)}]: $count events | Max ID: $maxId | Avg ID: ${avgId}%.2f"
      println(s"\n🧹 Flushing expired session ${format(s.start)} - ${format(s.end)}")
      sessions -= s
    }

    flushed.toList
  }
}
