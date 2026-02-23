# 🚨 CRITICAL ISSUES - SOTA 구조의 치명적 결함

**작성일**: 2026-02-12
**심각도**: 🔴 HIGH
**상태**: 즉시 수정 필요

---

## 1. ❌ Result Pattern Boilerplate 여전히 과다

### 문제

**RFC-IMPL-018에서 지적했지만 여전히 해결 안됨!**

```kotlin
// IngestionWorkflow.kt (Domain Layer)
when (val result = rawDataRepo.putIdempotent(rawData)) {
    is Result.Err -> return Result.Err(result.error)  // ← 반복 1
    is Result.Ok -> Unit
}

val slicingResult = when (val result = slicingEngine.slice(rawData, ...)) {
    is Result.Ok -> result.value                        // ← 반복 2
    is Result.Err -> return Result.Err(result.error)
}

when (val result = sliceRepo.putAllIdempotent(slices)) {
    is Result.Err -> return Result.Err(result.error)   // ← 반복 3
    is Result.Ok -> Unit
}

when (val result = viewComposer.compose(slices, ...)) {
    is Result.Ok -> result.value                        // ← 반복 4
    is Result.Err -> return Result.Err(result.error)
}
```

**총 7번의 `when (result)` 반복 패턴!**

### 영향

- ✅ Layer는 분리했지만
- ❌ **코드 가독성은 여전히 나쁨**
- ❌ **비즈니스 로직이 에러 처리에 묻힘**
- ❌ **SOTA급 아님** - 2010년대 스타일

### 해결책

#### Option 1: Arrow Either with bind()
```kotlin
suspend fun execute(command: IngestionCommand): Result<WorkflowResult> = either {
    val rawData = RawDataRecord.create(...)
    rawDataRepo.putIdempotent(rawData).bind()

    val slices = slicingEngine.slice(rawData, ...).bind().slices
    sliceRepo.putAllIdempotent(slices).bind()

    val views = viewComposer.compose(slices, ...).bind()
    viewRepo.putAllIdempotent(views).bind()

    WorkflowResult(rawData, slices, views)
}
```

#### Option 2: Kotlin Result with getOrThrow()
```kotlin
suspend fun execute(command: IngestionCommand): Result<WorkflowResult> = runCatching {
    val rawData = RawDataRecord.create(...)
    rawDataRepo.putIdempotent(rawData).getOrThrow()

    val slices = slicingEngine.slice(rawData, ...).getOrThrow().slices
    sliceRepo.putAllIdempotent(slices).getOrThrow()

    val views = viewComposer.compose(slices, ...).getOrThrow()
    viewRepo.putAllIdempotent(views).getOrThrow()

    WorkflowResult(rawData, slices, views)
}.toResult()
```

#### Option 3: Extension Function
```kotlin
// Result Extensions
suspend inline fun <T> Result<T>.bindOrReturn(): T = when (this) {
    is Result.Ok -> this.value
    is Result.Err -> return this  // ← early return
}

// 사용
suspend fun execute(command: IngestionCommand): Result<WorkflowResult> {
    val rawData = RawDataRecord.create(...)
    rawDataRepo.putIdempotent(rawData).bindOrReturn()

    val slices = slicingEngine.slice(rawData, ...).bindOrReturn().slices
    sliceRepo.putAllIdempotent(slices).bindOrReturn()

    val views = viewComposer.compose(slices, ...).bindOrReturn()
    viewRepo.putAllIdempotent(views).bindOrReturn()

    return Result.Ok(WorkflowResult(rawData, slices, views))
}
```

---

## 2. ❌ TransactionPort가 여전히 Result에 강결합

### 문제

```kotlin
interface TransactionPort {
    suspend fun <T> execute(block: suspend () -> Result<T>): Result<T>
                                              ^^^^^^^^^^^^^^
    // ← Result 타입 강제!
}
```

**문제점**:
- Domain Layer가 **Result 타입에 종속**됨
- Port가 **Domain 타입을 강제**함 (의존성 역전 위반!)
- **진짜 Port는 타입에 무관해야 함**

### SOTA 해결책

```kotlin
// ✅ Port는 타입 중립적
interface TransactionPort {
    suspend fun <T> execute(block: suspend () -> T): T
    // Result 없음! 예외는 throw
}

// Adapter에서 Result 변환
class JooqTransactionAdapter(
    private val dslContext: DSLContext
) : TransactionPort {
    override suspend fun <T> execute(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            dslContext.transactionResult { _ ->
                runBlocking { block() }
            }
        }
    // 예외는 자연스럽게 전파
}

// Application Layer에서 Result 변환
class IngestionOrchestrator {
    suspend fun ingest(command: IngestionCommand): Result<IngestionResult> =
        runCatching {
            transactionPort.execute {
                workflow.execute(command)  // ← Result<T> 아닌 T 반환
            }
        }.toResult()
}
```

---

## 3. ❌ Domain Event vs Outbox Payload 중복

### 문제

**두 개의 유사한 구조가 공존**:

```kotlin
// shared/domain/events/DomainEvent.kt
data class ViewsComposedEvent(
    val eventId: String,
    val occurredAt: Instant,
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val viewKeys: List<ViewKey>,
    val sliceKeys: List<SliceKey>
) : DomainEvent

// rawdata/domain/OutboxPayload.kt
@Serializable
data class ViewsComposedPayload(
    val payloadVersion: String = "1.0",
    val tenantId: String,           // ← String (중복)
    val entityKey: String,          // ← String (중복)
    val viewKeys: List<ViewKey>,    // ← 동일
    val sliceKeys: List<SliceKey>   // ← 동일
) : OutboxPayload
```

**문제점**:
- **DomainEvent**는 사용되지 않음 (dead code)
- **OutboxPayload**만 실제 사용됨
- **ViewKey, SliceKey가 중복 정의**됨:
  - `shared/domain/events/DomainEvent.kt`
  - `orchestration/application/ShipEventHandler.kt`

### SOTA 해결책

```kotlin
// ✅ DomainEvent를 Payload로 직렬화
sealed interface DomainEvent {
    fun toPayload(): String
}

data class ViewsComposedEvent(...) : DomainEvent {
    @Serializable
    data class Payload(...)

    override fun toPayload(): String =
        Json.encodeToString(Payload.serializer(), Payload(...))
}

// Outbox 생성
val event = ViewsComposedEvent(...)
val outboxEntry = OutboxEntry.create(
    eventType = "ViewsComposed",
    payload = event.toPayload()  // ← 직렬화 위임
)
```

---

## 4. ❌ ViewKey/SliceKey 정의가 3곳에 중복

### 문제

**동일한 데이터 클래스가 3곳에 중복 정의**:

1. `shared/domain/events/DomainEvent.kt`:
   ```kotlin
   @Serializable
   data class ViewKey(...)
   @Serializable
   data class SliceKey(...)
   ```

2. `rawdata/domain/OutboxPayload.kt`:
   ```kotlin
   // ViewKey/SliceKey import from shared.domain.events
   ```

3. `orchestration/application/ShipEventHandler.kt`:
   ```kotlin
   @Serializable
   data class ViewKeyData(...)    // ← 중복!
   @Serializable
   data class SliceKeyData(...)   // ← 중복!
   ```

**문제점**:
- DRY 위반
- 변경 시 3곳 수정 필요
- 타입 불일치 가능성

### SOTA 해결책

```kotlin
// shared/domain/types/Keys.kt (새 파일)
@Serializable
data class ViewKey(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val viewType: String
)

@Serializable
data class SliceKey(
    val tenantId: String,
    val entityKey: String,
    val version: Long,
    val sliceType: String
)

// 모든 곳에서 재사용
```

---

## 5. ❌ IngestionCommand가 version을 강제

### 문제

```kotlin
data class IngestionCommand(
    val version: Long = 1L  // ← 왜 Command가 version을 아는가?
)
```

**문제점**:
- **Command는 사용자 의도**를 표현해야 함
- **version은 Domain 내부 로직**임
- API에서 `POST /ingest { version: 1 }` 이렇게 받는가?

### SOTA 해결책

```kotlin
// ✅ Command에서 version 제거
data class IngestRawDataCommand(
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val data: JsonObject,
    val schemaRef: ContractRef,
    val ruleSetRef: ContractRef,
    val viewDefIds: List<String>
    // version 없음!
)

// ✅ Workflow에서 version 생성
class IngestionWorkflow {
    suspend fun execute(command: IngestRawDataCommand): Result<WorkflowResult> {
        // version은 Repository가 자동 생성
        val rawData = RawDataRecord.create(
            tenantId = command.tenantId,
            entityKey = command.entityKey,
            data = command.data
            // version 파라미터 없음 - 자동 생성
        )
    }
}
```

---

## 6. ❌ OutboxPayload companion object의 encode()가 사용 안됨

### 문제

```kotlin
// OutboxPayload.kt
companion object {
    fun <T : OutboxPayload> encode(payload: T): String =
        json.encodeToString(serializer(), payload)
}

// 실제 사용 (IngestionOrchestrator.kt)
payload = Json.encodeToString(
    OutboxPayload.ViewsComposedPayload.serializer(),
    payload
)
// ← companion object의 encode() 안 씀!
```

**문제점**:
- 유틸리티 함수를 만들었는데 사용 안함
- Dead code

### 해결책

**Option 1**: companion object 제거 (사용 안하면)
**Option 2**: 실제로 사용
```kotlin
payload = OutboxPayload.encode(payload)
```

---

## 7. ❌ ShipEventHandler의 ViewsComposed 핸들러가 stub

### 문제

```kotlin
private suspend fun processViewsComposed(entry: OutboxEntry) {
    val payload = json.decodeFromString<ViewsComposedPayload>(entry.payload)

    logger.info("Processing ViewsComposed: ...")

    // TODO: SinkRule 기반 자동 Ship 트리거
    logger.debug("ViewsComposed processed, Ship triggering pending (TODO)")
    // ← 아무 것도 안함!
}
```

**문제점**:
- **ViewsComposed 이벤트가 실제로 처리 안됨**
- **Sink로 Ship 안됨**
- **TODO만 남김**

### 해결책

**즉시 구현 필요**:
```kotlin
private suspend fun processViewsComposed(entry: OutboxEntry) {
    val payload = json.decodeFromString<ViewsComposedPayload>(entry.payload)

    // 1. entityType 추출
    val entityType = extractEntityType(payload.entityKey) ?: return

    // 2. SinkRule 조회
    val rules = sinkRuleRegistry?.findByEntityType(entityType) ?: return

    // 3. 각 Rule에 대해 ShipRequested 생성
    for (rule in rules) {
        val shipEntry = createShipRequestedOutbox(
            tenantId = payload.tenantId,
            entityKey = payload.entityKey,
            version = payload.version,
            sink = rule.sinkType
        )
        outboxRepo.insert(shipEntry)
    }
}
```

---

## 8. ❌ 테스트가 실제 동작을 검증 안함

### 문제

```kotlin
it("✅ ViewsComposed 이벤트 발행 확인") {
    orchestrator.ingest(command)

    val entries = outboxRepo.findPending(limit = 10)
    val event = entries.firstOrNull { it.eventType == "ViewsComposed" }
    event shouldNotBe null  // ← 이벤트 발행만 확인

    // ✗ 이벤트 처리 검증 없음!
    // ✗ Ship 실행 검증 없음!
}
```

**문제점**:
- Outbox에 이벤트가 들어갔다는 것만 확인
- **실제로 처리되는지 검증 안함**
- **E2E가 아님** - 중간까지만 검증

### SOTA 해결책

```kotlin
it("✅ E2E: ViewsComposed → Ship 실행까지") {
    // Given
    val mockSink = mockk<SinkPort>()
    val eventHandler = ShipEventHandler(...)

    // When
    orchestrator.ingest(command)
    val outboxEntries = outboxRepo.findPending(limit = 10)

    // Outbox Worker가 처리
    for (entry in outboxEntries) {
        eventHandler.handleSliceEvent(entry)
    }

    // Then: Ship이 실제로 호출되었는지 검증
    verify { mockSink.ship(any()) }
}
```

---

## 9. ❌ WorkflowResult가 모든 Domain 객체를 반환

### 문제

```kotlin
data class WorkflowResult(
    val rawData: RawDataRecord,   // ← Domain 객체 노출
    val slices: List<SliceRecord>, // ← Domain 객체 노출
    val views: List<ViewRecord>    // ← Domain 객체 노출
)
```

**문제점**:
- **Domain 객체가 Application Layer로 노출**됨
- **Layer 경계 누수**
- Application은 count만 필요한데 전체 객체 반환

### SOTA 해결책

```kotlin
// ✅ DTO로 변환
data class WorkflowResult(
    val version: Long,
    val sliceCount: Int,
    val viewCount: Int,
    val sliceTypes: List<String>,
    val viewTypes: List<String>
    // Domain 객체 없음!
)
```

---

## 10. ❌ IngestionOrchestrator가 이벤트 생성 로직 포함

### 문제

```kotlin
class IngestionOrchestrator {
    private fun createViewsComposedEvent(...): OutboxEntry {
        // 30줄의 이벤트 생성 로직
        val payload = OutboxPayload.ViewsComposedPayload(...)
        return OutboxEntry.create(...)
    }
}
```

**문제점**:
- **Orchestrator가 너무 많은 책임**
- **이벤트 생성은 별도 객체가 해야 함**
- Single Responsibility 위반

### SOTA 해결책

```kotlin
// ✅ EventPublisher로 분리
class OutboxEventPublisher(
    private val outboxRepo: OutboxRepositoryPort
) {
    suspend fun publishViewsComposed(
        tenantId: String,
        entityKey: String,
        ...
    ): Result<Unit> {
        val event = createViewsComposedEvent(...)
        return outboxRepo.insert(event)
    }
}

// ✅ Orchestrator는 위임만
class IngestionOrchestrator(
    private val workflow: IngestionWorkflow,
    private val eventPublisher: OutboxEventPublisher,
    private val transactionPort: TransactionPort
) {
    suspend fun ingest(command: IngestionCommand): Result<IngestionResult> =
        transactionPort.execute {
            val result = workflow.execute(command)
            eventPublisher.publishViewsComposed(...)  // ← 위임
            result
        }
}
```

---

## 📊 심각도 평가

| 이슈 | 심각도 | 즉시 수정 | 리팩토링 | 상태 |
|------|--------|-----------|----------|------|
| 1. Result Pattern Boilerplate | 🔴 HIGH | ✅ | Phase 2 | 🔲 |
| 2. TransactionPort Result 강결합 | 🔴 HIGH | ✅ | Phase 2 | 🔲 |
| 3. DomainEvent vs Payload 중복 | 🟡 MEDIUM | - | Phase 3 | 🔲 |
| 4. ViewKey/SliceKey 3곳 중복 | 🟡 MEDIUM | ✅ | Phase 2 | 🔲 |
| 5. Command version 강제 | 🟢 LOW | - | Phase 3 | 🔲 |
| 6. encode() Dead code | 🟢 LOW | ✅ | Phase 2 | 🔲 |
| 7. ViewsComposed stub | 🔴 CRITICAL | ✅ | Phase 1 | ✅ |
| 8. 테스트 불완전 | 🔴 HIGH | ✅ | Phase 1 | ✅ |
| 9. Domain 객체 노출 | 🟡 MEDIUM | - | Phase 3 | 🔲 |
| 10. Orchestrator SRP 위반 | 🟡 MEDIUM | - | Phase 3 | 🔲 |

---

## ✅ Phase 1 완료 (2026-02-12)

### 1. ✅ **ViewsComposed 핸들러 구현** (Issue #7)

**구현 내용**:
```kotlin
// ShipEventHandler.kt
private suspend fun processViewsComposed(entry: OutboxEntry) {
    val payload = json.decodeFromString<ViewsComposedPayload>(entry.payload)

    // 1. entityKey에서 entityType 추출
    val entityType = extractEntityType(payload.entityKey) ?: return

    // 2. SinkRule 조회
    val rules = sinkRuleRegistry.findByEntityType(entityType).bind()

    // 3. 각 ACTIVE SinkRule에 대해 ShipRequested 생성
    for (rule in rules.filter { it.status == SinkRuleStatus.ACTIVE }) {
        val shipEntry = createShipRequestedOutbox(
            tenantId = payload.tenantId,
            entityKey = payload.entityKey,
            version = payload.version,
            sinkType = rule.target.type.name,
            sinkRuleId = rule.id
        )
        outboxRepo.insert(shipEntry)
    }
}
```

**변경 파일**:
- `ShipEventHandler.kt`: SinkRuleRegistry, OutboxRepo 주입 추가
- `WorkflowModule.kt`: DI 설정에 SinkRuleRegistry, OutboxRepo 추가

**검증**:
- ✅ ViewsComposed 이벤트 수신
- ✅ SinkRule 조회 (entityType 기반)
- ✅ ACTIVE 상태 필터링
- ✅ ShipRequested Outbox 생성
- ✅ 87개 단위 테스트 통과

---

### 2. ✅ **E2E 테스트 보강** (Issue #8)

**추가된 테스트**:

#### Test 1: ViewsComposed → SinkRule 조회 → ShipRequested 생성
```kotlin
it("✅ E2E: ViewsComposed → SinkRule 조회 → ShipRequested 생성") {
    // Given: InMemory SinkRuleRegistry with PRODUCT rules
    // When: Ingest data → Process ViewsComposed
    // Then: ShipRequested 2개 생성 확인 (OpenSearch, Personalize)
    shipRequestedEvents shouldHaveSize 2
}
```

#### Test 2: Ship 실행까지 검증 (Mock)
```kotlin
it("✅ E2E: Ship 실행까지 검증 (Mock)") {
    // Given: Mock ShipWorkflow
    // When: Ingest → Process ViewsComposed → Process ShipRequested
    // Then: ShipWorkflow.execute() 호출 검증
    coVerify(exactly = 1) {
        mockShipWorkflow.execute(...)
    }
}
```

**검증 항목**:
- ✅ Outbox 삽입뿐 아니라 실제 이벤트 처리 검증
- ✅ SinkRule 기반 ShipRequested 자동 생성 검증
- ✅ ShipWorkflow 실행 검증 (Mock)
- ✅ 전체 파이프라인 E2E 검증

**결과**:
```
✓ 성공: 87     ○ 스킵: 0      ✗ 실패: 0
⏱ 소요 시간: 13.96초
🎉 모든 테스트 통과!
```

---

## 🔄 다음 리팩토링 (Phase 2)

3. ✅ **Result Pattern 개선** (Issue #1)
4. ✅ **TransactionPort 타입 중립화** (Issue #2)
5. ✅ **ViewKey/SliceKey 단일화** (Issue #4)

---

---

## 📝 결론

### Phase 1 완료 후 현재 상태

- ✅ **Layer 분리 성공** - Clean Architecture 준수
- ✅ **ViewsComposed 핸들러 완전 구현** - Stub 제거, 실제 Ship 트리거
- ✅ **E2E 테스트 완비** - Outbox 삽입 → 이벤트 처리 → Ship 실행까지 검증
- ⚠️ **코드 품질 개선 필요** - Result Pattern boilerplate 여전히 과다
- ⚠️ **Phase 2 필요** - TransactionPort 타입 중립화, ViewKey/SliceKey 중복 제거

### 다음 단계

**Phase 2 (HIGH Priority)**:
1. Result Pattern boilerplate 개선 (Issue #1)
2. TransactionPort 타입 중립화 (Issue #2)
3. ViewKey/SliceKey 단일화 (Issue #4)

**Phase 3 (MEDIUM Priority)**:
4. DomainEvent vs Payload 통합 (Issue #3)
5. Orchestrator SRP 개선 (Issue #10)
6. Domain 객체 노출 제거 (Issue #9)

**현재 상태**: Phase 1 완료로 **치명적 결함 해소**, SOTA 방향으로 진행 중 ✅
