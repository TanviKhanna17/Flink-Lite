package checkpoint

import ujson._
import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths}

case class Checkpoint(windowBuffer: Map[Long, List[Int]], watermark: Long)

object CheckpointManager {
  private val checkpointFile = "checkpoint.json"

  def saveCheckpoint(windowBuffer: Map[Long, List[Int]], watermark: Long): Unit = {
    val json = Obj(
      "windows" -> Obj.from(
        windowBuffer.map { case (k, v) => k.toString -> Arr(v.map(Num(_)): _*) }
      ),
      "watermark" -> Num(watermark)
    )
    val writer = new PrintWriter(checkpointFile)
    try writer.write(json.render())
    finally writer.close()
  }

  def loadCheckpoint(): Option[Checkpoint] = {
    if (!Files.exists(Paths.get(checkpointFile))) return None

    val source = scala.io.Source.fromFile(checkpointFile)
    try {
      val json = ujson.read(source.mkString)
      val windowBufferOpt = json.obj.get("windows").map {
        case windowsObj: Obj =>
          windowsObj.value.map {
            case (k, Arr(values)) => k.toLong -> values.map(_.num.toInt).toList
            case (k, _)           => k.toLong -> Nil
          }.toMap
        case _ => Map.empty[Long, List[Int]]
      }

      val watermark = json.obj.get("watermark").map(_.num.toLong).getOrElse(0L)

      windowBufferOpt.map(wb => Checkpoint(wb, watermark))
    } catch {
      case _: Throwable => None
    } finally {
      source.close()
    }
  }
}
