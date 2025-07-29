# ⚡ FlinkLite — A Real-Time Distributed Stream Processing Engine

**FlinkLite** is a lightweight real-time stream processing engine built in **Scala** using **Akka Streams** and **Apache Kafka**, inspired by Apache Flink. It supports **event-time processing**, **out-of-order event handling**, **windowing**, **checkpointing**, and **real-time observability** using **Prometheus** and **Grafana**.

---

## 🚀 Features

* ✅ Event-time processing with **sliding windows**
* 🔄 Handles **out-of-order events** using watermarks and allowed lateness
* 📂 Disk-based **checkpointing** every 5 seconds
* 📈 Real-time **monitoring** with Prometheus + Grafana
* 🔥 Supports multiple event types: `important`, `critical`, `normal`
* 📤 Optionally forwards processed results to another Kafka topic
* 🧪 Simulates production-like disorder and throughput for testing

---

## 📦 Tech Stack

| Component     | Tool / Framework      |
| ------------- | --------------------- |
| Language      | Scala                 |
| Streaming     | Akka Streams          |
| Messaging     | Apache Kafka          |
| Metrics       | Prometheus            |
| Visualization | Grafana               |
| Checkpointing | JSON-based disk files |
| Build Tool    | sbt                   |

---

## 🏗 Architecture

```
Kafka Producer → [Kafka Topic: flink-events]
                      ↓
           [Akka Streams: FlinkLite Core]
           - Watermark Manager
           - Sliding Window Manager
           - Disk Checkpointing
                      ↓
     [Prometheus Exporter]   [Kafka Output Topic (optional)]
                      ↓
                  [Grafana]
```

---

## ⚙️ How to Run

### 1. Start Kafka + Create Topics

```bash
# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties

# Start Kafka
bin/kafka-server-start.sh config/server.properties

# Create topics
kafka-topics.sh --create --topic flink-events --bootstrap-server localhost:9092
kafka-topics.sh --create --topic processed-events --bootstrap-server localhost:9092
```

---

### 2. Run the Stream Engine

```bash
sbt run
```

This:

* Consumes from `flink-events`
* Processes only `"important"` and `"critical"` messages
* Maintains window buffers and checkpoints to disk
* Exposes Prometheus metrics at `http://localhost:1234/metrics`

---

### 3. Simulate Event Stream with Disorder

```bash
for i in {1..100}; do
  id=$i
  now=$(date +%s%3N)
  offset=$((10000 * (RANDOM % 6)))
  timestamp=$((now - offset))

  case $((RANDOM % 3)) in
    0) msg="important" ;;
    1) msg="normal" ;;
    2) msg="critical" ;;
  esac

  echo "{\"id\": $id, \"timestamp\": $timestamp, \"message\": \"$msg\"}" | \
    kafka-console-producer --broker-list localhost:9092 --topic flink-events > /dev/null
done
```

---

### 4. Set Up Prometheus

Edit `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'flinklite'
    static_configs:
      - targets: ['localhost:1234']
```

Then run:

```bash
./prometheus --config.file=prometheus.yml
```

---

### 5. Visualize with Grafana

* Open `http://localhost:3000`
* Add Prometheus as data source
* Example queries:

  * `rate(flinklite_events_total[1m])` → event throughput
  * `flinklite_windows_flushed_total` → number of flushed windows
  * `flinklite_watermark_timestamp` → stream time progress
  * `flinklite_last_checkpoint_time` → last checkpoint time in ms

---

## 📊 Metrics Tracked

| Metric                            | Description                        |
| --------------------------------- | ---------------------------------- |
| `flinklite_events_total`          | Total number of processed events   |
| `flinklite_windows_flushed_total` | Number of windows flushed          |
| `flinklite_watermark_timestamp`   | Current watermark timestamp        |
| `flinklite_last_checkpoint_time`  | Time (ms) of last checkpoint write |

---

## 🧠 Internals

### ✅ Watermarking Logic

* Keeps track of max observed timestamp
* Watermark = max timestamp − allowed lateness (5s)
* Events older than watermark + 5s are **discarded**

### ✅ Windowing Logic

* Sliding window: size = 10s, slide = 5s
* Events are placed in all overlapping windows
* Flushed when watermark surpasses window end

### ✅ Checkpointing

* Saved every 5 seconds to `checkpoint.json`
* Restored on restart to prevent data loss

---

## 📂 Project Structure

```
FlinkLite/
├── src/main/scala/
│   ├── Main.scala
│   ├── model/Event.scala
│   ├── time/
│   │   ├── WindowManager.scala
│   │   └── SessionWindowManager.scala
│   ├── watermark/WatermarkManager.scala
│   ├── checkpoint/CheckpointManager.scala
│   └── metrics/Metrics.scala
├── prometheus.yml
├── build.sbt
└── .gitignore
```

---

## 🚰 Future Enhancements

* Add support for **session windows**
* Introduce **joins** across event streams
* Persist outputs to **Cassandra** or **PostgreSQL**
* Add support for **custom aggregation operators**
* Include **Docker Compose** for easier setup

---

## 📬 Feedback

Feel free to raise issues or contribute with PRs. Star ⭐ the repo if you liked the project!
