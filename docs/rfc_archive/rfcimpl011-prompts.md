# RFC-IMPL-011 — 병렬 작업 프롬프트

의존성 기준으로 Wave 단위로 나눔. **같은 Wave 내 작업은 동시 진행 가능!**

```
Wave 1 ─┬─ [A] Markers & Models ────────────────────┐
        └─ [B] State Models ────────────────────────┤
                                                    ▼
Wave 2 ─┬─ [C] Client Core ─────────────────────────┤
        ├─ [D] Entity DSL ──────────────────────────┤
        ├─ [E] Sink DSL ────────────────────────────┤
        ├─ [F] Deploy Accessors ────────────────────┤
        └─ [G] State Machine ───────────────────────┤
                                                    ▼
Wave 3 ─┬─ [H] Deploy Builders ─────────────────────┤
        └─ [I] Compiler Targets ────────────────────┤
                                                    ▼
Wave 4 ─── [J] DeployableContext (통합) ────────────┤
                                                    ▼
Wave 5 ─┬─ [K] Status API ──────────────────────────┤
        └─ [L] Executor & Tests ────────────────────┘
```

---

# 🌊 Wave 1: Foundation (동시 작업 2개)

## [A] Markers & Models

```
RFC-IMPL-011 Wave 1-A 구현해줘. (Markers & Models)

목표: @DslMarker + 모든 모델 정의

구현할 파일:
1. sdk/dsl/markers/IvmDslMarker.kt
   @DslMarker
   @Target(AnnotationTarget.CLASS)
   annotation class IvmDslMarker

2. sdk/model/CompileMode.kt
   sealed interface CompileMode {
       object Sync : CompileMode
       object Async : CompileMode
       data class SyncWithTargets(val targets: List<TargetRef>) : CompileMode
   }

3. sdk/model/ShipMode.kt
   enum class ShipMode { Sync, Async }

4. sdk/model/CutoverMode.kt
   enum class CutoverMode { Ready, Done }

5. sdk/model/TargetRef.kt
   data class TargetRef(val id: String, val version: String = "v1")

6. sdk/model/SinkSpec.kt
   sealed interface SinkSpec

7. sdk/model/OpenSearchSinkSpec.kt
   data class OpenSearchSinkSpec(
       val index: String? = null,
       val alias: String? = null,
       val batchSize: Int = 1000
   ) : SinkSpec

8. sdk/model/PersonalizeSinkSpec.kt
   data class PersonalizeSinkSpec(
       val datasetArn: String? = null,
       val roleArn: String? = null
   ) : SinkSpec

9. sdk/model/ShipSpec.kt
   data class ShipSpec(
       val mode: ShipMode,
       val sinks: List<SinkSpec>
   )

10. sdk/model/DeploySpec.kt
    data class DeploySpec(
        val compileMode: CompileMode = CompileMode.Sync,
        val shipSpec: ShipSpec? = null,
        val cutoverMode: CutoverMode = CutoverMode.Ready
    )

11. sdk/model/DeployResult.kt
    data class DeployResult(
        val success: Boolean,
        val entityKey: String,
        val version: String,
        val error: String? = null
    ) {
        companion object {
            fun success(entityKey: String, version: String) = DeployResult(true, entityKey, version)
            fun failure(entityKey: String, version: String, error: String) = DeployResult(false, entityKey, version, error)
        }
    }

12. sdk/model/DeployJob.kt
    data class DeployJob(
        val jobId: String,
        val entityKey: String,
        val version: String,
        val state: DeployState
    )

의존성: 없음 (다른 Wave와 동시 작업 가능)
```

---

## [B] State Models

```
RFC-IMPL-011 Wave 1-B 구현해줘. (State Models)

목표: Deploy 상태 머신 관련 모델

구현할 파일:
1. sdk/model/DeployState.kt
   enum class DeployState {
       QUEUED,    // 대기열에 추가됨
       RUNNING,   // 컴파일 실행 중
       READY,     // 컴파일 완료, Ship 대기
       SINKING,   // Ship 진행 중
       DONE,      // 완료
       FAILED     // 실패
   }

2. sdk/model/DeployEvent.kt
   sealed interface DeployEvent {
       data class StartRunning(val workerId: String) : DeployEvent
       object CompileComplete : DeployEvent
       object StartSinking : DeployEvent
       object Complete : DeployEvent
       data class Failed(val error: String) : DeployEvent
   }

3. sdk/model/StateError.kt
   sealed interface StateError {
       data class InvalidTransition(
           val current: DeployState,
           val event: DeployEvent
       ) : StateError
   }

4. sdk/model/DeployJobStatus.kt
   data class DeployJobStatus(
       val jobId: String,
       val state: DeployState,
       val createdAt: java.time.Instant,
       val updatedAt: java.time.Instant,
       val error: String? = null
   )

5. sdk/model/DeployPlan.kt
   data class DeployPlan(
       val deployId: String,
       val graph: DependencyGraph,
       val activatedRules: List<String>,
       val executionSteps: List<ExecutionStep>
   )

6. sdk/model/DependencyGraph.kt
   data class DependencyGraph(
       val nodes: Map<String, GraphNode>
   )
   
   data class GraphNode(
       val id: String,
       val dependencies: List<String>,
       val provides: List<String>
   )

7. sdk/model/ExecutionStep.kt
   data class ExecutionStep(
       val stepNumber: Int,
       val sliceRef: String,
       val dependencies: List<String>
   )

의존성: 없음 (Wave 1-A와 동시 작업 가능)
```

---

# 🌊 Wave 2: Builders (동시 작업 5개)

> **전제조건**: Wave 1 완료

## [C] Client Core

```
RFC-IMPL-011 Wave 2-C 구현해줘. (Client Core)

목표: Ivm.client().ingest() 체이닝

구현할 파일:
1. sdk/client/IvmClientConfig.kt
   data class IvmClientConfig(
       val baseUrl: String = "http://localhost:8080",
       val tenantId: String? = null,
       val timeout: java.time.Duration = java.time.Duration.ofSeconds(30)
   )

2. sdk/client/IvmClient.kt
   object Ivm {
       private var config: IvmClientConfig = IvmClientConfig()
       
       fun configure(block: IvmClientConfig.Builder.() -> Unit) { ... }
       fun client(): IvmClient = IvmClient(config)
   }
   
   class IvmClient internal constructor(private val config: IvmClientConfig) {
       fun ingest(): IngestContext = IngestContext(config)
   }

3. sdk/dsl/ingest/IngestContext.kt
   @IvmDslMarker
   class IngestContext internal constructor(
       private val config: IvmClientConfig
   ) {
       // Wave 2-D에서 확장 함수로 product { } 추가 예정
   }

테스트:
4. test/.../sdk/IvmClientTest.kt
   - Ivm.client() 호출 가능
   - .ingest() 체이닝 가능

의존성: Wave 1 (IvmDslMarker)
```

---

## [D] Entity DSL

```
RFC-IMPL-011 Wave 2-D 구현해줘. (Entity DSL)

목표: .product { sku("X"); name("Y"); price(100) } 빌더

구현할 파일:
1. sdk/dsl/entity/EntityInput.kt
   sealed interface EntityInput {
       val tenantId: String
       val entityType: String
   }

2. sdk/dsl/entity/ProductInput.kt
   data class ProductInput(
       override val tenantId: String,
       val sku: String,
       val name: String,
       val price: Long,
       val currency: String = "KRW",
       val category: String? = null,
       val brand: String? = null,
       val attributes: Map<String, Any> = emptyMap()
   ) : EntityInput {
       override val entityType: String = "product"
   }

3. sdk/dsl/entity/ProductDsl.kt
   @IvmDslMarker
   class ProductBuilder internal constructor() {
       private var tenantId: String? = null
       private var sku: String? = null
       private var name: String? = null
       private var price: Long? = null
       private var currency: String = "KRW"
       private var category: String? = null
       private var brand: String? = null
       private val attributes = mutableMapOf<String, Any>()
       
       fun tenantId(value: String) { tenantId = value }
       fun sku(value: String) { sku = value }
       fun name(value: String) { name = value }
       fun price(value: Long) { price = value }
       fun currency(value: String) { currency = value }
       fun category(value: String) { category = value }
       fun brand(value: String) { brand = value }
       fun attribute(key: String, value: Any) { attributes[key] = value }
       
       internal fun build(): ProductInput {
           return ProductInput(
               tenantId = requireNotNull(tenantId) { "tenantId is required" },
               sku = requireNotNull(sku) { "sku is required" },
               name = requireNotNull(name) { "name is required" },
               price = requireNotNull(price) { "price is required" },
               currency = currency,
               category = category,
               brand = brand,
               attributes = attributes.toMap()
           )
       }
   }
   
   // IngestContext 확장 함수
   fun IngestContext.product(block: ProductBuilder.() -> Unit): DeployableContext {
       val input = ProductBuilder().apply(block).build()
       return DeployableContext(input, config)
   }

테스트:
4. test/.../sdk/ProductBuilderTest.kt
   - 필수 필드 누락 시 IllegalArgumentException
   - 정상 빌드 테스트

의존성: Wave 1 (IvmDslMarker), Wave 2-C (IngestContext)
```

---

## [E] Sink DSL

```
RFC-IMPL-011 Wave 2-E 구현해줘. (Sink DSL)

목표: opensearch { index("x") }, personalize { } 빌더

구현할 파일:
1. sdk/dsl/sink/SinkBuilder.kt
   @IvmDslMarker
   class SinkBuilder internal constructor() {
       private val sinks = mutableListOf<SinkSpec>()
       
       fun opensearch(block: OpenSearchBuilder.() -> Unit = {}) {
           sinks.add(OpenSearchBuilder().apply(block).build())
       }
       
       fun personalize(block: PersonalizeBuilder.() -> Unit = {}) {
           sinks.add(PersonalizeBuilder().apply(block).build())
       }
       
       internal fun build(): List<SinkSpec> = sinks.toList()
   }

2. sdk/dsl/sink/OpenSearchBuilder.kt
   @IvmDslMarker
   class OpenSearchBuilder internal constructor() {
       private var index: String? = null
       private var alias: String? = null
       private var batchSize: Int = 1000
       
       fun index(value: String) { index = value }
       fun alias(value: String) { alias = value }
       fun batchSize(value: Int) { batchSize = value }
       
       internal fun build(): OpenSearchSinkSpec = OpenSearchSinkSpec(index, alias, batchSize)
   }

3. sdk/dsl/sink/PersonalizeBuilder.kt
   @IvmDslMarker
   class PersonalizeBuilder internal constructor() {
       private var datasetArn: String? = null
       private var roleArn: String? = null
       
       fun datasetArn(value: String) { datasetArn = value }
       fun roleArn(value: String) { roleArn = value }
       
       internal fun build(): PersonalizeSinkSpec = PersonalizeSinkSpec(datasetArn, roleArn)
   }

테스트:
4. test/.../sdk/SinkBuilderTest.kt
   - opensearch() 기본 호출
   - opensearch { index("products"); batchSize(500) } 설정
   - 여러 sink 동시 등록

의존성: Wave 1 (SinkSpec, Models)
```

---

## [F] Deploy Accessors

```
RFC-IMPL-011 Wave 2-F 구현해줘. (Deploy Accessors)

목표: compile.sync(), ship.async { }, cutover.ready() 메서드

구현할 파일:
1. sdk/dsl/deploy/CompileAccessor.kt
   @IvmDslMarker
   class CompileAccessor internal constructor(
       private val onSet: (CompileMode) -> Unit
   ) {
       fun sync() { onSet(CompileMode.Sync) }
       fun async() { onSet(CompileMode.Async) }
       
       // RFC-009: targets 지원
       operator fun invoke(block: CompileTargetsBuilder.() -> Unit) {
           val targets = CompileTargetsBuilder().apply(block).build()
           onSet(CompileMode.SyncWithTargets(targets))
       }
   }

2. sdk/dsl/deploy/ShipAccessor.kt
   @IvmDslMarker
   class ShipAccessor internal constructor(
       private val onSet: (ShipSpec) -> Unit
   ) {
       fun sync(block: SinkBuilder.() -> Unit) {
           val sinks = SinkBuilder().apply(block).build()
           onSet(ShipSpec(ShipMode.Sync, sinks))
       }
       
       fun async(block: SinkBuilder.() -> Unit) {
           val sinks = SinkBuilder().apply(block).build()
           onSet(ShipSpec(ShipMode.Async, sinks))
       }
   }

3. sdk/dsl/deploy/ShipAsyncOnlyAccessor.kt
   // compile.async 일 때 사용 - sync 메서드 없음!
   @IvmDslMarker
   class ShipAsyncOnlyAccessor internal constructor(
       private val onSet: (ShipSpec) -> Unit
   ) {
       fun async(block: SinkBuilder.() -> Unit) {
           val sinks = SinkBuilder().apply(block).build()
           onSet(ShipSpec(ShipMode.Async, sinks))
       }
       // sync 없음 - 타입 레벨에서 차단!
   }

4. sdk/dsl/deploy/CutoverAccessor.kt
   @IvmDslMarker
   class CutoverAccessor internal constructor(
       private val onSet: (CutoverMode) -> Unit
   ) {
       fun ready() { onSet(CutoverMode.Ready) }
       fun done() { onSet(CutoverMode.Done) }
   }

의존성: Wave 1 (Models), Wave 2-E (SinkBuilder)
```

---

## [G] State Machine

```
RFC-IMPL-011 Wave 2-G 구현해줘. (State Machine)

목표: Deploy 상태 전이 로직

구현할 파일:
1. sdk/execution/DeployStateMachine.kt
   import arrow.core.Either
   import arrow.core.left
   import arrow.core.right
   
   object DeployStateMachine {
       fun transition(current: DeployState, event: DeployEvent): Either<StateError, DeployState> =
           when (current) {
               DeployState.QUEUED -> when (event) {
                   is DeployEvent.StartRunning -> DeployState.RUNNING.right()
                   is DeployEvent.Failed -> DeployState.FAILED.right()
                   else -> StateError.InvalidTransition(current, event).left()
               }
               DeployState.RUNNING -> when (event) {
                   is DeployEvent.CompileComplete -> DeployState.READY.right()
                   is DeployEvent.Failed -> DeployState.FAILED.right()
                   else -> StateError.InvalidTransition(current, event).left()
               }
               DeployState.READY -> when (event) {
                   is DeployEvent.StartSinking -> DeployState.SINKING.right()
                   is DeployEvent.Failed -> DeployState.FAILED.right()
                   else -> StateError.InvalidTransition(current, event).left()
               }
               DeployState.SINKING -> when (event) {
                   is DeployEvent.Complete -> DeployState.DONE.right()
                   is DeployEvent.Failed -> DeployState.FAILED.right()
                   else -> StateError.InvalidTransition(current, event).left()
               }
               DeployState.DONE, DeployState.FAILED -> 
                   StateError.InvalidTransition(current, event).left()
           }
   }

테스트:
2. test/.../sdk/StateMachineTest.kt
   - QUEUED → RUNNING → READY → SINKING → DONE 정상 전이
   - QUEUED → CompileComplete 시 InvalidTransition
   - DONE 상태에서 모든 이벤트 거부
   - 어느 상태에서든 Failed 이벤트는 FAILED로 전이

의존성: Wave 1-B (DeployState, DeployEvent, StateError)
```

---

# 🌊 Wave 3: Builders 조합 (동시 작업 2개)

> **전제조건**: Wave 2 완료

## [H] Deploy Builders

```
RFC-IMPL-011 Wave 3-H 구현해줘. (Deploy Builders)

목표: .deploy { compile.sync(); ship.async { } } 빌더

구현할 파일:
1. sdk/dsl/deploy/DeployBuilder.kt
   @IvmDslMarker
   class DeployBuilder internal constructor() {
       private var compileMode: CompileMode = CompileMode.Sync
       private var shipSpec: ShipSpec? = null
       private var cutoverMode: CutoverMode = CutoverMode.Ready
       
       val compile = CompileAccessor { compileMode = it }
       val ship = ShipAccessor { shipSpec = it }
       val cutover = CutoverAccessor { cutoverMode = it }
       
       internal fun build(): DeploySpec {
           // Axis Validation: compile.async + ship.sync 차단
           if (compileMode == CompileMode.Async && shipSpec?.mode == ShipMode.Sync) {
               throw IllegalStateException(
                   "Invalid axis combination: compile.async + ship.sync is not allowed. " +
                   "Use ship.async instead."
               )
           }
           return DeploySpec(compileMode, shipSpec, cutoverMode)
       }
   }

2. sdk/dsl/deploy/DeployAsyncBuilder.kt
   // compile.async 전용 - ship.sync 타입 레벨 차단
   @IvmDslMarker
   class DeployAsyncBuilder internal constructor() {
       private var shipSpec: ShipSpec? = null
       private var cutoverMode: CutoverMode = CutoverMode.Ready
       
       val compile = object {
           fun async() {} // 이미 async 모드
       }
       val ship = ShipAsyncOnlyAccessor { shipSpec = it }
       val cutover = CutoverAccessor { cutoverMode = it }
       
       internal fun build(): DeploySpec {
           return DeploySpec(CompileMode.Async, shipSpec, cutoverMode)
       }
   }

3. sdk/validation/AxisValidator.kt
   object AxisValidator {
       fun validate(spec: DeploySpec): List<String> {
           val errors = mutableListOf<String>()
           
           // compile.async + ship.sync 차단
           if (spec.compileMode == CompileMode.Async && 
               spec.shipSpec?.mode == ShipMode.Sync) {
               errors.add("compile.async + ship.sync is not allowed")
           }
           
           return errors
       }
   }

테스트:
4. test/.../sdk/DeployBuilderTest.kt
   - deploy { compile.sync(); ship.async { opensearch() } } 정상
   - deploy { compile.async(); ship.async { opensearch() } } 정상
   - deploy { compile.async(); ship.sync { } } IllegalStateException

5. test/.../sdk/AxisValidationTest.kt

의존성: Wave 2-F (Accessors), Wave 2-E (SinkBuilder)
```

---

## [I] Compiler Targets (RFC-009)

```
RFC-IMPL-011 Wave 3-I 구현해줘. (Compiler Targets)

목표: compile { targets { searchDoc(); recoFeed() } }

구현할 파일:
1. sdk/dsl/deploy/CompileTargetsBuilder.kt
   @IvmDslMarker
   class CompileTargetsBuilder internal constructor() {
       private val targets = mutableListOf<TargetRef>()
       
       fun targets(block: TargetsBuilder.() -> Unit) {
           TargetsBuilder(targets).apply(block)
       }
       
       internal fun build(): List<TargetRef> = targets.toList()
   }

2. sdk/dsl/deploy/TargetsBuilder.kt
   @IvmDslMarker
   class TargetsBuilder internal constructor(
       private val targets: MutableList<TargetRef>
   ) {
       fun searchDoc(version: String = "v1") {
           targets.add(TargetRef("search-doc", version))
       }
       
       fun recoFeed(version: String = "v1") {
           targets.add(TargetRef("reco-feed", version))
       }
       
       fun custom(id: String, version: String = "v1") {
           targets.add(TargetRef(id, version))
       }
   }

테스트:
3. test/.../sdk/TargetsDslTest.kt
   - compile { targets { searchDoc() } } 정상
   - compile { targets { searchDoc(); recoFeed() } } 복수 타겟
   - targets가 CompileMode.SyncWithTargets로 설정됨

의존성: Wave 1 (TargetRef)
```

---

# 🌊 Wave 4: Integration (단일 작업)

> **전제조건**: Wave 3 완료

## [J] DeployableContext

```
RFC-IMPL-011 Wave 4-J 구현해줘. (DeployableContext 통합)

목표: 모든 DSL 조합하여 deploy(), deployNow() 등 완성

구현할 파일:
1. sdk/dsl/deploy/DeployableContext.kt
   @IvmDslMarker
   class DeployableContext internal constructor(
       private val input: EntityInput,
       private val config: IvmClientConfig
   ) {
       // Full DSL
       fun deploy(block: DeployBuilder.() -> Unit): DeployResult {
           val spec = DeployBuilder().apply(block).build()
           return execute(spec)
       }
       
       // Async DSL (타입 안전)
       fun deployAsync(block: DeployAsyncBuilder.() -> Unit): DeployJob {
           val spec = DeployAsyncBuilder().apply(block).build()
           return executeAsync(spec)
       }
       
       // === Shortcut APIs (RFC-008 Section 11) ===
       
       // compile.sync + ship.async + cutover.ready
       fun deployNow(block: SinkBuilder.() -> Unit): DeployResult {
           val sinks = SinkBuilder().apply(block).build()
           val spec = DeploySpec(
               compileMode = CompileMode.Sync,
               shipSpec = ShipSpec(ShipMode.Async, sinks),
               cutoverMode = CutoverMode.Ready
           )
           return execute(spec)
       }
       
       // compile.sync + ship.sync + cutover.ready
       fun deployNowAndShipNow(block: SinkBuilder.() -> Unit): DeployResult {
           val sinks = SinkBuilder().apply(block).build()
           val spec = DeploySpec(
               compileMode = CompileMode.Sync,
               shipSpec = ShipSpec(ShipMode.Sync, sinks),
               cutoverMode = CutoverMode.Ready
           )
           return execute(spec)
       }
       
       // compile.async + ship.async + cutover.ready
       fun deployQueued(block: SinkBuilder.() -> Unit): DeployJob {
           val sinks = SinkBuilder().apply(block).build()
           val spec = DeploySpec(
               compileMode = CompileMode.Async,
               shipSpec = ShipSpec(ShipMode.Async, sinks),
               cutoverMode = CutoverMode.Ready
           )
           return executeAsync(spec)
       }
       
       // === Internal Execution ===
       
       private fun execute(spec: DeploySpec): DeployResult {
           // TODO: Wave 5에서 DeployExecutor 연동
           val entityKey = "${input.entityType}:${(input as? ProductInput)?.sku ?: "unknown"}"
           val version = "v1-${System.currentTimeMillis()}"
           return DeployResult.success(entityKey, version)
       }
       
       private fun executeAsync(spec: DeploySpec): DeployJob {
           // TODO: Wave 5에서 DeployExecutor 연동
           val entityKey = "${input.entityType}:${(input as? ProductInput)?.sku ?: "unknown"}"
           val version = "v1-${System.currentTimeMillis()}"
           val jobId = "job-${java.util.UUID.randomUUID()}"
           return DeployJob(jobId, entityKey, version, DeployState.QUEUED)
       }
   }

테스트:
2. test/.../sdk/DeployableContextTest.kt
3. test/.../sdk/ShortcutApiTest.kt
   - deployNow { opensearch() } → compile.sync + ship.async
   - deployNowAndShipNow { opensearch() } → compile.sync + ship.sync
   - deployQueued { opensearch() } → compile.async + ship.async + DeployJob 반환

의존성: Wave 3 전체 (DeployBuilder, DeployAsyncBuilder, SinkBuilder)
```

---

# 🌊 Wave 5: Execution (동시 작업 2개)

> **전제조건**: Wave 4 완료

## [K] Status API

```
RFC-IMPL-011 Wave 5-K 구현해줘. (Status API)

목표: Ivm.client().deploy.status(jobId), .await(jobId)

구현할 파일:
1. sdk/client/DeployStatusApi.kt
   class DeployStatusApi internal constructor(
       private val config: IvmClientConfig
   ) {
       suspend fun status(jobId: String): DeployJobStatus {
           // TODO: 실제 API 호출 or Repository 조회
           return DeployJobStatus(
               jobId = jobId,
               state = DeployState.RUNNING,
               createdAt = java.time.Instant.now(),
               updatedAt = java.time.Instant.now()
           )
       }
       
       suspend fun await(
           jobId: String, 
           timeout: java.time.Duration = java.time.Duration.ofMinutes(5),
           pollInterval: java.time.Duration = java.time.Duration.ofSeconds(1)
       ): DeployResult {
           val deadline = java.time.Instant.now().plus(timeout)
           
           while (java.time.Instant.now().isBefore(deadline)) {
               val status = status(jobId)
               when (status.state) {
                   DeployState.DONE -> return DeployResult.success(jobId, "completed")
                   DeployState.FAILED -> return DeployResult.failure(jobId, "failed", status.error ?: "Unknown error")
                   else -> kotlinx.coroutines.delay(pollInterval.toMillis())
               }
           }
           
           return DeployResult.failure(jobId, "timeout", "Timeout waiting for job completion")
       }
   }

2. sdk/client/PlanExplainApi.kt
   class PlanExplainApi internal constructor(
       private val config: IvmClientConfig
   ) {
       fun explainLastPlan(deployId: String): DeployPlan {
           // TODO: 실제 API 호출
           return DeployPlan(
               deployId = deployId,
               graph = DependencyGraph(emptyMap()),
               activatedRules = listOf("product-to-search-doc", "product-to-reco-feed"),
               executionSteps = emptyList()
           )
       }
   }

3. IvmClient.kt 수정 - deploy, plan 프로퍼티 추가
   class IvmClient internal constructor(private val config: IvmClientConfig) {
       fun ingest(): IngestContext = IngestContext(config)
       val deploy: DeployStatusApi = DeployStatusApi(config)
       val plan: PlanExplainApi = PlanExplainApi(config)
   }

테스트:
4. test/.../sdk/StatusApiTest.kt
   - Ivm.client().deploy.status("job-123") 호출 가능
   - await() 타임아웃 테스트

의존성: Wave 1-B (DeployJobStatus, DeployPlan)
```

---

## [L] Executor & Integration Tests

```
RFC-IMPL-011 Wave 5-L 구현해줘. (Executor & Integration)

목표: 실제 Workflow 연동 + Golden Tests

구현할 파일:
1. sdk/execution/DeployExecutor.kt
   class DeployExecutor(
       private val ingestWorkflow: IngestWorkflow,
       private val slicingWorkflow: SlicingWorkflow,
       private val outboxRepository: OutboxRepository
   ) {
       suspend fun <T : EntityInput> executeSync(input: T, spec: DeploySpec): DeployResult {
           // 1. RawData Ingest
           val rawResult = ingestWorkflow.ingest(input.toRawData())
           
           // 2. Compile (Slicing)
           if (spec.compileMode != CompileMode.Async) {
               slicingWorkflow.compile(rawResult.entityKey, rawResult.version)
           }
           
           // 3. Ship
           spec.shipSpec?.let { shipSpec ->
               if (shipSpec.mode == ShipMode.Sync) {
                   // 동기 Ship
                   shipSpec.sinks.forEach { sink -> shipToSink(sink, rawResult) }
               } else {
                   // 비동기 Ship → Outbox
                   shipSpec.sinks.forEach { sink ->
                       outboxRepository.insert(OutboxTask.ship(sink, rawResult))
                   }
               }
           }
           
           return DeployResult.success(rawResult.entityKey, rawResult.version)
       }
       
       suspend fun <T : EntityInput> executeAsync(input: T, spec: DeploySpec): DeployJob {
           // 1. RawData Ingest만 동기
           val rawResult = ingestWorkflow.ingest(input.toRawData())
           
           // 2. COMPILE_TASK Outbox 적재
           val jobId = outboxRepository.insert(
               OutboxTask.compile(rawResult.entityKey, rawResult.version, spec)
           )
           
           return DeployJob(jobId, rawResult.entityKey, rawResult.version, DeployState.QUEUED)
       }
   }

2. apps/runtimeapi/wiring/SdkModule.kt
   val sdkModule = module {
       single { DeployExecutor(get(), get(), get()) }
   }

3. DeployableContext.kt 수정 - Executor 연동
   (기존 TODO 부분을 실제 DeployExecutor 호출로 변경)

테스트 (Golden Tests):
4. test/.../sdk/RfcGoldenTest.kt
   // RFC-008 예시 전체 테스트
   @Test fun `RFC-008 9-1 Raw Input DSL`()
   @Test fun `RFC-008 10-1 Default Deploy`()
   @Test fun `RFC-008 10-2 All Sync`()
   @Test fun `RFC-008 10-3 Async Deploy`()
   @Test fun `RFC-008 11-1 deployNow`()
   @Test fun `RFC-008 11-2 deployNowAndShipNow`()
   @Test fun `RFC-008 11-3 deployQueued`()
   @Test fun `RFC-009 11-1 Compile with Targets`()
   @Test fun `RFC-009 11-2 Explain Plan`()

5. test/.../sdk/DeployExecutorIntegrationTest.kt
   - InMemory 어댑터로 E2E 테스트

의존성: Wave 4 (DeployableContext), 기존 Workflow들
```

---

# 📋 동시 작업 요약

```
┌─────────────────────────────────────────────────────────────────┐
│ Wave 1: 작업자 2명 동시 가능                                      │
│   [A] Markers & Models ←──── 작업자 1                            │
│   [B] State Models ←──────── 작업자 2                            │
├─────────────────────────────────────────────────────────────────┤
│ Wave 2: 작업자 5명 동시 가능                                      │
│   [C] Client Core ←───────── 작업자 1                            │
│   [D] Entity DSL ←────────── 작업자 2                            │
│   [E] Sink DSL ←──────────── 작업자 3                            │
│   [F] Deploy Accessors ←──── 작업자 4                            │
│   [G] State Machine ←─────── 작업자 5                            │
├─────────────────────────────────────────────────────────────────┤
│ Wave 3: 작업자 2명 동시 가능                                      │
│   [H] Deploy Builders ←───── 작업자 1                            │
│   [I] Compiler Targets ←──── 작업자 2                            │
├─────────────────────────────────────────────────────────────────┤
│ Wave 4: 작업자 1명 (통합)                                         │
│   [J] DeployableContext                                          │
├─────────────────────────────────────────────────────────────────┤
│ Wave 5: 작업자 2명 동시 가능                                      │
│   [K] Status API ←────────── 작업자 1                            │
│   [L] Executor & Tests ←──── 작업자 2                            │
├─────────────────────────────────────────────────────────────────┤
│ Wave 6: 작업자 2명 동시 가능 (Full Automation)                    │
│   [M] Entity Codegen ←────── 작업자 1                            │
│   [N] Sink Codegen ←──────── 작업자 2                            │
└─────────────────────────────────────────────────────────────────┘

총 Wave: 6개
최대 동시 작업자: 5명 (Wave 2)
예상 소요: Wave 1~6 순차 진행 시 6 라운드
```

---

# 🌊 Wave 6: Contract Codegen (동시 작업 2개)

> **전제조건**: Wave 5 완료 (SDK 수동 사용 가능 상태)

## [M] Entity Codegen

```
RFC-IMPL-011 Wave 6-M 구현해줘. (Entity Codegen)

목표: RuleSet Contract YAML → EntityDsl 자동 생성

구현할 파일:
1. codegen/EntityDslGenerator.kt
   class EntityDslGenerator(
       private val outputDir: Path,
       private val packageName: String = "com.oliveyoung.ivmlite.sdk.dsl.entity"
   ) {
       fun generate(ruleSetPath: Path) {
           val ruleSet = YamlParser.parse<RuleSetContract>(ruleSetPath)
           
           ruleSet.entities.forEach { entity ->
               generateEntityInput(entity)
               generateEntityBuilder(entity)
               generateExtensionFunction(entity)
           }
       }
       
       private fun generateEntityInput(entity: EntitySchema) {
           // KotlinPoet으로 data class 생성
           val fileSpec = FileSpec.builder(packageName, "${entity.name}Input")
               .addType(TypeSpec.classBuilder("${entity.name}Input")
                   .addModifiers(KModifier.DATA)
                   .addSuperinterface(EntityInput::class)
                   .primaryConstructor(buildConstructor(entity))
                   .addProperties(buildProperties(entity))
                   .build())
               .build()
           fileSpec.writeTo(outputDir)
       }
       
       private fun generateEntityBuilder(entity: EntitySchema) {
           // @IvmDslMarker class ${entity.name}Builder 생성
           // 필수/옵션 필드 분석
           // fun build(): ${entity.name}Input
       }
       
       private fun generateExtensionFunction(entity: EntitySchema) {
           // fun IngestContext.${entity.name.lowercase()}(block: ...): DeployableContext
       }
   }

2. codegen/model/EntitySchema.kt
   data class EntitySchema(
       val name: String,
       val fields: List<FieldSchema>
   )
   
   data class FieldSchema(
       val name: String,
       val type: String,
       val required: Boolean = false,
       val defaultValue: Any? = null
   )

3. codegen/model/RuleSetContract.kt
   data class RuleSetContract(
       val version: String,
       val entities: List<EntitySchema>,
       val rules: List<RuleSchema>
   )

4. codegen/parser/YamlParser.kt
   object YamlParser {
       private val yaml = Yaml(...)
       inline fun <reified T> parse(path: Path): T
   }

테스트:
5. test/.../codegen/EntityDslGeneratorTest.kt
   - Product YAML → ProductInput.kt, ProductBuilder.kt 생성
   - 생성된 코드 컴파일 성공
   - 필수 필드 올바르게 추출

샘플 Contract (contracts/product.yaml):
```yaml
version: "1.0"
entities:
  - name: Product
    fields:
      - name: tenantId
        type: String
        required: true
      - name: sku
        type: String
        required: true
      - name: name
        type: String
        required: true
      - name: price
        type: Long
        required: true
      - name: currency
        type: String
        defaultValue: "KRW"
      - name: category
        type: String
      - name: brand
        type: String
```

의존성: Wave 5 완료 (기존 SDK 구조 참조)
```

---

## [N] Sink Codegen

```
RFC-IMPL-011 Wave 6-N 구현해줘. (Sink Codegen)

목표: SinkRule Contract YAML → SinkDsl 자동 생성

구현할 파일:
1. codegen/SinkDslGenerator.kt
   class SinkDslGenerator(
       private val outputDir: Path,
       private val packageName: String = "com.oliveyoung.ivmlite.sdk.dsl.sink"
   ) {
       fun generate(sinkRulePath: Path) {
           val sinkRule = YamlParser.parse<SinkRuleContract>(sinkRulePath)
           
           sinkRule.sinks.forEach { sink ->
               generateSinkSpec(sink)
               generateSinkBuilder(sink)
               generateSinkBuilderExtension(sink)
           }
       }
       
       private fun generateSinkSpec(sink: SinkSchema) {
           // data class ${sink.name}SinkSpec(...) : SinkSpec
       }
       
       private fun generateSinkBuilder(sink: SinkSchema) {
           // @IvmDslMarker class ${sink.name}Builder
       }
       
       private fun generateSinkBuilderExtension(sink: SinkSchema) {
           // fun SinkBuilder.${sink.name.lowercase()}(block: ...): Unit
       }
   }

2. codegen/model/SinkRuleContract.kt
   data class SinkRuleContract(
       val version: String,
       val sinks: List<SinkSchema>
   )
   
   data class SinkSchema(
       val name: String,
       val type: String,  // "opensearch", "personalize", "kafka", etc.
       val config: List<ConfigField>
   )
   
   data class ConfigField(
       val name: String,
       val type: String,
       val required: Boolean = false,
       val defaultValue: Any? = null
   )

테스트:
3. test/.../codegen/SinkDslGeneratorTest.kt
   - OpenSearch YAML → OpenSearchSinkSpec.kt, OpenSearchBuilder.kt 생성
   - 커스텀 Sink (예: Kafka) 추가 시 자동 생성

샘플 Contract (contracts/sinks.yaml):
```yaml
version: "1.0"
sinks:
  - name: OpenSearch
    type: opensearch
    config:
      - name: index
        type: String
      - name: alias
        type: String
      - name: batchSize
        type: Int
        defaultValue: 1000
        
  - name: Personalize
    type: personalize
    config:
      - name: datasetArn
        type: String
      - name: roleArn
        type: String
        
  - name: Kafka
    type: kafka
    config:
      - name: topic
        type: String
        required: true
      - name: bootstrapServers
        type: String
        required: true
```

의존성: Wave 5 완료 (기존 SDK 구조 참조)
```

---

## [O] Gradle Plugin

```
RFC-IMPL-011 Wave 6-O 구현해줘. (Gradle Plugin)

목표: ./gradlew generateIvmDsl 명령으로 자동 생성

구현할 파일:
1. buildSrc/src/main/kotlin/IvmCodegenPlugin.kt
   class IvmCodegenPlugin : Plugin<Project> {
       override fun apply(project: Project) {
           val extension = project.extensions.create(
               "ivmCodegen", 
               IvmCodegenExtension::class.java
           )
           
           project.tasks.register("generateIvmDsl", IvmCodegenTask::class.java) {
               it.contractsDir.set(extension.contractsDir)
               it.outputDir.set(extension.outputDir)
               it.packageName.set(extension.packageName)
           }
       }
   }

2. buildSrc/src/main/kotlin/IvmCodegenExtension.kt
   open class IvmCodegenExtension {
       var contractsDir: String = "contracts"
       var outputDir: String = "build/generated/ivm-sdk"
       var packageName: String = "com.oliveyoung.ivmlite.sdk.generated"
   }

3. buildSrc/src/main/kotlin/IvmCodegenTask.kt
   abstract class IvmCodegenTask : DefaultTask() {
       @get:InputDirectory
       abstract val contractsDir: DirectoryProperty
       
       @get:OutputDirectory
       abstract val outputDir: DirectoryProperty
       
       @get:Input
       abstract val packageName: Property<String>
       
       @TaskAction
       fun generate() {
           val contracts = contractsDir.get().asFile.toPath()
           val output = outputDir.get().asFile.toPath()
           
           // Entity 생성
           contracts.resolve("entities").toFile().listFiles()?.forEach { file ->
               EntityDslGenerator(output, packageName.get()).generate(file.toPath())
           }
           
           // Sink 생성
           contracts.resolve("sinks").toFile().listFiles()?.forEach { file ->
               SinkDslGenerator(output, packageName.get()).generate(file.toPath())
           }
       }
   }

4. build.gradle.kts 수정
   plugins {
       id("ivm-codegen")
   }
   
   ivmCodegen {
       contractsDir = "contracts"
       outputDir = "src/main/kotlin/generated"
       packageName = "com.oliveyoung.ivmlite.sdk.generated"
   }
   
   sourceSets {
       main {
           kotlin {
               srcDir("src/main/kotlin/generated")
           }
       }
   }

사용법:
./gradlew generateIvmDsl

검증:
- contracts/ 폴더에 YAML 추가 시 재생성
- 생성된 코드가 기존 수동 작성과 동일 인터페이스

의존성: Wave 6-M, 6-N 완료
```

---

# ⚡ Quick Reference

| Wave | 작업 | 의존성 | 병렬 |
|------|------|--------|------|
| 1-A | Markers & Models | 없음 | ✅ |
| 1-B | State Models | 없음 | ✅ |
| 2-C | Client Core | 1-A | ✅ |
| 2-D | Entity DSL | 1-A, 2-C | ✅ |
| 2-E | Sink DSL | 1-A | ✅ |
| 2-F | Deploy Accessors | 1-A, 2-E | ✅ |
| 2-G | State Machine | 1-B | ✅ |
| 3-H | Deploy Builders | 2-E, 2-F | ✅ |
| 3-I | Compiler Targets | 1-A | ✅ |
| 4-J | DeployableContext | Wave 3 전체 | ❌ |
| 5-K | Status API | 1-B | ✅ |
| 5-L | Executor & Tests | 4-J | ✅ |
| 6-M | Entity Codegen | Wave 5 | ✅ |
| 6-N | Sink Codegen | Wave 5 | ✅ |
| 6-O | Gradle Plugin | 6-M, 6-N | ❌ |

---

# 🎯 완료 기준

```
Wave 1~5 완료 = SDK 수동 사용 가능 ✅
Wave 6 완료   = Contract 변경 시 SDK 자동 재생성 ✅✅

전체 완료 시:
- Ivm.client().ingest().product { } ✅
- .deploy { compile.sync(); ship.async { } } ✅
- deployNow, deployQueued shortcuts ✅
- Axis Validation (타입 안전) ✅
- State Machine ✅
- Status API ✅
- Compiler Targets (RFC-009) ✅
- Contract Codegen (Full Automation) ✅
```