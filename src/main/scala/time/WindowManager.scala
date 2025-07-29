package time

import model.Event
import watermark.WatermarkManager
import checkpoint.CheckpointManager

import scala.collection.mutable
import scala.concurrent.duration._
import java.util.concurrent.Executors
import scala.concurrent.blocking
import metrics.Metrics

class WindowManager(
                     windowSizeMillis: Long = 10000,
                     slideSizeMillis: Long = 5000,
                     allowedLatenessMillis: Long = 5000,
                     loadCheckpoint: Boolean = true
                   ) {

  private val windowBuffer = mutable.Map[Long, List[Int]]()
  private val watermarkManager = new WatermarkManager(allowedLatenessMillis)

  var totalEvents = 0
  var acceptedEvents = 0
  var droppedEvents = 0

  private def format(ts: Long): String = {
    val date = new java.text.SimpleDateFormat("HH:mm:ss")
    date.format(new java.util.Date(ts))
  }

  private def getWindowEnds(timestamp: Long): List[Long] = {
    val lastStart = (timestamp / slideSizeMillis) * slideSizeMillis
    val windowEnds = for (i <- 0 to (windowSizeMillis / slideSizeMillis).toInt) yield {
      lastStart - (i * slideSizeMillis) + windowSizeMillis
    }
    windowEnds.filter(_ > timestamp).toList
  }

  // ✅ Load previous checkpoint if needed
  if (loadCheckpoint) {
    CheckpointManager.loadCheckpoint().foreach { checkpoint =>
      windowBuffer ++= checkpoint.windowBuffer
      watermarkManager.setWatermark(checkpoint.watermark)
      println(s"♻️  Loaded checkpoint: watermark=${format(checkpoint.watermark)}, windows=${checkpoint.windowBuffer.keys.map(format).toList}")
    }
  }

  // ✅ Save periodically every 5s
  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  scheduler.scheduleAtFixedRate(
    () => {
      blocking {
        CheckpointManager.saveCheckpoint(windowBuffer.toMap, watermarkManager.getWatermark)
        Metrics.checkpointTime.set(System.currentTimeMillis().toDouble)
        println(s"💾 Saved checkpoint at ${format(System.currentTimeMillis())}")
      }
    },
    5, 5, java.util.concurrent.TimeUnit.SECONDS
  )

  def process(event: Event): List[String] = {
    val eventTime = event.timestamp
    val watermark = watermarkManager.update(eventTime)
    val now = System.currentTimeMillis()

    println(f"\n📩 Event received: id=${event.id}, time=${format(eventTime)}, arrival=${format(now)}")
    totalEvents += 1
    if (eventTime <= watermark + allowedLatenessMillis) {
      acceptedEvents += 1
      println(s"✅ Accepted (within allowed lateness). Event belongs to windows ending at:")
      val windowEnds = getWindowEnds(eventTime)
      windowEnds.foreach { end =>
        println(s"  - ${format(end)}")
        val updatedList = event.id :: windowBuffer.getOrElse(end, Nil)
        windowBuffer.update(end, updatedList)
      }
    } else {
      droppedEvents += 1
      println(s"❌ Dropped: too late. Event timestamp: ${format(eventTime)}, Watermark: ${format(watermark)}")
    }

    val flushed = windowBuffer
      .filter { case (endTime, _) => watermark >= endTime }
      .map { case (endTime, events) =>
        val start = endTime - windowSizeMillis
        val count = events.size
        val maxId = events.max
        val avgId = events.sum.toDouble / count

        println(s"\n🪟 FLUSHING Window ${format(start)} - ${format(endTime)} | Count: $count events")
        windowBuffer.remove(endTime)
        f"[Window ${format(start)} - ${format(endTime)}]: $count events | Max ID: $maxId | Avg ID: ${avgId}%.2f"
      }.toList

    flushed
  }

  def shutdown(): Unit = {
    scheduler.shutdown()
    CheckpointManager.saveCheckpoint(windowBuffer.toMap, watermarkManager.getWatermark)
    Metrics.checkpointTime.set(System.currentTimeMillis().toDouble)
    val accuracy = (acceptedEvents.toDouble / totalEvents) * 100
    println(f"✅ Accuracy in window assignment: $accuracy%.2f%%")
    println("🧹 Final checkpoint saved on shutdown.")
  }
  def currentWatermark: Long = watermarkManager.getWatermark
}
