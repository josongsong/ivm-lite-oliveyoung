# 윈도우 배치 Ship 처리

Kafka 등으로 보낼 때 변경 드리프트를 줄이기 위한 윈도우 배치 처리 전략입니다.

## 문제 상황

Kafka로 Ship할 때:
- 같은 엔티티가 짧은 시간에 여러 번 변경되면 드리프트 발생
- 개별 메시지로 보내면 순서 문제, 중복 처리 등 이슈 발생
- 변경이 너무 많아서 소비자가 따라가지 못함

## 해결 방법

**윈도우 배치 처리**를 통해:
1. 일정 시간(윈도우) 동안 쌓인 ShipRequested를 모아서 배치 처리
2. 같은 엔티티의 여러 변경을 하나로 합침 (latest wins)
3. 배치 크기 제한으로 즉시 처리 가능

## 사용법

### 1. 설정 추가

`application.yaml`:

```yaml
worker:
  enabled: true
  batchSize: 100
  windowedBatch:
    enabled: true           # 윈도우 배치 활성화
    windowSizeMs: 5000      # 5초 윈도우
    maxBatchSize: 100       # 최대 100개까지 모음
    dedupeByEntity: true    # 같은 엔티티 중복 제거
```

### 2. 코드 통합

`WorkerModule.kt` 또는 DI 설정:

```kotlin
// 기본 ShipEventHandler
val shipEventHandler = ShipEventHandler(
    shipWorkflow = get(),
    slicingWorkflow = get(),
    deployJobRepository = get()
)

// 윈도우 배치로 래핑
val windowedHandler = if (workerConfig.windowedBatch?.enabled == true) {
    WindowedBatchShipHandler(
        delegate = shipEventHandler,
        windowSizeMs = workerConfig.windowedBatch.windowSizeMs,
        maxBatchSize = workerConfig.windowedBatch.maxBatchSize,
        dedupeByEntity = workerConfig.windowedBatch.dedupeByEntity
    )
} else {
    shipEventHandler
}

// OutboxPollingWorker에 주입
single<OutboxPollingWorker.EventHandler>(qualifier = named("ship")) {
    windowedHandler
}
```

### 3. 동작 방식

#### 시나리오 1: 시간 기반 윈도우

```
시간: 0초    1초    2초    3초    4초    5초
      ↓      ↓      ↓      ↓      ↓      ↓
이벤트: A1   B1    A2    C1    A3    [처리]
                              ↑
                        윈도우 닫힘 (5초 경과)
                        
결과: A3, B1, C1만 전송 (A1, A2는 중복 제거됨)
```

#### 시나리오 2: 배치 크기 제한

```
이벤트: 1, 2, 3, ..., 98, 99, 100
                              ↑
                        배치 크기 도달 (100개)
                        
결과: 즉시 처리 (윈도우 시간 기다리지 않음)
```

#### 시나리오 3: 중복 제거 비활성화

```yaml
windowedBatch:
  dedupeByEntity: false
```

```
이벤트: A1, B1, A2, C1, A3
결과: 모든 이벤트 전송 (A1, A2, A3 모두 포함)
```

## 모니터링

윈도우 상태 조회:

```kotlin
val windowedHandler = get<WindowedBatchShipHandler>()
val status = windowedHandler.getWindowStatus()

status.forEach { (sinkType, status) ->
    println("Sink: $sinkType")
    println("  Pending: ${status.pendingCount}")
    println("  Oldest Age: ${status.oldestEntryAgeMs}ms")
    println("  Has Timer: ${status.hasTimer}")
}
```

## Graceful Shutdown

애플리케이션 종료 시 모든 윈도우 플러시:

```kotlin
// Shutdown hook
Runtime.getRuntime().addShutdownHook(Thread {
    runBlocking {
        windowedHandler.flushAll()
    }
})
```

## 설정 가이드

### Kafka Sink용 권장 설정

```yaml
worker:
  windowedBatch:
    enabled: true
    windowSizeMs: 5000      # 5초: 변경 드리프트 감소
    maxBatchSize: 100       # 100개: Kafka 배치 최적화
    dedupeByEntity: true     # 중복 제거: latest wins
```

### OpenSearch Sink용 권장 설정

```yaml
worker:
  windowedBatch:
    enabled: false           # 즉시 처리 (검색 인덱스는 실시간성 중요)
```

또는 작은 윈도우:

```yaml
worker:
  windowedBatch:
    enabled: true
    windowSizeMs: 1000      # 1초: 짧은 윈도우
    maxBatchSize: 50        # 작은 배치
    dedupeByEntity: true
```

### Personalize Sink용 권장 설정

```yaml
worker:
  windowedBatch:
    enabled: true
    windowSizeMs: 10000     # 10초: 긴 윈도우 (배치 처리 최적화)
    maxBatchSize: 500        # 큰 배치
    dedupeByEntity: true     # 중복 제거 필수
```

## 성능 고려사항

1. **메모리 사용량**: 윈도우 큐에 쌓이는 이벤트 수만큼 메모리 사용
2. **지연 시간**: 윈도우 크기만큼 지연 발생 (최대 windowSizeMs)
3. **처리량**: 배치 처리로 처리량 향상 가능

## 주의사항

1. **중복 제거**: `dedupeByEntity=true`일 때 같은 엔티티의 이전 버전은 무시됨
2. **순서 보장**: 윈도우 내에서는 순서가 보장되지 않음 (latest wins)
3. **에러 처리**: 배치 내 일부 실패 시 개별 처리 (전체 롤백 없음)

## 향후 개선

- [ ] ShipWorkflow.executeBatch()를 활용한 진짜 배치 처리
- [ ] SinkType별 다른 윈도우 설정 지원
- [ ] 메트릭 수집 (윈도우 크기, 처리 시간 등)
- [ ] 동적 윈도우 크기 조정 (부하에 따라)
