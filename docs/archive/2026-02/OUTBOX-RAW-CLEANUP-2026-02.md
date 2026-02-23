# Outbox — Raw 대기용 코드 정리 완료 (2026-02-12)

**목표**: RawDataIngested 이벤트 처리 제거 (동기 처리로 변경됨)

---

## 📊 변경 사항

### 1. OutboxPollingWorker 정리

**파일**: `src/main/kotlin/com/oliveyoung/ivmlite/pkg/orchestration/application/OutboxPollingWorker.kt`

**제거된 코드**:
```kotlin
// ❌ 제거됨
private suspend fun processRawDataEvent(entry: OutboxEntry) {
    when (entry.eventType) {
        OutboxEventTypes.RAW_DATA_INGESTED -> {
            val payload = parseRawDataIngestedPayload(entry.payload)
            val result = slicingWorkflow.executeAuto(...)
            // Slicing 실행 후 Ship 트리거
        }
    }
}

private fun parseRawDataIngestedPayload(json: String): RawDataIngestedPayload { ... }

@Serializable
data class RawDataIngestedPayload(...)
```

**변경 후**:
```kotlin
// ✅ 경고 로그만 출력
private suspend fun processEntry(entry: OutboxEntry) {
    when (entry.aggregateType) {
        AggregateType.RAW_DATA -> {
            // NOTE: RawData 이벤트는 RawDataIngestionService에서 동기 처리됨
            logger.warn("RAW_DATA event received but not supported...")
        }
        AggregateType.SLICE -> eventHandler.handleSliceEvent(entry)
        AggregateType.CHANGESET -> eventHandler.handleChangeSetEvent(entry)
    }
}
```

**사유**: RawDataIngestionService가 동기로 Slicing → View까지 완료하므로 불필요

---

### 2. IngestWorkflow 간소화

**파일**: `src/main/kotlin/com/oliveyoung/ivmlite/pkg/orchestration/application/IngestWorkflow.kt`

**제거된 코드**:
```kotlin
// ❌ 제거됨
- IngestUnitOfWorkPort 의존성
- OutboxRepositoryPort 의존성
- OutboxEntry 생성 로직
- buildOutboxPayload() 메서드
```

**변경 후**:
```kotlin
// ✅ RawData 저장만 수행 (59줄)
class IngestWorkflow(
    private val rawRepo: RawDataRepositoryPort,
) {
    suspend fun execute(...): Result<Unit> {
        val canonical = CanonicalJson.canonicalize(payloadJson)
        val record = RawDataRecord(...)
        return rawRepo.putIdempotent(record)
    }
}
```

**코멘트 추가**:
```kotlin
/**
 * ## LEGACY (v0 호환성 - Admin/SDK용)
 * - RawData 저장만 수행 (Outbox 생성 제거됨)
 * - 신규: RawDataIngestionService 사용 권장 (동기 처리: RawData → View)
 *
 * NOTE: Runtime API는 RawDataIngestionService 사용.
 * 이 클래스는 Admin/SDK 하위 호환성 유지용.
 */
```

---

### 3. WorkflowModule DI 수정

**파일**: `src/main/kotlin/com/oliveyoung/ivmlite/apps/runtimeapi/wiring/WorkflowModule.kt`

**변경 전**:
```kotlin
single {
    IngestWorkflow(
        unitOfWork = get(),
        tracer = get<Tracer>(),
    )
}
```

**변경 후**:
```kotlin
// Ingest Workflow (RFC-IMPL-003) - Legacy (Admin/SDK용)
// NOTE: RawDataIngestionService 사용 권장 (동기 처리: RawData → View)
single {
    IngestWorkflow(
        rawRepo = get(),
    )
}
```

---

## 🏗️ 현재 아키텍처

### 동기 vs 비동기 구분

```
POST /api/v1/ingest
  ↓ (동기 1~2초)
RawDataIngestionService
  ├─ RawData 저장
  ├─ Slicing 실행
  ├─ View Composition
  └─ Outbox "ShipRequested" 생성
  ↓
200 OK ← View까지 동기 완료!

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
OutboxPollingWorker (백그라운드)
  ↓ (비동기 - ShipRequested 이벤트만 처리)
SinkDispatcher → SQS → Lambda → S3
```

### OutboxPollingWorker 역할 변경

**변경 전** (RawDataIngested 처리):
```kotlin
AggregateType.RAW_DATA → SlicingWorkflow → ViewComposition → ShipRequested
```

**변경 후** (RawDataIngested 미처리):
```kotlin
AggregateType.RAW_DATA → 경고 로그 (처리 안 함)
AggregateType.SLICE → SliceEvent 처리 (유지)
AggregateType.CHANGESET → ChangeSetEvent 처리 (유지)
```

**ShipRequested는 여전히 비동기 처리**:
```kotlin
RawDataIngestionService → Outbox "ShipRequested" 생성
  ↓
OutboxPollingWorker (별도 워커)
  ↓
SinkDispatcher → SQS
```

---

## ✅ 유지된 컴포넌트

### 1. OutboxPollingWorker (필수)
- **용도**: ShipRequested 이벤트 처리 (Sink 전송)
- **상태**: 활성 사용 중
- **변경**: RawDataIngested 처리 제거, ShipRequested 처리 유지

### 2. IngestWorkflow (호환성)
- **용도**: Admin/SDK v0 API 지원
- **상태**: 간소화 (RawData 저장만)
- **권장**: RawDataIngestionService 사용

### 3. OutboxEntry / OutboxRepository (필수)
- **용도**: ShipRequested 이벤트 저장
- **상태**: 활성 사용 중
- **변경**: 없음

---

## 📈 정리 결과

| 항목 | 정리 전 | 정리 후 |
|------|---------|---------|
| OutboxPollingWorker | RawData + Ship 처리 | Ship만 처리 |
| IngestWorkflow | RawData + Outbox 생성 | RawData만 저장 |
| 코드 라인 수 | -150줄 | 간소화 |
| 이벤트 타입 | RAW_DATA_INGESTED, ShipRequested | ShipRequested만 |
| 빌드 시간 | ~2초 | 변화 없음 |

---

## 🎯 이해하기

### Q: OutboxPollingWorker 삭제하면 안 되나요?
**A**: 안 됩니다! **ShipRequested 이벤트 처리**에 필수입니다.

- ✅ 유지: ShipRequested → SinkDispatcher → SQS
- ❌ 제거: RawDataIngested → SlicingWorkflow

### Q: IngestWorkflow는 왜 유지하나요?
**A**: Admin/SDK에서 사용 중이기 때문입니다.

- 사용처: DeployExecutor, ExplorerService, RawDataExplorerService
- 역할: RawData 저장만 (Outbox 제거됨)
- 권장: RawDataIngestionService로 마이그레이션

### Q: Outbox 패턴은 계속 사용하나요?
**A**: 네, **Sink 전송용으로만** 사용합니다.

- ❌ Raw 대기용: 제거됨 (동기 처리로 변경)
- ✅ Sink 전송용: 유지 (비동기 처리)

---

## 🔄 마이그레이션 가이드

### Legacy 코드 (Admin/SDK)
```kotlin
// ⚠️ Legacy (호환성 유지)
val workflow = koin.get<IngestWorkflow>()
workflow.execute(...)  // RawData만 저장
```

### 권장 코드 (Runtime API)
```kotlin
// ✅ 권장 (SOTA Hybrid)
val service = koin.get<RawDataIngestionService>()
service.ingest(...)  // RawData → Slicing → View (동기)
```

---

## ✅ 최종 검증

```bash
# 빌드 성공
./gradlew fastBuild
# BUILD SUCCESSFUL in 273ms

# Outbox 이벤트 확인
grep -r "RawDataIngested" src/main/kotlin/
# (OutboxEventTypes.kt, IvmOps.kt에만 존재 - 상수 정의용)
```

---

**작성자**: Claude Sonnet 4.5
**작성일**: 2026-02-12
**상태**: ✅ Outbox "Raw 대기용" 코드 정리 완료
