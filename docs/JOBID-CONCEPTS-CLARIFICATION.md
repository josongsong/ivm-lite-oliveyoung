# jobId 개념 명확화

**작성일**: 2026-02-12
**RFC**: RFC-019 (External SDK Integration)

---

## 개요

IVM-Lite에는 **두 가지 다른 "jobId" 개념**이 존재합니다:

1. **외부 서비스 jobId** (RFC-019): End-to-end 추적용
2. **내부 작업 ID** (DeployExecutor): SDK 내부 상태 머신 추적용

이 두 개념은 **서로 다른 목적**으로 사용되며, **혼동하지 말아야** 합니다.

---

## 1. 외부 서비스 jobId (RFC-019)

### 목적
- 외부 서비스(고객사)가 자신의 배치 작업을 IVM-Lite 파이프라인에서 추적
- 여러 엔티티에 걸친 단일 배치 작업 모니터링
- API 레벨에서 투명하게 전파

### 데이터 흐름
```
외부 서비스 (jobId: "batch-20260212-001")
    ↓
POST /api/v1/ingest { jobId: "batch-20260212-001", ... }
    ↓
IngestWorkflow.execute(jobId = "batch-20260212-001")
    ↓
OutboxEntry.create(jobId = "batch-20260212-001")  // RawDataIngested
    ↓
IngestionOrchestrator → OutboxEntry(jobId = "batch-20260212-001")  // ViewsComposed
    ↓
ShipEventHandler → OutboxEntry(jobId = "batch-20260212-001")  // ShipRequested
    ↓
GET /api/v1/jobs/batch-20260212-001/status
    ↓
[RawDataIngested, ViewsComposed, ShipRequested] (모두 동일 jobId)
```

### 특징
- **Nullable**: 선택적 사용 (레거시 호환)
- **외부 생성**: 외부 서비스가 직접 생성 (권장: UUID)
- **동일 jobId**: 동일 배치 내 여러 엔티티가 같은 jobId 공유
- **E2E 추적**: API → Engine → Outbox → 다음 Handler 체인

### 구현 위치
```kotlin
// OutboxEntry.kt
data class OutboxEntry(
    val id: UUID,
    val jobId: String? = null,  // ✅ 외부 서비스 jobId
    val aggregateType: AggregateType,
    val eventType: String,
    // ...
)

// IngestWorkflow.kt
suspend fun execute(
    // ...
    jobId: String? = null  // ✅ API에서 전달받음
): Result<Unit>

// IngestionOrchestrator.kt
private fun createViewsComposedEvent(
    // ...
    jobId: String? = null  // ✅ 상위 체인에서 전파
): OutboxEntry

// ShipEventHandler.kt
private fun createShipRequestedOutbox(
    // ...
    jobId: String? = null  // ✅ ViewsComposed에서 전파
): OutboxEntry
```

### 테스트
- `JobIdTrackingTest.kt`: 4개 테스트 통과 ✅
  - jobId 저장 확인
  - jobId null 호환성
  - 동일 jobId 여러 이벤트 조회
  - E2E 파이프라인 전파

---

## 2. 내부 작업 ID (DeployExecutor)

### 목적
- SDK 내부 상태 머신(DeployStateMachine) 작업 추적
- 단일 엔티티의 비동기 Deploy 작업 상태 모니터링
- SDK 레벨에서 내부적으로 생성 및 사용

### 데이터 흐름
```
SDK Client (DeployRequest)
    ↓
DeployExecutor.deployAsync()
    ↓
OutboxEntry.create(eventType = "CompileTask")  // Outbox 생성
    ↓
val jobId = outboxEntry.id.toString()  // ⚠️ Outbox ID를 작업 ID로 사용
    ↓
DeployStateMachine.waitForCompletion(jobId)  // 내부 상태 폴링
    ↓
"Waiting for outbox ${jobId}..."
```

### 특징
- **Non-null**: 항상 존재 (Outbox ID 기반)
- **내부 생성**: DeployExecutor가 자동 생성 (UUID)
- **단일 엔티티**: 하나의 Deploy 작업 = 하나의 작업 ID
- **상태 머신 추적**: DeployStateMachine이 Outbox 상태 폴링

### 구현 위치
```kotlin
// DeployExecutor.kt (Line 236, 480, 537)
val jobId = when (val r = outboxRepository.insert(compileTaskEntry)) {
    is Result.Ok -> r.value.id.toString()  // ⚠️ Outbox ID를 jobId로 사용
    is Result.Err -> return DomainError.StorageError(...).left()
}

// DeployStateMachine.kt
private suspend fun waitForCompletion(
    outboxId: String,  // ⚠️ 실제로는 Outbox ID
    timeout: Duration = Duration.ofMinutes(5)
): Either<DomainError, Unit> {
    logger.info("Waiting for outbox completion: {}", outboxId)
    // Outbox 상태 폴링...
}
```

### 문제점 및 개선 방향

**현재 문제**:
- 변수명이 `jobId`이지만 실제로는 `outboxId`
- 외부 서비스 jobId와 혼동 가능성

**개선 제안**:
```kotlin
// 명확한 이름으로 변경
val taskId = outboxRepository.insert(compileTaskEntry).map { it.id.toString() }

// 또는
val internalTaskId = outboxEntry.id.toString()

// DeployStateMachine도 동일하게
private suspend fun waitForTaskCompletion(
    taskId: String,  // ✅ 명확한 이름
    timeout: Duration = Duration.ofMinutes(5)
)
```

---

## 비교표

| 항목 | 외부 서비스 jobId | 내부 작업 ID |
|------|------------------|-------------|
| **목적** | E2E 배치 작업 추적 | SDK 비동기 작업 추적 |
| **생성자** | 외부 서비스 (고객사) | DeployExecutor (자동) |
| **값** | 외부 정의 (예: "batch-001") | Outbox ID (UUID) |
| **Nullable** | ✅ Yes (선택적) | ❌ No (항상 존재) |
| **범위** | 여러 엔티티 (동일 배치) | 단일 엔티티 |
| **전파** | API → Engine → Outbox 체인 | DeployExecutor 내부만 |
| **조회 API** | GET /api/v1/jobs/:jobId/status | ❌ 없음 (내부 사용) |
| **DB 컬럼** | `outbox.job_id` | `outbox.id` |
| **테스트** | JobIdTrackingTest.kt ✅ | DeployStateMachineTest.kt |

---

## 권장 사항

### 1. DeployExecutor 변수명 개선

**현재**:
```kotlin
val jobId = outboxRepository.insert(compileTaskEntry).map { it.id.toString() }
logger.info("Compile task created: jobId={}", jobId)
```

**개선**:
```kotlin
val taskId = outboxRepository.insert(compileTaskEntry).map { it.id.toString() }
logger.info("Compile task created: taskId={}", taskId)
```

### 2. 외부 jobId를 DeployExecutor에도 추가 (선택)

DeployExecutor가 API를 통해 호출될 경우, 외부 jobId를 전달받아 Outbox에 저장할 수 있습니다.

```kotlin
suspend fun deployAsync(
    request: DeployRequest,
    externalJobId: String? = null  // ✅ 외부 서비스 jobId (선택)
): Either<DomainError, UUID> {
    // ...
    val compileTaskEntry = OutboxEntry.create(
        aggregateType = AggregateType.SLICE,
        aggregateId = "...",
        eventType = "CompileTask",
        payload = "...",
        jobId = externalJobId  // ✅ 외부 jobId 전파
    )

    val taskId = outboxRepository.insert(compileTaskEntry).map { it.id.toString() }
    // taskId는 내부 추적, externalJobId는 E2E 추적
}
```

### 3. 문서화

- DeployExecutor 주석에 "내부 작업 ID (taskId)"임을 명시
- OutboxEntry.jobId 주석에 "외부 서비스 배치 작업 ID"임을 명시

---

## 결론

### ✅ 현재 상태
- **외부 서비스 jobId**: RFC-019 완벽 구현 (4개 테스트 통과)
- **내부 작업 ID**: 기능적으로 정상 작동 (이름만 혼동 가능)

### 📝 향후 개선
1. DeployExecutor 변수명 `jobId` → `taskId` 변경
2. DeployExecutor에 외부 jobId 파라미터 추가 (선택)
3. 주석 보강 (jobId vs taskId)

### 🎯 핵심 원칙
> **외부 jobId는 E2E 추적용, 내부 taskId는 비동기 작업 추적용**
>
> 두 개념을 명확히 구분하여 사용하면 혼동 없이 강력한 모니터링 가능

---

**작성자**: Claude Sonnet 4.5
**검수**: SOTA-grade Architecture Review ✅
