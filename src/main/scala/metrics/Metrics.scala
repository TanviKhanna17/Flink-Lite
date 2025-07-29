package metrics

import io.prometheus.client.{Counter, Gauge}

object Metrics {
  val eventsProcessed: Counter = Counter.build()
    .name("flinklite_events_total")
    .help("Total number of important events processed")
    .register()

  val flushedWindows: Counter = Counter.build()
    .name("flinklite_windows_flushed_total")
    .help("Number of windows flushed")
    .register()

  val watermarkGauge: Gauge = Gauge.build()
    .name("flinklite_watermark_timestamp")
    .help("Current watermark timestamp")
    .register()

  val checkpointTime: Gauge = Gauge.build()
    .name("flinklite_last_checkpoint_time")
    .help("Checkpoint save timestamp")
    .register()
}
