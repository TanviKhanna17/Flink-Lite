import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import model.Event
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import time.WindowManager
import ujson._
import io.prometheus.client.{Counter, Gauge}
import io.prometheus.client.hotspot.DefaultExports
import io.prometheus.client.exporter.HTTPServer
import metrics.Metrics

import java.util.Properties
import scala.concurrent.duration._
import scala.util.Try

object Main extends App {
  implicit val system: ActorSystem = ActorSystem("FlinkLiteKafkaSystem")
  implicit val mat: Materializer = Materializer(system)
  import system.dispatcher

  println("🚀 Starting Kafka-powered FlinkLite Stream Engine")
  DefaultExports.initialize()
  new HTTPServer(1234)

  val windowManager = new WindowManager()

  // Kafka consumer settings
  val consumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers("localhost:9092")
    .withGroupId("flink-lite-group")
    .withProperty("auto.offset.reset", "earliest")

  // Kafka producer (for processed-events)
  val producerProps = new Properties()
  producerProps.put("bootstrap.servers", "localhost:9092")
  producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  val kafkaProducer = new KafkaProducer[String, String](producerProps)

  // Stream logic
  Consumer
    .plainSource(consumerSettings, Subscriptions.topics("flink-events"))
    .map(_.value())
    .map { jsonString =>
      Try(read(jsonString)).toOption.flatMap { json =>
        for {
          id <- json("id").numOpt.map(_.toInt)
          timestamp <- json("timestamp").numOpt.map(_.toLong)
          message <- json("message").strOpt
        } yield Event(id, timestamp, message)
      }
    }
    .collect {
      case Some(event) if Set("important", "critical").contains(event.message) =>
        Metrics.eventsProcessed.inc()
        val result = windowManager.process(event)
        Metrics.watermarkGauge.set(windowManager.currentWatermark.toDouble)

        result.foreach { r =>
          println(s"${if (event.message == "critical") "🔥 CRITICAL" else "✅ Important"}: $r")
          Metrics.flushedWindows.inc()

          kafkaProducer.send(new ProducerRecord[String, String]("processed-events", r))
        }
    }
    .runWith(Sink.ignore)

  // Graceful shutdown after 1 minute (optional)
  system.scheduler.scheduleOnce(1.minute) {
    windowManager.shutdown()
    system.terminate()
    println("✅ Kafka stream engine shutdown complete.")
  }
}
