# RFC-IMPL-018: Hybrid Architecture 비판적 검토 및 개선안

**상태**: Draft
**작성일**: 2026-02-12
**작성자**: Claude (SOTA L15급 검토)

---

## 1. Executive Summary

현재 구현된 Hybrid Architecture를 비판적으로 검토하고, Production-ready SOTA급 구조로 개선하기 위한 방안을 제시합니다.

**핵심 개선 포인트**:
- 🔥 **Layer 분리 명확화**: Application vs Domain 경계
- 🔥 **에러 처리 일관성**: Result Pattern 전파 최적화
- 🔥 **트랜잭션 복잡도**: TransactionManager 역할 재정의
- 🔥 **이벤트 네이밍**: SlicingCompleted → ViewsComposed
- 🔥 **테스트 커버리지**: Integration Test 누락

---

## 2. 현재 구조의 문제점

### 2.1 ❌ Layer 경계 모호

**문제**:
```kotlin
// RawDataIngestionService.kt (Application Layer)
class RawDataIngestionService(
    private val rawDataRepo: RawDataRepositoryPort,
    private val sliceRepo: SliceRepositoryPort,
    private val viewRepo: ViewRepositoryPort,
    private val outboxRepo: OutboxRepositoryPort,
    private val slicingEngine: SlicingEnginePort,
    private val viewComposer: ViewComposer,         // ← Domain Service
    private val contractRegistry: ContractRegistryPort,
    private val transactionManager: TransactionManager
) {
    // Application Service가 너무 많은 책임을 가짐
}
```

**문제점**:
1. **Application Service가 Domain Service를 직접 의존** (ViewComposer)
2. **8개의 의존성** - Single Responsibility 위반
3. **트랜잭션 관리 + 비즈니스 로직 + 이벤트 발행** 모두 한 곳에

**SOTA 개선안**:
```kotlin
// Application Layer: 트랜잭션 & 오케스트레이션만
class IngestionOrchestrator(
    private val ingestionWorkflow: IngestionWorkflow,
    private val transactionManager: TransactionManager
) {
    suspend fun ingest(command: IngestCommand): Result<IngestionResult> =
        transactionManager.transaction {
            ingestionWorkflow.execute(command)
        }
}

// Domain Layer: 비즈니스 로직
class IngestionWorkflow(
    private val rawDataRepo: RawDataRepositoryPort,
    private val sliceRepo: SliceRepositoryPort,
    private val viewRepo: ViewRepositoryPort,
    private val slicingEngine: SlicingEnginePort,
    private val viewComposer: ViewComposer
) {
    suspend fun execute(command: IngestCommand): Result<IngestionResult> {
        // 순수 비즈니스 로직 (트랜잭션 무관)
    }
}
```

---

### 2.2 ❌ 이벤트 네이밍 불명확

**문제**:
```kotlin
// SlicingCompleted 이벤트
val outboxEntry = createSlicingCompletedOutbox(...)
```

**문제점**:
- **Slicing은 이미 완료**되었음
- 실제로는 **View Composition까지 완료**된 상태
- `SlicingCompleted`는 오해의 소지

**SOTA 개선안**:
```kotlin
// 이벤트 타입: ViewsComposed (더 명확)
sealed class DomainEvent {
    data class RawDataIngested(val tenantId: TenantId, val entityKey: EntityKey, val version: Long)
    data class SlicingCompleted(val sliceKeys: List<SliceKey>)
    data class ViewsComposed(val viewKeys: List<ViewKey>, val sliceKeys: List<SliceKey>)  // ✅
    data class ShipRequested(val sink: String, val viewKey: ViewKey)
}

// Outbox 생성
val event = DomainEvent.ViewsComposed(
    viewKeys = views.map { it.toKey() },
    sliceKeys = slices.map { it.toKey() }
)
val outboxEntry = OutboxEntry.fromEvent(event)
```

---

### 2.3 ❌ Result Pattern 전파 과다

**문제**:
```kotlin
when (val result = rawDataRepo.putIdempotent(rawData)) {
    is Result.Err -> return@transaction Result.Err(result.error)
    is Result.Ok -> Unit
}
when (val result = slicingEngine.slice(rawData, ruleSetRef)) {
    is Result.Ok -> result.value
    is Result.Err -> return@transaction Result.Err(result.error)
}
// ... 반복 반복 반복
```

**문제점**:
- **Boilerplate 코드 과다**: 모든 호출마다 `when (result)` 패턴
- **가독성 저하**: 실제 비즈니스 로직이 묻힘
- **Arrow Either를 버린 이유가 무색**

**SOTA 개선안 Option 1: Context Receiver (Kotlin 1.9+)**
```kotlin
context(ResultContext)
suspend fun execute(command: IngestCommand): IngestionResult {
    val rawData = RawDataRecord.create(command.tenantId, command.entityKey, command.data)
    rawDataRepo.putIdempotent(rawData).bind()  // ← bind()가 자동으로 에러 전파

    val slices = slicingEngine.slice(rawData, command.ruleSetRef).bind()
    sliceRepo.putAllIdempotent(slices).bind()

    val views = viewComposer.compose(slices, command.viewDefId).bind()
    viewRepo.putAllIdempotent(views).bind()

    return IngestionResult(...)
}
```

**SOTA 개선안 Option 2: Monad-free Approach**
```kotlin
suspend fun execute(command: IngestCommand): Result<IngestionResult> = runCatching {
    val rawData = RawDataRecord.create(command.tenantId, command.entityKey, command.data)
    rawDataRepo.putIdempotent(rawData).getOrThrow()

    val slices = slicingEngine.slice(rawData, command.ruleSetRef).getOrThrow()
    sliceRepo.putAllIdempotent(slices).getOrThrow()

    val views = viewComposer.compose(slices, command.viewDefId).getOrThrow()
    viewRepo.putAllIdempotent(views).getOrThrow()

    IngestionResult(...)
}.toResult()
```

---

### 2.4 ❌ TransactionManager 책임 과다

**문제**:
```kotlin
class TransactionManager(private val dslContext: DSLContext) {
    suspend fun <T> transaction(
        isolationLevel: Int = Connection.TRANSACTION_READ_COMMITTED,
        block: suspend () -> Result<T>
    ): Result<T>
}
```

**문제점**:
- **jOOQ DSLContext를 직접 의존**: Infrastructure 누수
- **Result 타입 강제**: 비즈니스 로직이 Result에 종속
- **테스트 불가능**: final class, mock 어려움

**SOTA 개선안**:
```kotlin
// Port (Domain)
interface TransactionPort {
    suspend fun <T> execute(block: suspend () -> T): T
}

// Adapter (Infrastructure)
class JooqTransactionAdapter(private val dslContext: DSLContext) : TransactionPort {
    override suspend fun <T> execute(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            dslContext.transactionResult { _ ->
                runBlocking { block() }
            }
        }
}

// 테스트용
class NoOpTransactionAdapter : TransactionPort {
    override suspend fun <T> execute(block: suspend () -> T): T = block()
}
```

---

### 2.5 ❌ 이벤트 Payload 직렬화 수동

**문제**:
```kotlin
private fun createSlicingCompletedOutbox(...): OutboxEntry {
    val sliceKeysJson = sliceKeys.joinToString(",", "[", "]") { key ->
        """{"tenantId":"${key.tenantId.value}","entityKey":"${key.entityKey.value}","version":${key.version},"sliceType":"${key.sliceType.name}"}"""
    }
    val payload = """{"payloadVersion":"1.0","tenantId":"${tenantId.value}",...}"""

    return OutboxEntry.create(...)
}
```

**문제점**:
- **수동 JSON 문자열 생성**: 에러 가능성 높음
- **타입 안전성 없음**: 컴파일 타임 검증 불가
- **유지보수 어려움**: Payload 스키마 변경 시 수동 수정

**SOTA 개선안**:
```kotlin
@Serializable
sealed class OutboxPayload {
    abstract val payloadVersion: String

    @Serializable
    data class ViewsComposedPayload(
        override val payloadVersion: String = "1.0",
        val tenantId: String,
        val entityKey: String,
        val version: Long,
        val viewKeys: List<ViewKeyData>,
        val sliceKeys: List<SliceKeyData>
    ) : OutboxPayload()
}

// 타입 안전 생성
private fun createViewsComposedOutbox(...): OutboxEntry {
    val payload = OutboxPayload.ViewsComposedPayload(
        tenantId = tenantId.value,
        entityKey = entityKey.value,
        version = version,
        viewKeys = views.map { it.toKeyData() },
        sliceKeys = slices.map { it.toKeyData() }
    )

    return OutboxEntry.create(
        aggregateType = AggregateType.VIEW,
        aggregateId = "$tenantId:$entityKey",
        eventType = "ViewsComposed",
        payload = Json.encodeToString(payload)
    )
}
```

---

### 2.6 ❌ 테스트 커버리지 부족

**현재**:
- ✅ E2E 테스트 (RawDataToSinkE2ETest)
- ❌ Integration 테스트 (실제 DB, 실제 트랜잭션)
- ❌ Performance 테스트 (1~2초 목표 검증)
- ❌ Concurrency 테스트 (동시 요청 처리)

**SOTA 개선안**:
```kotlin
// Integration Test
@Tag("IntegrationTag")
class RawDataIngestionIntegrationTest : DescribeSpec({
    lateinit var postgres: PostgreSQLContainer<*>
    lateinit var dsl: DSLContext

    beforeSpec {
        postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply { start() }
        dsl = DSL.using(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(postgres.jdbcUrl, ...).load().migrate()
    }

    describe("실제 DB 트랜잭션 테스트") {
        it("트랜잭션 롤백 시 모든 데이터가 저장되지 않음") {
            // Given: Slicing이 실패하도록 설정
            // When: ingest() 실행
            // Then: RawData, Slice, View, Outbox 모두 롤백 확인
        }
    }
})

// Performance Test
class RawDataIngestionPerformanceTest : DescribeSpec({
    describe("응답 시간 검증") {
        it("View 완성까지 2초 이내") {
            val start = Instant.now()
            val result = service.ingest(...)
            val duration = Duration.between(start, Instant.now())

            duration.toMillis() shouldBeLessThan 2000
        }
    }
})

// Concurrency Test
class RawDataIngestionConcurrencyTest : DescribeSpec({
    describe("동시 요청 처리") {
        it("100개 동시 요청 처리 성공") {
            val requests = (1..100).map { i ->
                async { service.ingest(tenantId, EntityKey("product:$i"), data) }
            }
            val results = requests.awaitAll()

            results.all { it is Result.Ok } shouldBe true
        }
    }
})
```

---

## 3. SOTA급 개선 방향

### 3.1 📐 Layer 명확화

```
┌─────────────────────────────────────────────────┐
│  API Layer (Ktor Routes)                        │
│  - HTTP 요청/응답                                │
│  - DTO ↔ Command 변환                           │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│  Application Layer                               │
│  - IngestionOrchestrator                        │
│    - 트랜잭션 관리                              │
│    - IngestionWorkflow 호출                     │
│    - OutboxEventPublisher 호출                  │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│  Domain Layer                                    │
│  - IngestionWorkflow                            │
│    - RawData → Slicing → View 비즈니스 로직    │
│  - ViewComposer                                 │
│  - SlicingEngine                                │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│  Infrastructure Layer                            │
│  - PostgresRawDataRepository                    │
│  - PostgresSliceRepository                      │
│  - JooqTransactionAdapter                       │
└─────────────────────────────────────────────────┘
```

### 3.2 🎯 Command/Query 분리

**Commands** (Write):
```kotlin
data class IngestRawDataCommand(
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val data: JsonObject,
    val schemaRef: ContractRef,
    val ruleSetRef: ContractRef,
    val viewDefIds: List<String> = listOf("view.product.pdp.v1")
)

interface IngestionCommandHandler {
    suspend fun handle(command: IngestRawDataCommand): Result<IngestionResult>
}
```

**Queries** (Read):
```kotlin
data class GetViewQuery(
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val viewDefId: String,
    val version: Long? = null  // null = latest
)

interface ViewQueryHandler {
    suspend fun handle(query: GetViewQuery): Result<ViewData>
}
```

### 3.3 🔄 이벤트 기반 아키텍처

```kotlin
// Domain Events
sealed interface DomainEvent {
    val eventId: String
    val occurredAt: Instant
    val aggregateType: AggregateType
    val aggregateId: String
}

data class ViewsComposedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateType: AggregateType = AggregateType.VIEW,
    override val aggregateId: String,
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val version: Long,
    val viewKeys: List<ViewKey>,
    val sliceKeys: List<SliceKey>
) : DomainEvent

// Event Publisher
interface EventPublisher {
    suspend fun publish(event: DomainEvent): Result<Unit>
}

class OutboxEventPublisher(
    private val outboxRepo: OutboxRepositoryPort
) : EventPublisher {
    override suspend fun publish(event: DomainEvent): Result<Unit> {
        val outboxEntry = OutboxEntry.fromEvent(event)
        return outboxRepo.insert(outboxEntry)
    }
}
```

### 3.4 📊 관찰성 (Observability)

```kotlin
class InstrumentedIngestionWorkflow(
    private val delegate: IngestionWorkflow,
    private val metrics: MetricsRegistry,
    private val tracer: Tracer
) : IngestionWorkflow {
    override suspend fun execute(command: IngestCommand): Result<IngestionResult> {
        val timer = metrics.timer("ingestion.duration").start()

        return tracer.withSpan("ingestion.workflow") { span ->
            span.setAttribute("tenant_id", command.tenantId.value)
            span.setAttribute("entity_key", command.entityKey.value)

            val result = delegate.execute(command)

            when (result) {
                is Result.Ok -> {
                    metrics.counter("ingestion.success").increment()
                    timer.stop()
                }
                is Result.Err -> {
                    metrics.counter("ingestion.failure").increment()
                    span.setStatus(StatusCode.ERROR, result.error.toString())
                    timer.stop()
                }
            }

            result
        }
    }
}
```

---

## 4. 우선순위별 개선 계획

### Phase 1 (즉시): 구조 리팩토링
1. ✅ **Layer 분리**: IngestionOrchestrator + IngestionWorkflow 분리
2. ✅ **TransactionPort 도입**: Infrastructure 의존성 제거
3. ✅ **이벤트 네이밍 변경**: SlicingCompleted → ViewsComposed

### Phase 2 (단기): 품질 개선
4. ✅ **Payload 타입 안전화**: @Serializable data class 사용
5. ✅ **Integration Test 추가**: 실제 DB 트랜잭션 검증
6. ✅ **Performance Test**: 응답 시간 2초 이내 검증

### Phase 3 (중기): 고도화
7. ⏳ **Result Pattern 개선**: Context Receiver 또는 Monad-free
8. ⏳ **Event Sourcing**: 이벤트 재생 기능
9. ⏳ **Observability**: Metrics, Tracing 통합

---

## 5. 성공 지표

| 지표 | 현재 | 목표 |
|------|------|------|
| Response Time (p95) | ~2s | <1s |
| Test Coverage | 60% | 85% |
| Layer Violation | 3건 | 0건 |
| Boilerplate LOC | 200+ | <100 |
| Transaction Rollback 정확도 | 미검증 | 100% |

---

## 6. 참고 자료

- **Clean Architecture** (Robert C. Martin)
- **Domain-Driven Design** (Eric Evans)
- **Hexagonal Architecture** (Alistair Cockburn)
- **Transactional Outbox Pattern** (Chris Richardson)
- **Arrow-kt Result Pattern** (https://arrow-kt.io)

---

## 7. 결론

현재 구현은 **기능적으로는 동작**하지만, **SOTA급 구조**를 위해 다음 개선이 필요합니다:

1. ✅ **Layer 명확화**: Application vs Domain 경계
2. ✅ **이벤트 네이밍**: ViewsComposed 사용
3. ✅ **TransactionPort**: Infrastructure 분리
4. ✅ **타입 안전 Payload**: @Serializable 활용
5. ✅ **Integration Test**: 실제 DB 검증

**다음 단계**: Phase 1 리팩토링 시작
