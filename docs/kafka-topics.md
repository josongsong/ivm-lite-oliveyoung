# Kafka 토픽 설정 가이드

## 토픽명 패턴

Outbox 이벤트는 다음 패턴으로 Kafka 토픽에 발행됩니다:

```
{topicPrefix}.events.{aggregatetype}
```

### 기본 설정

- **topicPrefix**: `ivm` (기본값)
- **토픽 예시**:
  - `ivm.events.raw_data` - RawDataIngested 이벤트
  - `ivm.events.slice` - SliceCreated 이벤트
  - `ivm.events.changeset` - ChangesetCreated 이벤트

## 설정 방법

### 1. application.yaml

```yaml
kafka:
  bootstrapServers: ${KAFKA_BOOTSTRAP_SERVERS:-localhost:9094}
  consumerGroup: ivm-lite
  topicPrefix: ${KAFKA_TOPIC_PREFIX:-ivm}  # 토픽명: {topicPrefix}.events.{aggregatetype}

worker:
  enabled: true
  # 특정 토픽만 처리 (선택적)
  topics: ["ivm.events.raw_data"]  # Slicing 전용 Worker
  # 또는
  # topics: ["ivm.events.slice"]  # Ship 전용 Worker
```

### 2. 환경 변수

```bash
export KAFKA_TOPIC_PREFIX=oliveyoung
# → 토픽: oliveyoung.events.raw_data

export WORKER_TOPICS=ivm.events.raw_data,ivm.events.slice
# → 특정 토픽만 처리
```

### 3. Debezium Connector 설정

Debezium Connector에서도 동일한 토픽 prefix를 사용하도록 설정:

```json
{
  "transforms.outbox.route.topic.replacement": "${KAFKA_TOPIC_PREFIX:-ivm}.events.${routedByValue}"
}
```

## AggregateType별 토픽

| AggregateType | 토픽명 (기본) | Topic Enum | 설명 |
|--------------|---------------|------------|------|
| `RAW_DATA` | `ivm.events.raw_data` | `Topic.RAW_DATA` | RawDataIngested 이벤트 |
| `SLICE` | `ivm.events.slice` | `Topic.SLICE` | SliceCreated, ShipRequested 이벤트 |
| `CHANGESET` | `ivm.events.changeset` | `Topic.CHANGESET` | ChangeSetCreated 이벤트 |

## SDK에서 Consume (토픽 기반)

### 특정 토픽만 Consume

```kotlin
import com.oliveyoung.ivmlite.sdk.Ivm
import com.oliveyoung.ivmlite.shared.domain.types.Topic

// 특정 토픽만 poll
val entries = Ivm.consume(Topic.RAW_DATA).poll()

// 여러 토픽 poll
val entries = Ivm.consume(Topic.RAW_DATA, Topic.SLICE).poll()

// 토픽명으로 poll
val entries = Ivm.consumeByTopicName("ivm.events.raw_data").poll()
```

### Flow로 연속 Consume

```kotlin
import kotlinx.coroutines.flow.collect

// Flow로 연속 처리
Ivm.consume(Topic.RAW_DATA)
    .batchSize(50)
    .pollInterval(200)
    .flow()
    .collect { entry ->
        println("Received: ${entry.eventType}")
        
        // 처리 완료 후 ack
        Ivm.consume(Topic.RAW_DATA).ack(listOf(entry))
    }
```

### Worker 분리 (토픽별)

```kotlin
// Slicing 전용 Worker
val slicingWorkerConfig = WorkerConfig(
    topics = listOf("ivm.events.raw_data")
)

// Ship 전용 Worker  
val shipWorkerConfig = WorkerConfig(
    topics = listOf("ivm.events.slice")
)
```

## Kafka와 PostgreSQL Polling 호환

Kafka Consumer와 PostgreSQL Polling Worker 모두에서 동일한 토픽 패턴을 사용합니다:

| 방식 | 토픽 설정 | 처리 방식 |
|------|----------|----------|
| **PostgreSQL Polling** | `worker.topics` | OutboxPollingWorker |
| **Kafka Consumer** | `consumer.subscribe(topics)` | Kafka Consumer API |
| **SDK Consume** | `Ivm.consume(Topic.*)` | 통합 API |

## 토픽 생성

Kafka 토픽은 Debezium Connector가 자동으로 생성합니다. 수동으로 생성하려면:

```bash
# RawData 토픽
kafka-topics.sh --create \
  --bootstrap-server localhost:9094 \
  --topic ivm.events.raw_data \
  --partitions 3 \
  --replication-factor 1

# Slice 토픽
kafka-topics.sh --create \
  --bootstrap-server localhost:9094 \
  --topic ivm.events.slice \
  --partitions 3 \
  --replication-factor 1

# Changeset 토픽
kafka-topics.sh --create \
  --bootstrap-server localhost:9094 \
  --topic ivm.events.changeset \
  --partitions 3 \
  --replication-factor 1
```

## Kafka Consumer 설정

```kotlin
val consumer = KafkaConsumer<String, String>(properties)

// 특정 토픽만 구독
consumer.subscribe(listOf("ivm.events.raw_data"))

// 모든 토픽 구독
consumer.subscribe(Topic.allTopicNames("ivm"))
```

## 참고

- RFC-IMPL-008: Transactional Outbox Pattern
- Topic enum: `com.oliveyoung.ivmlite.shared.domain.types.Topic`
- Debezium Outbox Event Router: https://debezium.io/documentation/reference/transformations/outbox-event-router.html
