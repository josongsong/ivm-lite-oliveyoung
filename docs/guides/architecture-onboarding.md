# IVM-Lite 아키텍처 온보딩

## 개요

IVM-Lite는 **Incremental View Maintenance** 패턴을 구현한 엔터프라이즈급 데이터 뷰 엔진입니다.

**핵심 기능**: RawData → Slicing → View 변환 파이프라인

---

## 아키텍처 패턴

### 헥사고날 아키텍처 (Ports & Adapters)

```
┌─────────────────────────────────────────────────────────────┐
│                      SDK Layer (Ivm)                        │
│  Ivm.product { ... }.deploy()  |  Ivm.query(...).get()      │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                   Application Layer                          │
│              (apps/runtimeapi/routes)                        │
│         REST API: /api/v1/ingest, /api/v2/query              │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                 Orchestration Layer                          │
│              (pkg/orchestration/application)                 │
│  IngestWorkflow | SlicingWorkflow | QueryViewWorkflow        │
└───────┬──────────────────┬──────────────────┬───────────────┘
        │                  │                  │
┌───────▼───────┐  ┌───────▼───────┐  ┌───────▼───────┐
│    RawData    │  │    Slices     │  │   Contracts   │
│    Domain     │  │    Domain     │  │    Domain     │
│  (pkg/rawdata)│  │ (pkg/slices)  │  │(pkg/contracts)│
└───────┬───────┘  └───────┬───────┘  └───────┬───────┘
        │                  │                  │
┌───────▼──────────────────▼──────────────────▼───────────────┐
│                      Ports Layer                             │
│  RawDataRepositoryPort | SliceRepositoryPort | ContractPort  │
└───────┬──────────────────┬──────────────────┬───────────────┘
        │                  │                  │
┌───────▼───────┐  ┌───────▼───────┐  ┌───────▼───────┐
│   Adapters    │  │   Adapters    │  │   Adapters    │
│ Jooq/DynamoDB │  │ Jooq/DynamoDB │  │DynamoDB/YAML  │
│   /InMemory   │  │   /InMemory   │  │   /InMemory   │
└───────────────┘  └───────────────┘  └───────────────┘
```

---

## 패키지 구조

```
src/main/kotlin/com/oliveyoung/ivmlite/
├── shared/                    # 공유 레이어
│   ├── config/               # AppConfig
│   ├── domain/
│   │   ├── types/            # TenantId, EntityKey, SliceType, SemVer
│   │   ├── determinism/      # CanonicalJson, Hashing
│   │   └── errors/           # DomainError
│   ├── ports/                # ContractCachePort, SingleFlightPort
│   └── adapters/             # TracingExtensions
│
├── sdk/                       # SDK 진입점
│   ├── Ivm.kt               # 메인 진입점
│   ├── client/              # IvmClient, QueryBuilder
│   ├── dsl/                 # ProductBuilder, BrandBuilder
│   ├── schema/              # Views, ViewRef (코드젠)
│   └── execution/           # DeployExecutor, DeployStateMachine
│
├── pkg/                       # 핵심 비즈니스 로직
│   ├── rawdata/
│   │   ├── domain/          # RawDataRecord, OutboxEntry
│   │   ├── ports/           # RawDataRepositoryPort, OutboxRepositoryPort
│   │   └── adapters/        # Jooq, DynamoDB, InMemory
│   │
│   ├── slices/
│   │   ├── domain/          # SliceRecord, SlicingEngine, InvertedIndexBuilder
│   │   ├── ports/           # SliceRepositoryPort, InvertedIndexRepositoryPort
│   │   └── adapters/        # Jooq, DynamoDB, InMemory
│   │
│   ├── contracts/
│   │   ├── domain/          # ViewDefinitionContract, RuleSetContract
│   │   ├── ports/           # ContractRegistryPort
│   │   └── adapters/        # DynamoDB, LocalYaml, Gated
│   │
│   ├── orchestration/
│   │   └── application/     # IngestWorkflow, SlicingWorkflow, QueryViewWorkflow
│   │
│   └── changeset/
│       └── domain/          # ChangeSetBuilder, ImpactCalculator
│
└── apps/                      # 애플리케이션
    ├── runtimeapi/
    │   ├── routes/          # IngestRoutes, QueryRoutes, HealthRoutes
    │   ├── dto/             # Requests, Responses
    │   └── wiring/          # Koin Modules (DI)
    └── opscli/              # 운영 CLI
```

---

## 핵심 컴포넌트

### 1. SDK 진입점 (Ivm.kt)

```kotlin
// Write API
Ivm.product {
    tenantId = "oliveyoung"
    sku = "SKU-001"
    name = "비타민C"
    price = 15000
}.deploy()

// Read API (타입 세이프)
val view = Ivm.query(Views.Product.Pdp)
    .key("SKU-001")
    .get()

// Read API (문자열)
val view = Ivm.query("product.pdp")
    .key("SKU-001")
    .get()
```

### 2. Workflow (Orchestration Layer)

| Workflow | 역할 | 파일 |
|----------|------|------|
| `IngestWorkflow` | RawData 저장 + Outbox 이벤트 생성 | `pkg/orchestration/application/IngestWorkflow.kt` |
| `SlicingWorkflow` | SlicingEngine으로 Slice 생성 (FULL/INCREMENTAL) | `pkg/orchestration/application/SlicingWorkflow.kt` |
| `QueryViewWorkflow` | ViewDefinition 기반 Slice 조회 | `pkg/orchestration/application/QueryViewWorkflow.kt` |

### 3. SlicingEngine (Domain Layer)

```kotlin
// RuleSet 기반 슬라이싱
suspend fun slice(rawData: RawDataRecord, ruleSetRef: ContractRef): Result<SlicingResult>

// INCREMENTAL 슬라이싱 (영향받는 타입만)
suspend fun slicePartial(rawData, ruleSetRef, impactedTypes): Result<SlicingResult>
```

**SlicingResult**:
- `slices`: 생성된 SliceRecord 목록
- `indexes`: 생성된 InvertedIndexEntry 목록

### 4. Contract (SSOT)

| Contract | 역할 |
|----------|------|
| `ViewDefinitionContract` | 조회 정책 (requiredSlices, missingPolicy) |
| `RuleSetContract` | 슬라이싱 규칙 (slices, indexes) |

---

## 데이터 흐름

### Write Path (배포)

```
1. Ivm.product { ... }.deploy()
   ↓
2. IngestWorkflow.execute()
   - CanonicalJson.canonicalize(payload)  # 정규화
   - Hashing.sha256Hex(...)               # 해싱
   - rawRepo.putIdempotent(record)        # RawData 저장
   - outboxRepo.insert(entry)             # Outbox 저장
   ↓
3. SlicingWorkflow.execute()  (또는 executeAuto)
   - rawRepo.get()                        # RawData 조회
   - slicingEngine.slice()                # 슬라이싱
   - sliceRepo.putAllIdempotent()         # Slice 저장
   - invertedIndexRepo.putAllIdempotent() # Index 저장
   ↓
4. Sink 전파 (OpenSearch, Personalize)
```

### Read Path (조회)

```
1. Ivm.query("product.pdp").key("SKU-001").get()
   ↓
2. QueryViewWorkflow.execute()
   - contractRegistry.loadViewDefinitionContract()  # Contract 로드
   - sliceRepo.getByVersion()                       # Slice 조회
   - MissingPolicy 적용 (FAIL_CLOSED / PARTIAL_ALLOWED)
   - 응답 JSON 생성
   ↓
3. ViewResponse 반환
```

### INCREMENTAL 슬라이싱

**SlicingWorkflow 메서드**:
```kotlin
// AUTO 모드: 이전 버전 유무에 따라 FULL/INCREMENTAL 자동 선택
suspend fun executeAuto(tenantId, entityKey, version, ruleSetRef): Result<List<SliceKey>>

// INCREMENTAL 모드: ChangeSet 기반 영향받는 Slice만 재생성
suspend fun executeIncremental(tenantId, entityKey, fromVersion, toVersion, ruleSetRef): Result<List<SliceKey>>
```

**동작 흐름**:
```
1. executeAuto(tenantId, entityKey, version)
   ↓
2. 이전 버전 존재 여부 확인
   - 없으면 → FULL 슬라이싱 (execute())
   - 있으면 → INCREMENTAL 슬라이싱 (executeIncremental())
   ↓
3. executeIncremental(fromVersion, toVersion)
   - ChangeSetBuilder.build()           # 변경 감지
   - ImpactCalculator.calculate()       # 영향 분석
   - slicingEngine.slicePartial()       # 부분 슬라이싱
   - 기존 Slice 버전 업데이트
   - Tombstone 처리
```

---

## Port-Adapter 맵핑

### Ports

| Port | 메서드 |
|------|--------|
| `RawDataRepositoryPort` | `putIdempotent()`, `get()` |
| `SliceRepositoryPort` | `putAllIdempotent()`, `batchGet()`, `getByVersion()`, `findByKeyPrefix()`, `count()` |
| `ContractRegistryPort` | `loadViewDefinitionContract()`, `loadRuleSetContract()`, `listViewDefinitions()` |
| `OutboxRepositoryPort` | `insert()`, `findPending()`, `markProcessed()` |
| `InvertedIndexRepositoryPort` | `putAllIdempotent()`, `queryByIndex()`, `listTargets()` |

### Adapters

| Port | InMemory | PostgreSQL | DynamoDB |
|------|----------|------------|----------|
| RawData | `InMemoryRawDataRepository` | `JooqRawDataRepository` | `DynamoDbRawDataRepository` |
| Slice | `InMemorySliceRepository` | `JooqSliceRepository` | `DynamoDbSliceRepository` |
| Contract | - | - | `DynamoDBContractRegistryAdapter` |
| Outbox | `InMemoryOutboxRepository` | `JooqOutboxRepository` | - |
| InvertedIndex | `InMemoryInvertedIndexRepository` | `JooqInvertedIndexRepository` | `DynamoDbInvertedIndexRepository` |

### DI 모듈 (Koin)

| 모듈 | 용도 |
|------|------|
| `adapterModule` | InMemory 어댑터 (개발/테스트) |
| `jooqAdapterModule` | PostgreSQL 어댑터 |
| `dynamodbContractModule` | DynamoDB Contract 어댑터 |
| `productionAdapterModule` | DynamoDB + PostgreSQL (운영) |

---

## 설계 원칙

### 1. Contract is Law

Contract가 실행 규칙의 **단일 진실 공급원(SSOT)**.

```kotlin
// ViewDefinitionContract
data class ViewDefinitionContract(
    val requiredSlices: List<SliceType>,   // 필수 슬라이스
    val optionalSlices: List<SliceType>,   // 선택 슬라이스
    val missingPolicy: MissingPolicy,      // FAIL_CLOSED | PARTIAL_ALLOWED
    val partialPolicy: PartialPolicy,
    val ruleSetRef: ContractRef,
)
```

### 2. Fail-Closed

필수 리소스 누락 시 **즉시 실패** (불완전한 데이터 제공 차단).

```kotlin
// QueryViewWorkflow.kt:88-98
when (viewDef.missingPolicy) {
    MissingPolicy.FAIL_CLOSED -> {
        if (missingRequired.isNotEmpty()) {
            return Result.Err(DomainError.MissingSliceError(...))
        }
    }
    // ...
}
```

### 3. Determinism (결정성)

동일 입력 → 동일 출력.

```kotlin
// 정규화
val canonical = CanonicalJson.canonicalize(payloadJson)

// 해싱
val hash = "sha256:" + Hashing.sha256Hex(canonical + "|" + schemaId + "|" + schemaVersion)
```

### 4. Idempotency (멱등성)

재실행해도 동일 결과. 메서드명으로 명시: `putIdempotent()`, `putAllIdempotent()`.

### 5. OpenTelemetry Tracing

전 계층 추적 지원.

```kotlin
tracer.withSpanSuspend(
    "IngestWorkflow.execute",
    mapOf(
        "tenant_id" to tenantId.value,
        "entity_key" to entityKey.value,
        "version" to version.toString(),
    ),
) { /* ... */ }
```

### 6. Transactional Outbox

RawData 저장과 이벤트 발행의 **원자성 보장**.

```kotlin
// IngestWorkflow.kt
rawRepo.putIdempotent(record)     // RawData 저장
outboxRepo.insert(outboxEntry)    // Outbox 저장 (같은 트랜잭션)
// → OutboxPollingWorker가 비동기로 처리
```

---

## 도메인 모델

### RawDataRecord

```kotlin
data class RawDataRecord(
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val version: Long,
    val schemaId: String,
    val schemaVersion: SemVer,
    val payload: String,       // 정규화된 JSON
    val payloadHash: String,   // SHA256 해시
)
```

### SliceRecord

```kotlin
data class SliceRecord(
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val version: Long,
    val sliceType: SliceType,  // CORE, PRICING, IMAGES 등
    val data: String,          // JSON 데이터
    val hash: String,          // Slice 해시
    val ruleSetId: String,
    val ruleSetVersion: SemVer,
    val tombstone: Tombstone?, // 삭제 표시
)
```

### OutboxEntry

```kotlin
data class OutboxEntry(
    val id: String,
    val aggregateType: AggregateType,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val status: OutboxStatus,  // PENDING, PROCESSED, FAILED
    val createdAt: Instant,
    val processedAt: Instant?,
)
```

---

## API 엔드포인트

| 엔드포인트 | 메서드 | 설명 |
|------------|--------|------|
| `/api/v1/ingest` | POST | RawData 수집 |
| `/api/v1/slice` | POST | 슬라이싱 실행 |
| `/api/v2/query` | POST | View 조회 (Contract 기반) |
| `/health` | GET | 헬스체크 |

---

## 파일 참조

| 역할 | 파일 경로 |
|------|-----------|
| SDK 진입점 | `src/main/kotlin/.../sdk/Ivm.kt` |
| Ingest Workflow | `src/main/kotlin/.../pkg/orchestration/application/IngestWorkflow.kt` |
| Slicing Workflow | `src/main/kotlin/.../pkg/orchestration/application/SlicingWorkflow.kt` |
| Query Workflow | `src/main/kotlin/.../pkg/orchestration/application/QueryViewWorkflow.kt` |
| Slicing Engine | `src/main/kotlin/.../pkg/slices/domain/SlicingEngine.kt` |
| ViewDefinition | `src/main/kotlin/.../pkg/contracts/domain/ViewDefinitionContract.kt` |
| DI 모듈 | `src/main/kotlin/.../apps/runtimeapi/wiring/AdapterModule.kt` |
| 워크플로우 모듈 | `src/main/kotlin/.../apps/runtimeapi/wiring/WorkflowModule.kt` |

---

## Quick Start

### 1. 개발 환경 (InMemory)

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.0")
}

// 사용
Ivm.configure {
    tenantId = "dev"
}

Ivm.product {
    sku = "SKU-001"
    name = "테스트 상품"
}.deploy()

val view = Ivm.query(Views.Product.Pdp)
    .key("SKU-001")
    .get()
```

### 2. 운영 환경 (PostgreSQL + DynamoDB)

```kotlin
// Koin DI 설정
startKoin {
    modules(
        infraModule,           // DB 커넥션
        productionAdapterModule,
        workflowModule,
    )
}
```

---

## 참고 문서

- [SDK Guide](sdk-guide.md)
- [RFC 문서](rfc/)
  - RFC-003: ViewDefinition Contract
  - RFC-010: Slicing Engine
  - RFCIMPL-009: OpenTelemetry Tracing
  - RFCIMPL-010: INCREMENTAL Slicing
