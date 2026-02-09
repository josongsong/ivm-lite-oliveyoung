# Slice → Sink → Outbox 흐름

## 전체 파이프라인

```
1. RawData Ingest
   ↓
2. Slice 생성 (SlicingWorkflow)
   ↓
3. Ship 트리거 결정 (DeployExecutor)
   ↓
4. ShipRequested Outbox 생성 (ship.async()일 때)
   ↓
5. OutboxPollingWorker가 ShipRequested 처리
   ↓
6. ShipWorkflow 실행
   ↓
7. Sink로 데이터 전달
```

---

## 상세 흐름

### 1. RawData Ingest → Slice 생성

```kotlin
// IngestWorkflow.kt
IngestWorkflow.execute()
  → rawRepo.putIdempotent(record)  // RawData 저장
  → outboxRepo.insert(OutboxEntry.create(
      aggregateType = RAW_DATA,
      eventType = "RawDataIngested"
    ))
```

**Outbox 이벤트**: `RawDataIngested` (aggregateType: `RAW_DATA`)

---

### 2. OutboxPollingWorker → SlicingWorkflow

```kotlin
// OutboxPollingWorker.kt:275-286
processRawDataEvent(entry)
  → when (entry.eventType == "RawDataIngested")
  → slicingWorkflow.executeAuto(tenantId, entityKey, version)
```

**SlicingWorkflow 실행**:
```kotlin
// SlicingWorkflow.kt:134-171
executeAuto()
  → rawRepo.get(tenantId, entityKey, version)  // RawData 조회
  → slicingEngine.slice(raw, ruleSetRef)  // Slice 생성
  → sliceRepo.putAllIdempotent(slices)  // Slice 저장
  → invertedIndexRepo.putAllIdempotent(indexes)  // InvertedIndex 저장
```

**결과**: Slice가 DynamoDB에 저장됨

**중요**: SlicingWorkflow는 Slice만 생성하고, **ShipRequested outbox를 생성하지 않음**

---

### 3. Ship 트리거 결정 (DeployExecutor)

**두 가지 경로**:

#### 경로 A: Sync 모드 (Outbox 사용 안 함)

```kotlin
// DeployExecutor.kt:149-175
ShipMode.Sync -> {
    shipWorkflow.executeToMultipleSinks(
        tenantId, entityKey, version, sinkTypes
    )  // ✅ 직접 실행, Outbox 거치지 않음
}
```

**흐름**:
```
DeployExecutor
  → shipWorkflow.executeToMultipleSinks()  // 직접 호출
  → ShipWorkflow.execute()
  → sliceRepo.getByVersion()  // Slice 조회
  → sink.ship()  // Sink로 전달
```

#### 경로 B: Async 모드 (Outbox 사용)

```kotlin
// DeployExecutor.kt:177-203
ShipMode.Async -> {
    shipSpec.sinks.forEach { sink ->
        val shipTaskEntry = OutboxEntry.create(
            aggregateType = AggregateType.SLICE,  // ✅ SLICE aggregateType
            aggregateId = "${tenantId}:${entityKey}",
            eventType = "ShipRequested",  // ✅ ShipRequested 이벤트
            payload = {
                "tenantId": "...",
                "entityKey": "...",
                "version": "...",
                "sink": "opensearch",
                "shipMode": "async"
            }
        )
        outboxRepository.insert(shipTaskEntry)  // ✅ Outbox에 저장
    }
}
```

**Outbox 이벤트**: `ShipRequested` (aggregateType: `SLICE`)

---

### 4. OutboxPollingWorker → ShipEventHandler

```kotlin
// OutboxPollingWorker.kt:257-272
processEntry(entry)
  → when (entry.aggregateType) {
      AggregateType.SLICE -> eventHandler.handleSliceEvent(entry)
      // ...
    }
```

**ShipEventHandler 처리**:
```kotlin
// ShipEventHandler.kt:31-39
handleSliceEvent(entry)
  → when (entry.eventType) {
      "ShipRequested" -> processShipRequested(entry)
      // ...
    }
```

---

### 5. ShipEventHandler → ShipWorkflow

```kotlin
// ShipEventHandler.kt:45-79
processShipRequested(entry)
  → val payload = json.decodeFromString<ShipRequestedPayload>(entry.payload)
  → shipWorkflow.execute(
      tenantId = TenantId(payload.tenantId),
      entityKey = EntityKey(payload.entityKey),
      version = payload.version.toLong(),
      sinkType = mapSinkName(payload.sink)  // "opensearch"
    )
```

---

### 6. ShipWorkflow → Sink

```kotlin
// ShipWorkflow.kt:39-86
execute(tenantId, entityKey, version, sinkType)
  → val sink = sinks[sinkType]  // OpenSearchSinkAdapter 찾기
  → sliceRepo.getByVersion(tenantId, entityKey, version)  // Slice 조회
  → mergeSlices(slices)  // Slice 병합
  → sink.ship(tenantId, entityKey, version, mergedPayload)  // Sink로 전달
```

**OpenSearchSinkAdapter**:
```kotlin
// OpenSearchSinkAdapter.kt:61-102
ship(tenantId, entityKey, version, payload)
  → val documentId = buildDocumentId(tenantId, entityKey)
  → val indexName = buildIndexName(tenantId)
  → client.put("${config.endpoint}/$indexName/_doc/$documentId") {
      setBody(payload)  // ✅ OpenSearch로 전달
    }
```

---

## 핵심 포인트

### 1. Slice 생성과 Ship 트리거는 분리됨

- **SlicingWorkflow**: Slice만 생성, ShipRequested outbox 생성 안 함
- **DeployExecutor**: Ship 트리거 결정 및 Outbox 생성

### 2. Outbox는 비동기 경계에서만 사용

| 모드 | Outbox 사용 | 실행 경로 |
|------|------------|----------|
| `ship.sync()` | ❌ 사용 안 함 | DeployExecutor → ShipWorkflow → Sink (직접) |
| `ship.async()` | ✅ 사용 | DeployExecutor → Outbox → OutboxPollingWorker → ShipEventHandler → ShipWorkflow → Sink |

### 3. Outbox 이벤트 타입

| 단계 | AggregateType | EventType | 생성 위치 |
|------|--------------|-----------|----------|
| RawData 저장 | `RAW_DATA` | `RawDataIngested` | IngestWorkflow |
| Slice 생성 | (없음) | (없음) | SlicingWorkflow (Outbox 생성 안 함) |
| Ship 트리거 | `SLICE` | `ShipRequested` | DeployExecutor (ship.async()일 때) |

### 4. ShipRequested Outbox 생성 시점

**DeployExecutor에서 명시적으로 생성**:
- `ship.async()` 호출 시
- `shipAsync()` 메서드 호출 시
- `shipAsyncTo()` 메서드 호출 시

**자동 생성 안 됨**:
- SlicingWorkflow 완료 후 자동으로 ShipRequested 생성 안 함
- 명시적으로 DeployExecutor에서 ship.async() 호출해야 함

---

## 실제 코드 위치

### ShipRequested Outbox 생성
- **파일**: `src/main/kotlin/com/oliveyoung/ivmlite/sdk/execution/DeployExecutor.kt`
- **라인**: 177-203 (ship.async() 경로)

### ShipRequested 처리
- **파일**: `src/main/kotlin/com/oliveyoung/ivmlite/pkg/orchestration/application/ShipEventHandler.kt`
- **라인**: 45-79 (processShipRequested)

### OutboxPollingWorker 라우팅
- **파일**: `src/main/kotlin/com/oliveyoung/ivmlite/pkg/orchestration/application/OutboxPollingWorker.kt`
- **라인**: 257-272 (processEntry → handleSliceEvent)

---

## 요약

```
Slice 생성 (SlicingWorkflow)
  ↓
DeployExecutor.ship.async() 호출
  ↓
ShipRequested Outbox 생성 (aggregateType: SLICE)
  ↓
OutboxPollingWorker.poll()
  ↓
ShipEventHandler.handleSliceEvent()
  ↓
ShipWorkflow.execute()
  ↓
Sink.ship() → OpenSearch/Personalize
```

**핵심**: Slice 생성과 Ship 트리거는 **명시적으로 분리**되어 있으며, `ship.async()` 호출 시에만 ShipRequested outbox가 생성됩니다.
