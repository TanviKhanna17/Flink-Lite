package watermark

class WatermarkManager(allowedLateness: Long) {
  private var currentMaxTimestamp: Long = Long.MinValue
  private var currentWatermark: Long = Long.MinValue

  def update(eventTime: Long): Long = {
    currentMaxTimestamp = math.max(currentMaxTimestamp, eventTime)
    currentWatermark = currentMaxTimestamp - allowedLateness
    currentWatermark
  }

  def getWatermark: Long = currentWatermark

  // ✅ Add this setter so we can resume from checkpoint
  def setWatermark(w: Long): Unit = {
    currentWatermark = w
    currentMaxTimestamp = w + allowedLateness
  }
}
