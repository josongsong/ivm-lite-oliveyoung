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
- **최신 버전 조회**: 특정 엔티티의 최신 버전 조회

**주요 메서드**:
```kotlin
// ViewDefinition 기반 조회 (v2)
suspend fun execute(
    tenantId: TenantId,
    viewId: String,
    entityKey: EntityKey,
    version: Long
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
- **INCREMENTAL 슬라이싱**: 영향받은 SliceType만 재생성
- **SlicingEngine 호출**: 실제 슬라이싱 로직은 SlicingEngine에 위임
- **저장**: 생성된 Slice와 InvertedIndex를 저장소에 저장
- **ChangeSet 생성**: 변경사항 추적

**주요 메서드**:
```kotlin
// FULL 슬라이싱
suspend fun execute(
    tenantId: TenantId,
    entityKey: EntityKey,
    version: Long,
    ruleSetRef: ContractRef = V1_RULESET_REF
): Result<List<SliceKey>>

// INCREMENTAL 슬라이싱
suspend fun executePartial(
    tenantId: TenantId,
    entityKey: EntityKey,
    version: Long,
    impactedTypes: Set<SliceType>,
    ruleSetRef: ContractRef = V1_RULESET_REF
): Result<List<SliceKey>>
```

**사용처**:
- **SDK**: `DeployExecutor` → `SlicingWorkflow.execute()`
- **Runtime API**: `QueryRoutes.post("/api/v1/slice")` → `SlicingWorkflow.execute()`
- **OutboxPollingWorker**: `RawDataIngested` 이벤트 처리 시 자동 슬라이싱

**의존성**:
- `RawDataRepositoryPort`: RawData 조회
- `SliceRepositoryPort`: Slice 저장
- `SlicingEnginePort`: 실제 슬라이싱 실행
- `InvertedIndexRepositoryPort`: 인덱스 저장
- `ChangeSetBuilderPort`: 변경사항 추적
- `ImpactCalculatorPort`: 영향도 계산

---

## 4. IngestWorkflow (데이터 수집 엔진)

**위치**: `src/main/kotlin/com/oliveyoung/ivmlite/pkg/orchestration/application/IngestWorkflow.kt`

**역할**:
- **RawData 저장**: 원본 데이터를 정규화하여 저장
- **Outbox 이벤트 생성**: `RawDataIngested` 이벤트를 Outbox에 추가
- **멱등성 보장**: 동일 키로 재요청 시 기존 데이터 반환

**주요 메서드**:
```kotlin
suspend fun execute(
    tenantId: TenantId,
    entityKey: EntityKey,
    payload: String,
    version: Long? = null
): Result<RawDataRecord>
```

**사용처**:
- **SDK**: `DeployExecutor.ingest()` → `IngestWorkflow.execute()`
- **Runtime API**: `IngestRoutes.post("/api/v1/ingest")` → `IngestWorkflow.execute()`

**의존성**:
- `RawDataRepositoryPort`: RawData 저장
- `OutboxRepositoryPort`: Outbox 이벤트 생성

---

## 5. DeployExecutor (SDK 배포 실행 엔진)

**위치**: `src/main/kotlin/com/oliveyoung/ivmlite/sdk/execution/DeployExecutor.kt`

**역할**:
- **전체 배포 파이프라인 오케스트레이션**: Ingest → Compile → Ship 순서 실행
- **동기/비동기 모드**: Sync는 즉시 실행, Async는 Outbox 사용
- **Ship 트리거**: Slicing 완료 후 Sink로 전송

**주요 메서드**:
```kotlin
suspend fun execute(
    tenantId: TenantId,
    entityKey: EntityKey,
    payload: String,
    version: Long? = null,
    shipMode: ShipMode = ShipMode.Sync
): Result<DeployResult>
```

**사용처**:
- **SDK**: `Ivm.product { ... }.deploy()` → `DeployExecutor.execute()`

**의존성**:
- `IngestWorkflow`: RawData 저장
- `SlicingWorkflow`: Slice 생성
- `ShipWorkflow`: Sink 전송
- `OutboxRepositoryPort`: 비동기 모드용

---

## 데이터 흐름

### Write Path (SDK / API → 엔진)

```
1. SDK/API 요청
   ↓
2. IngestWorkflow.execute()
   → RawData 저장 + Outbox 이벤트 생성
   ↓
3. SlicingWorkflow.execute()
   → RawData 조회
   → SlicingEngine.slice() 호출
   → Slice + InvertedIndex 생성
   → 저장소에 저장
   ↓
4. ShipWorkflow.execute() (선택적)
   → Sink로 전송
```

### Read Path (SDK / API → 엔진)

```
1. SDK/API 조회 요청
   ↓
2. QueryViewWorkflow.execute()
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
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  Repository Layer (Port/Adapter)        │
│  - RawDataRepositoryPort                 │
│  - SliceRepositoryPort                  │
│  - ContractRegistryPort                  │
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
