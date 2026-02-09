# SDK & API 엔진 아키텍처

## 개요

SDK와 Runtime API가 사용하는 핵심 엔진들의 위치와 역할을 정리합니다.

---

## 1. SlicingEngine (슬라이싱 엔진)

**위치**: `src/main/kotlin/com/oliveyoung/ivmlite/pkg/slices/domain/SlicingEngine.kt`

**역할**:
- **RawData → Slice 변환**: RuleSet Contract 기반으로 원본 데이터를 여러 Slice로 분리
- **JOIN 실행**: Light JOIN을 통한 관련 엔티티 데이터 병합
- **Inverted Index 생성**: 검색용 인덱스 동시 생성
- **결정성 보장**: 동일 RawData + RuleSet → 동일 Slices (멱등성)

**주요 메서드**:
```kotlin
// FULL 슬라이싱
suspend fun slice(
    rawData: RawDataRecord,
    ruleSetRef: ContractRef
): Result<SlicingResult>

// INCREMENTAL 슬라이싱 (영향받은 타입만)
suspend fun slicePartial(
    rawData: RawDataRecord,
    ruleSetRef: ContractRef,
    impactedTypes: Set<SliceType>
): Result<SlicingResult>
```

**사용처**:
- `SlicingWorkflow` → `SlicingEnginePort` (Adapter) → `SlicingEngine`
- `DefaultSlicingEngineAdapter`가 Port 인터페이스로 래핑

**의존성**:
- `ContractRegistryPort`: RuleSet Contract 로드
- `JoinExecutor` (optional): JOIN 실행

---

## 2. QueryViewWorkflow (뷰 조회 엔진)

**위치**: `src/main/kotlin/com/oliveyoung/ivmlite/pkg/orchestration/application/QueryViewWorkflow.kt`

**역할**:
- **View 조회**: ViewDefinition Contract 기반으로 여러 Slice를 조합하여 View 생성
- **MissingPolicy 처리**: 필수 슬라이스 누락 시 정책에 따라 처리 (FAIL_CLOSED / PARTIAL_ALLOWED)
- **Range Query**: 키 프리픽스 기반 범위 조회
- **Count Query**: 조건에 맞는 Slice 개수 조회
- **Latest Query**: 특정 엔티티의 최신 버전 조회

**주요 메서드**:
```kotlin
// ViewDefinition 기반 조회 (v2)
suspend fun execute(
    tenantId: TenantId,
    viewId: String,
    entityKey: EntityKey,
    version: Long
): Result<ViewResponse>

// 최신 버전 조회
suspend fun executeLatest(
    tenantId: TenantId,
    entityKey: EntityKey,
    sliceType: SliceType? = null
): Result<ViewResponse>

// Range Query
suspend fun executeRange(
    tenantId: TenantId,
    keyPrefix: String,
    sliceType: SliceType? = null,
    limit: Int = 100,
    cursor: String? = null
): Result<RangeResult>

// Count Query
suspend fun executeCount(
    tenantId: TenantId,
    keyPrefix: String? = null,
    sliceType: SliceType? = null
): Result<Long>

// v1 호환 (deprecated)
@Deprecated("Use execute(tenantId, viewId, entityKey, version) with ViewDefinitionContract")
suspend fun execute(
    tenantId: TenantId,
    viewId: String,
    entityKey: EntityKey,
    version: Long,
    requiredSliceTypes: List<SliceType>
): Result<ViewResponse>
```

**사용처**:
- **SDK**: `QueryApi.executeQueryViaWorkflow()` → `QueryViewWorkflow.execute()`
- **Runtime API**: `QueryRoutes.post("/api/v2/query")` → `QueryViewWorkflow.execute()`

**의존성**:
- `SliceRepositoryPort`: Slice 조회
- `ContractRegistryPort`: ViewDefinition Contract 로드

---

## 3. SlicingWorkflow (슬라이싱 오케스트레이션)

**위치**: `src/main/kotlin/com/oliveyoung/ivmlite/pkg/orchestration/application/SlicingWorkflow.kt`

**역할**:
- **FULL 슬라이싱**: 전체 Slice 재생성
- **INCREMENTAL 슬라이싱**: 영향받은 SliceType만 재생성 (ChangeSet 기반)
- **AUTO 모드**: 이전 버전 유무에 따라 FULL/INCREMENTAL 자동 선택
- **SlicingEngine 호출**: 실제 슬라이싱 로직은 SlicingEngine에 위임
- **저장**: 생성된 Slice와 InvertedIndex를 저장소에 저장
- **Tombstone 처리**: 삭제된 Slice는 tombstone으로 마킹

**주요 메서드**:
```kotlin
// FULL 슬라이싱
suspend fun execute(
    tenantId: TenantId,
    entityKey: EntityKey,
    version: Long,
    ruleSetRef: ContractRef = V1_RULESET_REF
): Result<List<SliceKey>>

// AUTO 모드: 이전 버전 유무에 따라 FULL/INCREMENTAL 자동 선택
suspend fun executeAuto(
    tenantId: TenantId,
    entityKey: EntityKey,
    version: Long,
    ruleSetRef: ContractRef = V1_RULESET_REF
): Result<List<SliceKey>>

// INCREMENTAL 슬라이싱 (ChangeSet 기반)
suspend fun executeIncremental(
    tenantId: TenantId,
    entityKey: EntityKey,
    fromVersion: Long,
    toVersion: Long,
    ruleSetRef: ContractRef
): Result<List<SliceKey>>
```

**사용처**:
- **SDK**: `DeployExecutor.executeSync()` → `SlicingWorkflow.execute()`
- **Runtime API**: `QueryRoutes.post("/api/v1/slice")` → `SlicingWorkflow.execute()`
- **OutboxPollingWorker**: `RawDataIngested` 이벤트 → `SlicingWorkflow.executeAuto()`

**의존성**:
- `RawDataRepositoryPort`: RawData 조회
- `SliceRepositoryPort`: Slice 저장
- `SlicingEnginePort`: 실제 슬라이싱 실행
- `InvertedIndexRepositoryPort`: 인덱스 저장
- `ChangeSetBuilderPort`: 변경사항 빌드
- `ImpactCalculatorPort`: 영향도 계산
- `ContractRegistryPort`: RuleSet Contract 로드

---

## 4. IngestWorkflow (데이터 수집 엔진)

**위치**: `src/main/kotlin/com/oliveyoung/ivmlite/pkg/orchestration/application/IngestWorkflow.kt`

**역할**:
- **RawData 저장**: 원본 데이터를 정규화하여 저장
- **Outbox 이벤트 생성**: `RawDataIngested` 이벤트를 Outbox에 추가
- **멱등성 보장**: 동일 키로 재요청 시 기존 데이터 반환
- **Transactional Outbox**: UnitOfWork 사용 시 RawData + Outbox 원자적 처리

**주요 메서드**:
```kotlin
suspend fun execute(
    tenantId: TenantId,
    entityKey: EntityKey,
    version: Long,
    schemaId: String,
    schemaVersion: SemVer,
    payloadJson: String
): Result<Unit>
```

**생성자**:
```kotlin
// Primary: UnitOfWork 사용 (트랜잭션 원자성 보장, 권장)
constructor(
    unitOfWork: IngestUnitOfWorkPort,
    tracer: Tracer = OpenTelemetry.noop().getTracer("ingest")
)

// Legacy: 개별 Port 사용 (하위 호환성)
constructor(
    rawRepo: RawDataRepositoryPort,
    outboxRepo: OutboxRepositoryPort,
    tracer: Tracer = OpenTelemetry.noop().getTracer("ingest")
)
```

**사용처**:
- **SDK**: `DeployExecutor.executeSync()` → `IngestWorkflow.execute()`
- **Runtime API**: `IngestRoutes.post("/api/v1/ingest")` → `IngestWorkflow.execute()`

**의존성**:
- `IngestUnitOfWorkPort` (권장): RawData + Outbox 트랜잭션 처리
- `RawDataRepositoryPort` (legacy): RawData 저장
- `OutboxRepositoryPort` (legacy): Outbox 이벤트 생성

---

## 5. DeployExecutor (SDK 배포 실행 엔진)

**위치**: `src/main/kotlin/com/oliveyoung/ivmlite/sdk/execution/DeployExecutor.kt`

**역할**:
- **전체 배포 파이프라인 오케스트레이션**: Ingest → Compile → Ship 순서 실행
- **동기/비동기 모드**: CompileMode에 따라 동기 또는 Outbox 기반 비동기 처리
- **Ship은 항상 비동기**: 모든 Ship은 Outbox를 통해 비동기로 처리 (일관성 보장)
- **세분화된 제어 API**: ingestOnly, compileOnly, compileAsync, shipAsync 등 개별 단계 제어

**주요 메서드**:
```kotlin
// 동기 Deploy 실행 (Ingest → Compile → Ship 순차 실행)
suspend fun <T : EntityInput> executeSync(input: T, spec: DeploySpec): DeployResult

// 비동기 Deploy 실행 (Ingest만 동기, 나머지 Outbox)
suspend fun <T : EntityInput> executeAsync(input: T, spec: DeploySpec): Either<DomainError, DeployJob>

// === 단계별 제어 API ===

// Ingest만 실행 (동기)
suspend fun <T : EntityInput> ingestOnly(input: T): IngestResult

// Compile만 실행 (동기) - 이미 Ingest된 데이터
suspend fun <T : EntityInput> compileOnly(input: T, version: Long): CompileResult

// Compile 비동기 실행 (Outbox)
suspend fun <T : EntityInput> compileAsync(input: T, version: Long): Either<DomainError, DeployJob>

// Ship 비동기 실행 (Outbox)
suspend fun <T : EntityInput> shipAsync(input: T, version: Long): Either<DomainError, DeployJob>

// 특정 Sink만 비동기 Ship (Outbox)
suspend fun <T : EntityInput> shipAsyncTo(input: T, version: Long, sinks: List<SinkSpec>): Either<DomainError, DeployJob>
```

**DeploySpec 구조**:
```kotlin
sealed class DeploySpec {
    abstract val compileMode: CompileMode
    abstract val cutoverMode: CutoverMode

    data class Full(
        override val compileMode: CompileMode = CompileMode.Sync,
        val ship: ShipSpec,  // 필수
        override val cutoverMode: CutoverMode = CutoverMode.Ready
    ) : DeploySpec()

    data class CompileOnly(
        override val compileMode: CompileMode = CompileMode.Sync,
        override val cutoverMode: CutoverMode = CutoverMode.Ready
    ) : DeploySpec()
}

sealed interface CompileMode {
    object Sync : CompileMode
    object Async : CompileMode
    data class SyncWithTargets(val targets: List<TargetRef>) : CompileMode
}
```

**사용처**:
- **SDK**: `Ivm.product { ... }.deploy()` → `DeployExecutor.executeSync()`

**의존성**:
- `IngestWorkflow`: RawData 저장
- `SlicingWorkflow`: Slice 생성
- `ShipWorkflow`: Sink 전송
- `OutboxRepositoryPort`: 비동기 작업 큐잉

---

## 데이터 흐름

### Write Path (SDK / API → 엔진)

```
1. SDK/API 요청
   ↓
2. IngestWorkflow.execute()
   → RawData 저장 + Outbox 이벤트 생성 (원자적 트랜잭션)
   ↓
3. SlicingWorkflow.execute() / executeAuto()
   → RawData 조회
   → SlicingEngine.slice() 호출
   → Slice + InvertedIndex 생성
   → 저장소에 저장
   ↓
4. ShipWorkflow (Outbox 기반 비동기)
   → Sink로 전송
```

### Read Path (SDK / API → 엔진)

```
1. SDK/API 조회 요청
   ↓
2. QueryViewWorkflow.execute() / executeLatest() / executeRange()
   → ViewDefinition Contract 로드
   → 필요한 SliceType 결정
   → SliceRepository에서 Slice 조회
   → MissingPolicy 적용
   → View JSON 생성
   ↓
3. 응답 반환
```

---

## 엔진 계층 구조

```
┌─────────────────────────────────────────┐
│  SDK / Runtime API (진입점)              │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  Workflow Layer (Orchestration)         │
│  - IngestWorkflow                        │
│  - SlicingWorkflow                       │
│  - QueryViewWorkflow                     │
│  - ShipWorkflow                          │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  Domain Engine Layer                     │
│  - SlicingEngine (슬라이싱 로직)         │
│  - JoinExecutor (JOIN 실행)               │
│  - ChangeSetBuilder (변경 추적)          │
│  - ImpactCalculator (영향도 계산)        │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  Repository Layer (Port/Adapter)        │
│  - RawDataRepositoryPort                 │
│  - SliceRepositoryPort                  │
│  - ContractRegistryPort                  │
│  - IngestUnitOfWorkPort                  │
└──────────────────────────────────────────┘
```

---

## 핵심 원칙

1. **Contract is Law**: 모든 엔진은 Contract를 SSOT로 사용
   - `SlicingEngine`: RuleSet Contract 기반 슬라이싱
   - `QueryViewWorkflow`: ViewDefinition Contract 기반 조회

2. **결정성 보장**: 동일 입력 → 동일 출력
   - `SlicingEngine`: CanonicalJson 정규화 + SHA256 해싱
   - `QueryViewWorkflow`: SliceType 정렬 후 조합

3. **멱등성 보장**: 재실행해도 동일 결과
   - `IngestWorkflow`: `putIdempotent()` 사용
   - `SlicingWorkflow`: `putAllIdempotent()` 사용

4. **Cross-domain Orchestration**: Workflow가 여러 도메인 조율
   - `SlicingWorkflow`: RawData 도메인 + Slice 도메인
   - `QueryViewWorkflow`: Contract 도메인 + Slice 도메인

5. **Transactional Outbox**: 원자성 보장
   - `IngestWorkflow`: UnitOfWork로 RawData + Outbox 원자적 처리
   - `DeployExecutor`: Ship은 항상 Outbox 기반 비동기 처리

---

## DI 바인딩 (Koin)

**위치**: `src/main/kotlin/com/oliveyoung/ivmlite/apps/runtimeapi/wiring/`

- **WorkflowModule**: Workflow 인스턴스 생성
- **AdapterModule**: SlicingEngine → SlicingEnginePort 어댑터
- **SdkModule**: DeployExecutor 및 SDK 초기화

**초기화 순서**:
1. Repository/Adapter 생성
2. Workflow 생성 (의존성 주입)
3. SDK 초기화 (`Ivm.initialize()`)
