RFC-IMPL-011 — Fluent SDK DX (Contract-First Kotlin DSL)

Status: Draft
Created: 2026-01-25
Scope: Fluent SDK API + Deploy Orchestration DSL + Contract Codegen
Depends on: RFC-008, RFC-009, RFC-IMPL-008, RFC-IMPL-009, RFC-IMPL-010
Audience: Platform / SDK / Application Developers
Non-Goals: UI 콘솔, 외부 언어 SDK (Python/JS), GraphQL

---

## 0. Executive Summary

본 RFC는 RFC-008/009에서 정의된 **Fluent SDK DX**를 구현하는 SSOT를 정의한다.

**핵심 목표**:
- `Ivm.client().ingest().product { ... }.deployNow { opensearch() }` 형태의 Fluent DSL 제공
- Contract Registry 기반 **Codegen**으로 EntityDsl 자동 생성 (IDE = 문서)
- Type-safe **Axis Validation** (compile.async + ship.sync 차단)
- Deploy 상태 머신 + Outbox 연동

**RFC-008에서 정의된 Fluent SDK 패턴 전체 구현**:
```kotlin
// 1. 기본 입력
Ivm.client().ingest().product { sku("ABC"); name("Cream"); price(19000) }

// 2. Deploy 축 조합
  .deploy { compile.sync(); ship.async { opensearch(); personalize() } }

// 3. Shortcut API
  .deployNow { opensearch() }
  .deployNowAndShipNow { opensearch() }
  .deployQueued { opensearch() }

// 4. 컴파일러 타겟 (RFC-009)
  .deploy { compile { targets { searchDoc(); recoFeed() } }; ship.async { ... } }

// 5. 비동기 Job 상태 조회
Ivm.client().deploy.status(job.id)

// 6. Plan 설명 API
ivm.explainLastPlan(deployId)
```

---

## 1. 용어 정의

### 1-1. Fluent DSL
- Kotlin DSL Builder 패턴으로 메서드 체이닝을 통해 선언적 API 제공
- `@DslMarker`로 스코프 격리

### 1-2. Entity DSL
- `product { ... }`, `brand { ... }` 등 엔티티별 입력 DSL
- Contract Registry의 RuleSet 기반 Codegen 산출물

### 1-3. Deploy Axis
- **Compile**: slicing 수행 시점 (sync/async)
- **Ship**: sink 전파 시점 (sync/async)
- **Cutover**: active pointer swap 시점 (ready/done)

### 1-4. Sink DSL
- `opensearch()`, `personalize()` 등 외부 시스템 전파 DSL
- SinkRule 기반 Codegen 산출물

---

## 2. SDK 패키지 구조

```
src/main/kotlin/com/oliveyoung/ivmlite/
  sdk/                              # 새로 추가
    client/
      IvmClient.kt                  # Ivm.client() 진입점
      IvmClientConfig.kt            # 클라이언트 설정
      
    dsl/
      markers/
        IvmDslMarker.kt             # @DslMarker 정의
        
      ingest/
        IngestDsl.kt                # .ingest() DSL
        IngestContext.kt            # 인제스트 컨텍스트
        
      entity/
        EntityDsl.kt                # 엔티티 공통 인터페이스
        ProductDsl.kt               # .product { ... } (수동/코드젠)
        BrandDsl.kt                 # .brand { ... }
        CategoryDsl.kt              # .category { ... }
        
      deploy/
        DeployDsl.kt                # .deploy { ... }
        DeployAsyncDsl.kt           # .deployAsync { ... }
        CompileDsl.kt               # compile.sync() / compile.async()
        ShipDsl.kt                  # ship.sync {} / ship.async {}
        CutoverDsl.kt               # cutover.ready() / cutover.done()
        TargetsDsl.kt               # targets { searchDoc(); recoFeed() }
        
      sink/
        SinkDsl.kt                  # 싱크 공통 인터페이스
        OpenSearchSinkDsl.kt        # opensearch() 
        PersonalizeSinkDsl.kt       # personalize()
        
      shortcuts/
        DeployShortcuts.kt          # deployNow, deployNowAndShipNow, deployQueued
        
    model/
      DeployJob.kt                  # 비동기 배포 Job
      DeployResult.kt               # 배포 결과
      DeployPlan.kt                 # Plan 설명 결과
      DeployState.kt                # 상태 머신 상태
      
    validation/
      AxisValidator.kt              # 축 조합 검증
      
    execution/
      DeployExecutor.kt             # 실제 실행 로직
      StateMachine.kt               # 상태 전이 로직
      
    codegen/                        # (Phase 7)
      EntityDslGenerator.kt         # RuleSet → EntityDsl 코드 생성
      SinkDslGenerator.kt           # SinkRule → SinkDsl 코드 생성
```

---

## 3. Core DSL 설계

### 3-1. DslMarker 정의

```kotlin
// sdk/dsl/markers/IvmDslMarker.kt
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class IvmDslMarker
```

### 3-2. IvmClient 진입점

```kotlin
// sdk/client/IvmClient.kt
object Ivm {
    fun client(config: IvmClientConfig = IvmClientConfig.default()): IvmClient {
        return IvmClient(config)
    }
}

class IvmClient internal constructor(
    private val config: IvmClientConfig
) {
    fun ingest(): IngestContext = IngestContext(config)
    
    val deploy: DeployStatusApi = DeployStatusApi(config)
    
    fun explainLastPlan(deployId: String): DeployPlan = ...
}
```

### 3-3. IngestContext

```kotlin
// sdk/dsl/ingest/IngestContext.kt
@IvmDslMarker
class IngestContext internal constructor(
    private val config: IvmClientConfig
) {
    fun product(block: ProductBuilder.() -> Unit): DeployableContext<ProductInput> {
        val builder = ProductBuilder()
        builder.block()
        return DeployableContext(
            config = config,
            entityType = EntityType.PRODUCT,
            input = builder.build()
        )
    }
    
    fun brand(block: BrandBuilder.() -> Unit): DeployableContext<BrandInput> { ... }
    fun category(block: CategoryBuilder.() -> Unit): DeployableContext<CategoryInput> { ... }
}
```

### 3-4. Entity Builder (ProductDsl 예시)

```kotlin
// sdk/dsl/entity/ProductDsl.kt
@IvmDslMarker
class ProductBuilder internal constructor() {
    private var _tenantId: String? = null
    private var _sku: String? = null
    private var _name: String? = null
    private var _price: Long? = null
    private var _currency: String = "KRW"
    private var _brandCode: String? = null
    private var _categoryCode: String? = null
    private val _tags: MutableList<String> = mutableListOf()
    
    fun tenantId(value: String) { _tenantId = value }
    fun sku(value: String) { _sku = value }
    fun name(value: String) { _name = value }
    fun price(value: Long) { _price = value }
    fun currency(value: String) { _currency = value }
    fun brandCode(value: String) { _brandCode = value }
    fun categoryCode(value: String) { _categoryCode = value }
    fun tags(vararg values: String) { _tags.addAll(values) }
    
    internal fun build(): ProductInput = ProductInput(
        tenantId = requireNotNull(_tenantId) { "tenantId is required" },
        sku = requireNotNull(_sku) { "sku is required" },
        name = requireNotNull(_name) { "name is required" },
        price = requireNotNull(_price) { "price is required" },
        currency = _currency,
        brandCode = _brandCode,
        categoryCode = _categoryCode,
        tags = _tags.toList()
    )
}

data class ProductInput(
    val tenantId: String,
    val sku: String,
    val name: String,
    val price: Long,
    val currency: String,
    val brandCode: String?,
    val categoryCode: String?,
    val tags: List<String>
)
```

---

## 4. Deploy DSL 설계 (핵심)

### 4-1. DeployableContext

```kotlin
// sdk/dsl/deploy/DeployableContext.kt
@IvmDslMarker
class DeployableContext<T : Any> internal constructor(
    private val config: IvmClientConfig,
    private val entityType: EntityType,
    private val input: T
) {
    // === 명시적 Deploy ===
    
    fun deploy(block: DeployBuilder.() -> Unit): DeployResult {
        val builder = DeployBuilder()
        builder.block()
        return executeSync(builder.build())
    }
    
    fun deployAsync(block: DeployAsyncBuilder.() -> Unit): DeployJob {
        val builder = DeployAsyncBuilder()
        builder.block()
        return executeAsync(builder.build())
    }
    
    // === Shortcut APIs (RFC-008: 11) ===
    
    /**
     * compile sync + ship async (기본값)
     */
    fun deployNow(block: SinkBuilder.() -> Unit): DeployResult {
        return deploy {
            compile.sync()
            ship.async(block)
        }
    }
    
    /**
     * compile sync + ship sync (즉시 반영)
     */
    fun deployNowAndShipNow(block: SinkBuilder.() -> Unit): DeployResult {
        return deploy {
            compile.sync()
            ship.sync(block)
        }
    }
    
    /**
     * compile async + ship async (대형 배포)
     */
    fun deployQueued(block: SinkBuilder.() -> Unit): DeployJob {
        return deployAsync {
            compile.async()
            ship.async(block)
        }
    }
    
    private fun executeSync(spec: DeploySpec): DeployResult { ... }
    private fun executeAsync(spec: DeploySpec): DeployJob { ... }
}
```

### 4-2. DeployBuilder (Sync)

```kotlin
// sdk/dsl/deploy/DeployBuilder.kt
@IvmDslMarker
class DeployBuilder internal constructor() {
    private var compileMode: CompileMode = CompileMode.Sync
    private var shipSpec: ShipSpec? = null
    private var cutoverMode: CutoverMode = CutoverMode.Ready
    
    val compile: CompileAccessor = CompileAccessor { mode -> compileMode = mode }
    val ship: ShipAccessor = ShipAccessor { spec -> shipSpec = spec }
    val cutover: CutoverAccessor = CutoverAccessor { mode -> cutoverMode = mode }
    
    internal fun build(): DeploySpec {
        // Axis Validation (RFC-008: 3)
        if (compileMode == CompileMode.Async && shipSpec?.mode == ShipMode.Sync) {
            throw IllegalStateException(
                "Invalid axis combination: compile.async + ship.sync is not allowed. " +
                "Use ship.async instead."
            )
        }
        
        return DeploySpec(
            compileMode = compileMode,
            shipSpec = shipSpec,
            cutoverMode = cutoverMode
        )
    }
}

// Compile Accessor
class CompileAccessor(private val setter: (CompileMode) -> Unit) {
    fun sync() { setter(CompileMode.Sync) }
    
    // targets 지원 (RFC-009)
    fun sync(block: CompileTargetsBuilder.() -> Unit) {
        val builder = CompileTargetsBuilder()
        builder.block()
        setter(CompileMode.SyncWithTargets(builder.build()))
    }
}

// Ship Accessor
class ShipAccessor(private val setter: (ShipSpec) -> Unit) {
    fun sync(block: SinkBuilder.() -> Unit) {
        val builder = SinkBuilder()
        builder.block()
        setter(ShipSpec(ShipMode.Sync, builder.build()))
    }
    
    fun async(block: SinkBuilder.() -> Unit) {
        val builder = SinkBuilder()
        builder.block()
        setter(ShipSpec(ShipMode.Async, builder.build()))
    }
}

// Cutover Accessor
class CutoverAccessor(private val setter: (CutoverMode) -> Unit) {
    fun ready() { setter(CutoverMode.Ready) }
    fun done() { setter(CutoverMode.Done) }
}
```

### 4-3. DeployAsyncBuilder

```kotlin
// sdk/dsl/deploy/DeployAsyncBuilder.kt
@IvmDslMarker
class DeployAsyncBuilder internal constructor() {
    private var compileMode: CompileMode = CompileMode.Async
    private var shipSpec: ShipSpec? = null
    private var cutoverMode: CutoverMode = CutoverMode.Ready
    
    val compile: AsyncCompileAccessor = AsyncCompileAccessor { mode -> compileMode = mode }
    val ship: AsyncShipAccessor = AsyncShipAccessor { spec -> shipSpec = spec }
    val cutover: CutoverAccessor = CutoverAccessor { mode -> cutoverMode = mode }
    
    internal fun build(): DeploySpec {
        return DeploySpec(
            compileMode = compileMode,
            shipSpec = shipSpec,
            cutoverMode = cutoverMode
        )
    }
}

// Async에서는 ship.sync 제공 안 함 (타입 레벨 차단)
class AsyncShipAccessor(private val setter: (ShipSpec) -> Unit) {
    fun async(block: SinkBuilder.() -> Unit) {
        val builder = SinkBuilder()
        builder.block()
        setter(ShipSpec(ShipMode.Async, builder.build()))
    }
    // ship.sync 메서드 없음 → 컴파일 에러로 차단
}
```

### 4-4. Compile Targets (RFC-009)

```kotlin
// sdk/dsl/deploy/TargetsDsl.kt
@IvmDslMarker
class CompileTargetsBuilder internal constructor() {
    private val _targets: MutableList<TargetRef> = mutableListOf()
    
    fun targets(block: TargetsBuilder.() -> Unit) {
        val builder = TargetsBuilder()
        builder.block()
        _targets.addAll(builder.build())
    }
    
    internal fun build(): List<TargetRef> = _targets.toList()
}

@IvmDslMarker
class TargetsBuilder internal constructor() {
    private val _targets: MutableList<TargetRef> = mutableListOf()
    
    fun searchDoc() { _targets.add(TargetRef("search-doc", SemVer(1, 0, 0))) }
    fun recoFeed() { _targets.add(TargetRef("reco-feed", SemVer(1, 0, 0))) }
    
    internal fun build(): List<TargetRef> = _targets.toList()
}
```

---

## 5. Sink DSL 설계

### 5-1. SinkBuilder

```kotlin
// sdk/dsl/sink/SinkBuilder.kt
@IvmDslMarker
class SinkBuilder internal constructor() {
    private val _sinks: MutableList<SinkSpec> = mutableListOf()
    
    fun opensearch(block: OpenSearchBuilder.() -> Unit = {}) {
        val builder = OpenSearchBuilder()
        builder.block()
        _sinks.add(builder.build())
    }
    
    fun personalize(block: PersonalizeBuilder.() -> Unit = {}) {
        val builder = PersonalizeBuilder()
        builder.block()
        _sinks.add(builder.build())
    }
    
    internal fun build(): List<SinkSpec> = _sinks.toList()
}
```

### 5-2. OpenSearch Sink

```kotlin
// sdk/dsl/sink/OpenSearchSinkDsl.kt
@IvmDslMarker
class OpenSearchBuilder internal constructor() {
    private var _index: String? = null
    private var _alias: String? = null
    private var _batchSize: Int = 1000
    
    fun index(value: String) { _index = value }
    fun alias(value: String) { _alias = value }
    fun batchSize(value: Int) { _batchSize = value }
    
    internal fun build(): SinkSpec = OpenSearchSinkSpec(
        index = _index,
        alias = _alias,
        batchSize = _batchSize
    )
}
```

### 5-3. Personalize Sink

```kotlin
// sdk/dsl/sink/PersonalizeSinkDsl.kt
@IvmDslMarker
class PersonalizeBuilder internal constructor() {
    private var _datasetArn: String? = null
    private var _roleArn: String? = null
    
    fun datasetArn(value: String) { _datasetArn = value }
    fun roleArn(value: String) { _roleArn = value }
    
    internal fun build(): SinkSpec = PersonalizeSinkSpec(
        datasetArn = _datasetArn,
        roleArn = _roleArn
    )
}
```

---

## 6. 상태 머신 (RFC-008: 4)

### 6-1. DeployState

```kotlin
// sdk/model/DeployState.kt
enum class DeployState {
    QUEUED,     // outbox에 기록됨
    RUNNING,    // compile 수행 중
    READY,      // slicing 완료, swap 가능
    SINKING,    // ship 수행 중
    DONE,       // 완료
    FAILED      // 실패
}
```

### 6-2. StateMachine

```kotlin
// sdk/execution/StateMachine.kt
object DeployStateMachine {
    
    fun transition(
        current: DeployState,
        event: DeployEvent
    ): Either<StateError, DeployState> = when (current) {
        DeployState.QUEUED -> when (event) {
            is DeployEvent.StartRunning -> DeployState.RUNNING.right()
            else -> StateError.InvalidTransition(current, event).left()
        }
        DeployState.RUNNING -> when (event) {
            is DeployEvent.CompileComplete -> DeployState.READY.right()
            is DeployEvent.Failed -> DeployState.FAILED.right()
            else -> StateError.InvalidTransition(current, event).left()
        }
        DeployState.READY -> when (event) {
            is DeployEvent.StartSinking -> DeployState.SINKING.right()
            is DeployEvent.Complete -> DeployState.DONE.right()  // ship 없을 때
            else -> StateError.InvalidTransition(current, event).left()
        }
        DeployState.SINKING -> when (event) {
            is DeployEvent.Complete -> DeployState.DONE.right()
            is DeployEvent.Failed -> DeployState.FAILED.right()
            else -> StateError.InvalidTransition(current, event).left()
        }
        DeployState.DONE, DeployState.FAILED -> 
            StateError.AlreadyTerminal(current).left()
    }
}

sealed interface DeployEvent {
    data class StartRunning(val workerId: String) : DeployEvent
    object CompileComplete : DeployEvent
    object StartSinking : DeployEvent
    object Complete : DeployEvent
    data class Failed(val error: DomainError) : DeployEvent
}
```

---

## 7. Deploy 상태 조회 API

### 7-1. DeployStatusApi

```kotlin
// sdk/client/DeployStatusApi.kt
class DeployStatusApi internal constructor(
    private val config: IvmClientConfig
) {
    suspend fun status(jobId: String): DeployJobStatus {
        // Outbox/Task 테이블에서 상태 조회
        ...
    }
    
    suspend fun await(jobId: String, timeout: Duration = 5.minutes): DeployResult {
        // Polling으로 완료 대기
        ...
    }
}

data class DeployJobStatus(
    val jobId: String,
    val state: DeployState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val error: String?
)
```

### 7-2. Plan 설명 API (RFC-009)

```kotlin
// sdk/model/DeployPlan.kt
data class DeployPlan(
    val deployId: String,
    val graph: DependencyGraph,
    val activatedRules: List<String>,
    val executionSteps: List<ExecutionStep>
)

data class ExecutionStep(
    val stepNumber: Int,
    val sliceRef: String,
    val dependencies: List<String>
)

// IvmClient 확장
fun IvmClient.explainLastPlan(deployId: String): DeployPlan {
    // Registry에서 Plan 조회
    ...
}
```

---

## 8. Workflow 연동

### 8-1. DeployExecutor

```kotlin
// sdk/execution/DeployExecutor.kt
class DeployExecutor(
    private val ingestWorkflow: IngestWorkflow,
    private val slicingWorkflow: SlicingWorkflow,
    private val outboxRepository: OutboxRepositoryPort,
    private val sinkOrchestrator: SinkOrchestrator  // RFC-007
) {
    suspend fun <T : Any> executeSync(
        entityType: EntityType,
        input: T,
        spec: DeploySpec
    ): DeployResult {
        // 1. RawData Ingest
        val rawResult = ingestWorkflow.execute(toIngestRequest(entityType, input))
        
        // 2. Compile (Slicing)
        val sliceResult = when (spec.compileMode) {
            is CompileMode.Sync -> slicingWorkflow.executeFull(rawResult.entityKey, rawResult.version)
            is CompileMode.SyncWithTargets -> slicingWorkflow.executeWithTargets(
                rawResult.entityKey, 
                rawResult.version,
                spec.compileMode.targets
            )
            else -> error("Async compile in sync executor")
        }
        
        // 3. Cutover (active version update)
        if (spec.cutoverMode == CutoverMode.Ready) {
            updateActiveVersion(rawResult.entityKey, rawResult.version)
        }
        
        // 4. Ship
        when (spec.shipSpec?.mode) {
            ShipMode.Sync -> {
                sinkOrchestrator.execute(sliceResult, spec.shipSpec.sinks)
                if (spec.cutoverMode == CutoverMode.Done) {
                    updateActiveVersion(rawResult.entityKey, rawResult.version)
                }
            }
            ShipMode.Async -> {
                // Outbox에 SHIP_TASK 적재
                outboxRepository.insert(OutboxEntry(
                    aggregateType = AggregateType.SHIP_TASK,
                    aggregateId = rawResult.entityKey,
                    eventType = "ShipRequested",
                    payload = buildShipPayload(sliceResult, spec.shipSpec.sinks)
                ))
            }
            null -> { /* no ship */ }
        }
        
        return DeployResult.success(rawResult.entityKey, rawResult.version)
    }
    
    suspend fun <T : Any> executeAsync(
        entityType: EntityType,
        input: T,
        spec: DeploySpec
    ): DeployJob {
        // 1. RawData Ingest만 동기 수행
        val rawResult = ingestWorkflow.execute(toIngestRequest(entityType, input))
        
        // 2. Outbox에 COMPILE_TASK 적재
        val jobId = UUID.randomUUID().toString()
        outboxRepository.insert(OutboxEntry(
            aggregateType = AggregateType.COMPILE_TASK,
            aggregateId = rawResult.entityKey,
            eventType = "CompileRequested",
            payload = buildCompilePayload(rawResult, spec)
        ))
        
        return DeployJob(
            jobId = jobId,
            entityKey = rawResult.entityKey,
            version = rawResult.version,
            state = DeployState.QUEUED
        )
    }
}
```

---

## 9. Acceptance Criteria

### Phase 1: Core SDK (P0)
- [ ] `Ivm.client()` 진입점 구현
- [ ] `IngestContext` + `.ingest()` 구현
- [ ] `ProductBuilder` + `.product { ... }` 구현
- [ ] `@IvmDslMarker` 스코프 격리 테스트

### Phase 2: Deploy DSL (P0)
- [ ] `DeployBuilder` + `.deploy { ... }` 구현
- [ ] `compile.sync()` / `ship.sync {}` / `ship.async {}` 구현
- [ ] `cutover.ready()` / `cutover.done()` 구현
- [ ] Axis Validation (compile.async + ship.sync 차단) 테스트

### Phase 3: Sink DSL (P0)
- [ ] `SinkBuilder` + `opensearch()` / `personalize()` 구현
- [ ] Sink 설정 옵션 지원

### Phase 4: Shortcut APIs (P1)
- [ ] `deployNow { ... }` 구현
- [ ] `deployNowAndShipNow { ... }` 구현
- [ ] `deployQueued { ... }` 구현

### Phase 5: Async & Status (P1)
- [ ] `DeployJob` 모델 구현
- [ ] `deploy.status(jobId)` 구현
- [ ] `deploy.await(jobId)` 구현
- [ ] StateMachine 상태 전이 테스트

### Phase 6: Compiler Targets (P1)
- [ ] `compile { targets { ... } }` DSL 구현
- [ ] `searchDoc()` / `recoFeed()` 타겟 구현
- [ ] `explainLastPlan()` 구현

### Phase 7: Contract Codegen (P2)
- [ ] `EntityDslGenerator` 구현
- [ ] `SinkDslGenerator` 구현
- [ ] Gradle 플러그인 통합

---

## 10. 테스트 전략

### 10-1. Unit Tests

```kotlin
// DSL Builder 테스트
class ProductBuilderTest : StringSpec({
    "should require mandatory fields" {
        shouldThrow<IllegalArgumentException> {
            ProductBuilder().build()  // sku 누락
        }
    }
    
    "should build valid input" {
        val input = ProductBuilder().apply {
            tenantId("t1")
            sku("ABC-123")
            name("Cream")
            price(19000)
        }.build()
        
        input.sku shouldBe "ABC-123"
    }
})

// Axis Validation 테스트
class AxisValidationTest : StringSpec({
    "should reject compile.async + ship.sync" {
        shouldThrow<IllegalStateException> {
            DeployBuilder().apply {
                compile.async()
                ship.sync { opensearch() }
            }.build()
        }
    }
    
    "should allow compile.async + ship.async" {
        shouldNotThrow<Exception> {
            DeployAsyncBuilder().apply {
                compile.async()
                ship.async { opensearch() }
            }.build()
        }
    }
})
```

### 10-2. Integration Tests

```kotlin
class FluentSdkIntegrationTest : StringSpec({
    "deployNow should execute sync compile + async ship" {
        val result = Ivm.client()
            .ingest()
            .product {
                tenantId("tenant1")
                sku("SKU-001")
                name("Test Product")
                price(10000)
            }
            .deployNow {
                opensearch()
            }
        
        result.isSuccess shouldBe true
        result.entityKey shouldContain "SKU-001"
    }
    
    "deployQueued should return job for async execution" {
        val job = Ivm.client()
            .ingest()
            .product { ... }
            .deployQueued {
                opensearch()
                personalize()
            }
        
        job.state shouldBe DeployState.QUEUED
        
        // Wait for completion
        val status = Ivm.client().deploy.await(job.jobId)
        status.state shouldBe DeployState.DONE
    }
})
```

### 10-3. Golden Tests (RFC 예시 검증)

```kotlin
class RfcExamplesGoldenTest : StringSpec({
    "RFC-008 Example 9-1: Raw Input DSL" {
        val result = Ivm.client()
            .ingest()
            .product {
                sku("ABC-123")
                name("Moisture Cream")
                price(19000)
                currency("KRW")
            }
            .deployNow { opensearch() }
        
        result.isSuccess shouldBe true
    }
    
    "RFC-008 Example 10-3: Async Deploy" {
        val job = Ivm.client()
            .ingest()
            .product { ... }
            .deployAsync {
                compile.async()
                ship.async {
                    opensearch()
                    personalize()
                }
            }
        
        Ivm.client().deploy.status(job.jobId)
    }
    
    "RFC-009 Example 11-1: Compile with Targets" {
        Ivm.client()
            .ingest()
            .product { ... }
            .deploy {
                compile {
                    targets {
                        searchDoc()
                        recoFeed()
                    }
                }
                ship.async {
                    opensearch()
                    personalize()
                }
            }
    }
})
```

---

## 11. Non-Negotiable Invariants

### 11-1. Type Safety (P0)
- 불가능한 축 조합은 **컴파일 타임**에 차단
- `compile.async + ship.sync`는 `DeployAsyncBuilder`에서 `ship.sync` 메서드 미제공으로 차단

### 11-2. DslMarker Isolation (P0)
- `@IvmDslMarker`로 스코프 격리
- 외부 컨텍스트의 메서드가 DSL 블록 내에서 접근 불가

### 11-3. Contract-First (P1)
- EntityDsl은 Contract Registry의 RuleSet 기반 코드젠
- 수동 작성 DSL과 코드젠 DSL은 동일 인터페이스

### 11-4. Determinism (P0)
- 동일 입력 → 동일 결과 (RFC-002 준수)
- DSL 빌더는 순수 함수형 (side-effect 없음)

---

## 12. Migration Guide

### 12-1. 기존 API에서 Fluent SDK로

```kotlin
// Before (기존 REST API 호출)
val response = httpClient.post("/api/v1/ingest") {
    body = IngestRequest(tenantId = "t1", entityType = "PRODUCT", ...)
}
httpClient.post("/api/v1/slice") { ... }

// After (Fluent SDK)
Ivm.client()
    .ingest()
    .product {
        tenantId("t1")
        sku("SKU-001")
        ...
    }
    .deployNow { opensearch() }
```

---

## 13. Final Judgment

| 항목 | 결정 |
|------|------|
| **Status** | Draft → Accepted 후 구현 |
| **역할** | RFC-008/009 Fluent SDK DX 구현 SSOT |

**한 줄 요약**: `Ivm.client().ingest().product { ... }.deployNow { opensearch() }` — IDE가 문서가 되는 Contract-First Fluent DSL.

---

## 14. Next Steps

1. **Phase 1-3**: Core SDK + Deploy DSL + Sink DSL (P0, 필수)
2. **Phase 4-5**: Shortcut APIs + Async/Status (P1)
3. **Phase 6**: Compiler Targets (P1)
4. **Phase 7**: Contract Codegen (P2)
