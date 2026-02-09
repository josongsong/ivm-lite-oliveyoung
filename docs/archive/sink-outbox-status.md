# Sink Outbox 설정 현황

## 현재 상태

### ✅ 구현된 것

1. **ShipRequested 이벤트 생성** (DeployExecutor.kt:177-191)
   ```kotlin
   ShipMode.Async -> {
       val shipTaskEntry = OutboxEntry.create(
           aggregateType = AggregateType.SLICE,
           aggregateId = "${tenantId}:${entityKey}",
           eventType = "ShipRequested",  // ✅ 이벤트 타입
           payload = {
               "tenantId": "...",
               "entityKey": "...",
               "version": "...",
               "sink": "opensearch",
               "shipMode": "async"
           }
       )
       outboxRepository.insert(shipTaskEntry)
   }
   ```

2. **ShipEventHandler 구현** (ShipEventHandler.kt)
   ```kotlin
   class ShipEventHandler(
       private val shipWorkflow: ShipWorkflow,
       ...
   ) : OutboxPollingWorker.EventHandler {
       override suspend fun handleSliceEvent(entry: OutboxEntry) {
           when (entry.eventType) {
               "ShipRequested" -> processShipRequested(entry)  // ✅ 처리 로직
               ...
           }
       }
   }
   ```

3. **WorkflowModule에 등록** (WorkflowModule.kt:111-117)
   ```kotlin
   single<OutboxPollingWorker.EventHandler>(qualifier = named("ship")) {
       ShipEventHandler(...)  // ✅ 등록됨
   }
   ```

### ❌ 문제점

**OutboxPollingWorker가 ShipEventHandler를 사용하지 않음**

```kotlin
// WorkerModule.kt:18-25
single {
    OutboxPollingWorker(
        outboxRepo = get(),
        slicingWorkflow = get(),
        config = get(),
        tracer = get(),
        // ❌ eventHandler 파라미터 없음 → DefaultEventHandler 사용
    )
}
```

**결과:**
- ShipRequested 이벤트는 outbox에 저장됨
- 하지만 OutboxPollingWorker가 처리하지 않음 (DefaultEventHandler는 no-op)
- ShipEventHandler가 호출되지 않음

---

## 해결 방법

### Option 1: WorkerModule 수정 (추천)

```kotlin
// WorkerModule.kt
single {
    OutboxPollingWorker(
        outboxRepo = get(),
        slicingWorkflow = get(),
        config = get(),
        tracer = get(),
        eventHandler = get<OutboxPollingWorker.EventHandler>(qualifier = named("ship"))  // ✅ 추가
    )
}
```

### Option 2: 기본 EventHandler를 ShipEventHandler로 변경

```kotlin
// WorkerModule.kt
single {
    OutboxPollingWorker(
        outboxRepo = get(),
        slicingWorkflow = get(),
        config = get(),
        tracer = get(),
        eventHandler = ShipEventHandler(...)  // ✅ 직접 주입
    )
}
```

---

## 현재 동작 흐름

### Sync 모드 (현재 동작)

```
DeployExecutor.executeSync()
  → ShipMode.Sync
  → shipWorkflow.executeToMultipleSinks()  // ✅ 직접 실행
  → OpenSearchSinkAdapter.ship()
```

### Async 모드 (이벤트만 생성, 처리 안됨)

```
DeployExecutor.executeSync()
  → ShipMode.Async
  → OutboxEntry.create("ShipRequested")  // ✅ 이벤트 생성
  → outboxRepository.insert()  // ✅ 저장됨
  → (OutboxPollingWorker가 처리 안함)  // ❌ 문제
```

---

## 수정 후 동작 흐름 (예상)

### Async 모드 (수정 후)

```
DeployExecutor.executeSync()
  → ShipMode.Async
  → OutboxEntry.create("ShipRequested")
  → outboxRepository.insert()
  ↓
OutboxPollingWorker.poll()
  → SLICE aggregateType 이벤트 발견
  → eventHandler.handleSliceEvent()  // ✅ ShipEventHandler 호출
  → ShipEventHandler.processShipRequested()
  → shipWorkflow.execute()
  → OpenSearchSinkAdapter.ship()
```

---

## 요약

| 항목 | 상태 |
|------|------|
| ShipRequested 이벤트 생성 | ✅ 구현됨 |
| ShipEventHandler 구현 | ✅ 구현됨 |
| OutboxPollingWorker 연결 | ❌ **미연결** |
| 실제 Ship 실행 (Async) | ❌ **동작 안함** |

**핵심**: 코드는 다 구현되어 있지만, **DI 연결만 안 되어 있음**.
