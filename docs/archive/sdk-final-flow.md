# 최종 SDK 흐름 (Final SDK Flow)

> **버전**: RFC-IMPL-013 완료  
> **최종 업데이트**: 2026-01-27  
> **상태**: Production Ready (SOTA-level Polling-based Outbox)

---

## 🎯 핵심 요약

```kotlin
// 이것만으로 끝!
ivm.product(product).deploy()
// → RawData Ingest → Slice 생성 → SinkRule 매칭 → 자동 Ship → Sink 전달
```

**핵심 원칙**:
1. **Zero Config Ship**: SinkRule만 정의하면 자동 전송
2. **Outbox-Only**: 모든 Ship은 Outbox를 통해 비동기 처리
3. **자동 트리거**: Slicing 완료 시 SinkRule 기반 자동 ShipRequested 생성
4. **Override 가능**: 필요시 `ship.to { }`로 특정 sink 지정

---

## 📊 전체 흐름도

```
┌─────────────────────────────────────────────────────────────────┐
│ SDK 진입점                                                       │
│ ivm.product(product).deploy()                                   │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│ DeployableContext.deploy()                                      │
│ - DeployBuilder.build() → DeploySpec 생성                        │
│   • shipSpec 있으면 → DeploySpec.Full                            │
│   • shipSpec 없으면 → DeploySpec.CompileOnly (SinkRule 자동)    │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│ DeployExecutor.executeSync()                                     │
│                                                                  │
│ 1. Ingest (항상 동기)                                            │
│    → IngestWorkflow.execute()                                   │
│    → RawData 저장 (PostgreSQL)                                   │
│    → RawDataIngested outbox 생성 (aggregateType: RAW_DATA)      │
│                                                                  │
│ 2. Compile (spec.compileMode에 따라)                            │
│    • Sync: SlicingWorkflow.execute() 직접 호출                  │
│    • Async: CompileRequested outbox 생성                        │
│                                                                  │
│ 3. Ship (spec.shipSpec에 따라)                                  │
│    • shipSpec 있으면: ShipRequested outbox 생성 (명시적)        │
│    • shipSpec 없으면: SinkRule 기반 자동 (OutboxPollingWorker)  │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│ OutboxPollingWorker (백그라운드)                                │
│                                                                  │
│ Polling Loop:                                                    │
│   claim(batchSize, aggregateType) → PENDING → PROCESSING        │
│                                                                  │
│ 이벤트 타입별 처리:                                              │
│                                                                  │
│ ┌──────────────────────────────────────────────────────────┐   │
│ │ RawDataIngested (aggregateType: RAW_DATA)               │   │
│ │   → SlicingWorkflow.executeAuto()                        │   │
│ │   → Slice 저장 (DynamoDB)                               │   │
│ │   → SinkRuleRegistry.findByEntityAndSliceType()         │   │
│ │   → ShipRequested outbox 자동 생성 (매칭되는 SinkRule마다)│   │
│ └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│ ┌──────────────────────────────────────────────────────────┐   │
│ │ ShipRequested (aggregateType: SLICE)                     │   │
│ │   → ShipEventHandler.handleSliceEvent()                  │   │
│ │   → ShipWorkflow.execute()                               │   │
│ │   → Sink.ship() → OpenSearch/Personalize                 │   │
│ └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│ ┌──────────────────────────────────────────────────────────┐   │
│ │ CompileRequested (aggregateType: RAW_DATA)              │   │
│ │   → SlicingWorkflow.executeAuto()                        │   │
│ │   → (동일하게 자동 ShipRequested 생성)                    │   │
│ └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔄 상세 단계별 흐름

### 1단계: SDK 호출

```kotlin
// 사용자 코드
ivm.product(product).deploy()
```

**파일**: `DeployableContext.kt:59`

```kotlin
fun deploy(block: DeployBuilder.() -> Unit = {}): DeployResult {
    val spec = DeployBuilder().apply(block).build()
    return execute(spec)
}
```

**DeployBuilder.build()**:
- `shipSpec` 있으면 → `DeploySpec.Full`
- `shipSpec` 없으면 → `DeploySpec.CompileOnly` (SinkRule 자동)

---

### 2단계: DeployExecutor 실행

**파일**: `DeployExecutor.kt:53`

#### 2-1. Ingest (항상 동기)

```kotlin
val ingestResult = ingestWorkflow.execute(
    tenantId = rawDataParams.tenantId,
    entityKey = rawDataParams.entityKey,
    version = rawDataParams.version,
    schemaId = rawDataParams.schemaId,
    schemaVersion = rawDataParams.schemaVersion,
    payloadJson = rawDataParams.payloadJson
)
```

**IngestWorkflow.execute()**:
1. RawData 저장 (PostgreSQL `raw_data` 테이블)
2. `RawDataIngested` outbox 생성:
   ```kotlin
   OutboxEntry.create(
       aggregateType = AggregateType.RAW_DATA,
       eventType = "RawDataIngested",
       payload = { tenantId, entityKey, version, ... }
   )
   ```

**결과**: RawData 저장 완료, Outbox에 `RawDataIngested` 이벤트 등록

---

#### 2-2. Compile (Slicing)

**경로 A: Sync 모드**

```kotlin
when (spec.compileMode) {
    is CompileMode.Sync -> {
        val slicingResult = slicingWorkflow.execute(
            tenantId = rawDataParams.tenantId,
            entityKey = rawDataParams.entityKey,
            version = rawDataParams.version
        )
    }
}
```

**SlicingWorkflow.execute()**:
1. RawData 조회
2. SlicingEngine.slice() → Slice 생성
3. Slice 저장 (DynamoDB)
4. InvertedIndex 저장

**결과**: Slice 생성 완료 (동기)

---

**경로 B: Async 모드**

```kotlin
is CompileMode.Async -> {
    val compileTaskEntry = OutboxEntry.create(
        aggregateType = AggregateType.RAW_DATA,
        eventType = "CompileRequested",
        payload = { tenantId, entityKey, version, compileMode: "async" }
    )
    outboxRepository.insert(compileTaskEntry)
}
```

**결과**: `CompileRequested` outbox 생성 → OutboxPollingWorker가 나중에 처리

---

#### 2-3. Ship

**경로 A: 명시적 Ship (shipSpec 있음)**

```kotlin
spec.shipSpec?.let { shipSpec ->
    shipSpec.sinks.forEach { sink ->
        val shipTaskEntry = OutboxEntry.create(
            aggregateType = AggregateType.SLICE,
            eventType = "ShipRequested",
            payload = {
                tenantId, entityKey, version,
                sink: "opensearch" | "personalize",
                shipMode: "async"
            }
        )
        outboxRepository.insert(shipTaskEntry)
    }
}
```

**결과**: `ShipRequested` outbox 생성 (명시적 sink 지정)

---

**경로 B: 자동 Ship (shipSpec 없음, SinkRule 기반)**

```kotlin
// DeployExecutor에서는 ShipRequested 생성 안 함
// OutboxPollingWorker가 Slicing 완료 후 자동 생성
```

**결과**: OutboxPollingWorker가 처리 (3단계 참고)

---

### 3단계: OutboxPollingWorker 처리

**파일**: `OutboxPollingWorker.kt`

#### 3-1. RawDataIngested 처리

**파일**: `OutboxPollingWorker.kt:322`

```kotlin
private suspend fun processRawDataEvent(entry: OutboxEntry) {
    when (entry.eventType) {
        OutboxEventTypes.RAW_DATA_INGESTED -> {
            val payload = parseRawDataIngestedPayload(entry.payload)
            
            // Slicing 실행
            val result = slicingWorkflow.executeAuto(
                tenantId = TenantId(payload.tenantId),
                entityKey = EntityKey(payload.entityKey),
                version = payload.version,
            )
            
            when (result) {
                is SlicingWorkflow.Result.Ok -> {
                    // RFC-IMPL-013: 자동 ShipRequested 생성
                    autoTriggerShip(
                        tenantId = payload.tenantId,
                        entityKey = payload.entityKey,
                        version = payload.version,
                        sliceKeys = result.value.map { ... }
                    )
                }
            }
        }
    }
}
```

---

#### 3-2. 자동 ShipRequested 생성 (SinkRule 기반)

**파일**: `OutboxPollingWorker.kt:349`

```kotlin
private suspend fun autoTriggerShip(
    tenantId: String, entityKey: String, version: Long,
    sliceKeys: List<SliceKey>
) {
    val registry = sinkRuleRegistry ?: return
    val entityType = extractEntityType(entityKey) ?: return
    
    val processedSinks = mutableSetOf<String>()
    
    for (sliceKey in sliceKeys) {
        // SinkRule 조회
        val rulesResult = registry.findByEntityAndSliceType(
            entityType = entityType,
            sliceType = sliceKey.sliceType
        )
        
        when (rulesResult) {
            is SinkRuleRegistryPort.Result.Ok -> {
                for (rule in rulesResult.value) {
                    if (rule.status != SinkRuleStatus.ACTIVE) continue
                    if (processedSinks.contains(rule.target.sinkId)) continue
                    
                    // ShipRequested outbox 생성
                    val shipEntry = OutboxEntry.create(
                        aggregateType = AggregateType.SLICE,
                        aggregateId = "$tenantId:$entityKey",
                        eventType = "ShipRequested",
                        payload = {
                            tenantId, entityKey, version,
                            sink: rule.target.sinkId,
                            sinkRuleId: rule.id,
                            shipMode: "async"
                        }
                    )
                    outboxRepository.insert(shipEntry)
                    processedSinks.add(rule.target.sinkId)
                }
            }
            is SinkRuleRegistryPort.Result.Err -> {
                logger.warn("Failed to query SinkRule: {}", rulesResult.error)
            }
        }
    }
}
```

**SinkRule 매칭 로직**:
1. `entityType` 추출 (예: "PRODUCT")
2. `sliceType` 추출 (예: `SliceType.CORE`)
3. `SinkRuleRegistry.findByEntityAndSliceType()` 호출
4. `ACTIVE` 상태인 SinkRule만 사용
5. 매칭되는 SinkRule마다 `ShipRequested` outbox 생성

**결과**: 매칭되는 SinkRule 수만큼 `ShipRequested` outbox 생성

---

#### 3-3. ShipRequested 처리

**파일**: `ShipEventHandler.kt:45`

```kotlin
private suspend fun processShipRequested(entry: OutboxEntry) {
    val payload = json.decodeFromString<ShipRequestedPayload>(entry.payload)
    
    val sinkType = mapSinkName(payload.sink)  // "opensearch" → SinkType.OPENSEARCH
    
    val result = shipWorkflow.execute(
        tenantId = TenantId(payload.tenantId),
        entityKey = EntityKey(payload.entityKey),
        version = payload.version.toLong(),
        sinkType = sinkType
    )
    
    when (result) {
        is ShipWorkflow.Result.Ok -> {
            logger.info("Ship completed: {}:{} v{} → {}", 
                payload.tenantId, payload.entityKey, payload.version, payload.sink)
        }
        is ShipWorkflow.Result.Err -> {
            throw ProcessingException("Ship failed: ${result.error}")
        }
    }
}
```

---

#### 3-4. ShipWorkflow 실행

**파일**: `ShipWorkflow.kt:39`

```kotlin
suspend fun execute(
    tenantId: TenantId,
    entityKey: EntityKey,
    version: Long,
    sinkType: SinkType
): Result {
    // 1. Sink 찾기
    val sink = sinks[sinkType] ?: return Result.Err("Sink not found: $sinkType")
    
    // 2. Slice 조회
    val sliceResult = sliceRepo.getByVersion(tenantId, entityKey, version)
    when (sliceResult) {
        is SliceRepositoryPort.Result.Err -> {
            return Result.Err("Slice not found: ${sliceResult.error}")
        }
        is SliceRepositoryPort.Result.Ok -> { /* continue */ }
    }
    
    // 3. Slice 병합
    val mergedPayload = mergeSlices(sliceResult.value)
    
    // 4. Sink로 전달
    val shipResult = sink.ship(tenantId, entityKey, version, mergedPayload)
    
    return when (shipResult) {
        is SinkPort.Result.Ok -> Result.Ok
        is SinkPort.Result.Err -> Result.Err(shipResult.error)
    }
}
```

---

#### 3-5. Sink 전달

**예시: OpenSearchSinkAdapter**

```kotlin
override suspend fun ship(
    tenantId: TenantId,
    entityKey: EntityKey,
    version: Long,
    payload: String
): SinkPort.Result {
    val documentId = buildDocumentId(tenantId, entityKey)
    val indexName = buildIndexName(tenantId)  // SinkRule에서 가져온 indexPattern
    
    val response = client.put("${config.endpoint}/$indexName/_doc/$documentId") {
        setBody(payload)
    }
    
    return if (response.status.isSuccess()) {
        SinkPort.Result.Ok
    } else {
        SinkPort.Result.Err("OpenSearch error: ${response.status}")
    }
}
```

**결과**: OpenSearch/Personalize 등 외부 Sink로 데이터 전달 완료

---

## 🎨 사용 패턴별 흐름

### 패턴 1: 기본 (SinkRule 자동)

```kotlin
ivm.product(product).deploy()
```

**흐름**:
1. Ingest → RawDataIngested outbox
2. Compile (Sync) → Slice 생성
3. OutboxPollingWorker:
   - RawDataIngested 처리 → Slicing (이미 완료됨)
   - SinkRule 매칭 → ShipRequested 자동 생성
   - ShipRequested 처리 → Sink 전달

**특징**: Ship 설정 불필요, SinkRule만 정의하면 자동 전송

---

### 패턴 2: Async Compile

```kotlin
ivm.product(product).deploy {
    compile.async()
}
```

**흐름**:
1. Ingest → RawDataIngested outbox
2. CompileRequested outbox 생성
3. OutboxPollingWorker:
   - CompileRequested 처리 → Slicing
   - SinkRule 매칭 → ShipRequested 자동 생성
   - ShipRequested 처리 → Sink 전달

**특징**: Compile도 비동기, 전체 파이프라인 비동기

---

### 패턴 3: 명시적 Ship Override

```kotlin
ivm.product(product).deploy {
    ship.to { personalize() }
}
```

**흐름**:
1. Ingest → RawDataIngested outbox
2. Compile (Sync) → Slice 생성
3. DeployExecutor:
   - ShipRequested outbox 생성 (sink: "personalize")
4. OutboxPollingWorker:
   - ShipRequested 처리 → Personalize 전달

**특징**: SinkRule 무시, 명시적 sink로만 전송

---

### 패턴 4: Compile Only (Ship 비활성화)

```kotlin
ivm.product(product).compileOnly()
```

**흐름**:
1. Ingest → RawDataIngested outbox
2. Compile (Sync) → Slice 생성
3. OutboxPollingWorker:
   - RawDataIngested 처리 → Slicing (이미 완료됨)
   - **ShipRequested 생성 안 함** (compileOnly 플래그)

**특징**: Slice만 생성, Ship 완전 비활성화

---

## 🔑 핵심 컴포넌트

### 1. DeployableContext

**역할**: SDK 진입점, DSL 빌더

**주요 메서드**:
- `deploy(block)` - 기본 deploy
- `deployAsync(block)` - 비동기 deploy
- `compileOnly(block)` - Ship 비활성화

**파일**: `sdk/dsl/deploy/DeployableContext.kt`

---

### 2. DeployExecutor

**역할**: 실제 Workflow 실행, Outbox 생성

**주요 메서드**:
- `executeSync(input, spec)` - 동기 실행
- `executeAsync(input, spec)` - 비동기 실행

**파일**: `sdk/execution/DeployExecutor.kt`

---

### 3. OutboxPollingWorker

**역할**: Outbox 이벤트 처리, 자동 Ship 트리거

**주요 기능**:
- Polling (claim → process → markProcessed)
- RawDataIngested → Slicing → 자동 ShipRequested
- ShipRequested → ShipWorkflow
- Stale 복구, Visibility Timeout

**파일**: `pkg/orchestration/application/OutboxPollingWorker.kt`

---

### 4. SinkRuleRegistry

**역할**: SinkRule 조회, 자동 라우팅

**주요 메서드**:
- `findByEntityAndSliceType(entityType, sliceType)` - 매칭 SinkRule 조회

**파일**: `pkg/sinks/ports/SinkRuleRegistryPort.kt`

---

### 5. ShipWorkflow

**역할**: Slice 조회 → 병합 → Sink 전달

**주요 메서드**:
- `execute(tenantId, entityKey, version, sinkType)`

**파일**: `pkg/orchestration/application/ShipWorkflow.kt`

---

## 📋 Outbox 이벤트 타입

| 이벤트 타입 | AggregateType | 생성 위치 | 처리 위치 |
|-----------|--------------|----------|----------|
| `RawDataIngested` | `RAW_DATA` | IngestWorkflow | OutboxPollingWorker |
| `CompileRequested` | `RAW_DATA` | DeployExecutor | OutboxPollingWorker |
| `ShipRequested` | `SLICE` | DeployExecutor (명시적) 또는 OutboxPollingWorker (자동) | ShipEventHandler |

---

## 🚀 SOTA 포인트

### 1. Zero Config Ship
- SinkRule만 정의하면 `deploy()`만 호출
- 매번 `ship.to { }` 설정 불필요

### 2. Automatic Routing
- `entityType` + `sliceType`으로 자동 라우팅
- Multi-Sink 지원 (하나의 Slice → 여러 Sink)

### 3. Outbox-Only Architecture
- 모든 Ship은 Outbox 경유
- 장애 복구, 재시도, DLQ 지원

### 4. Chained Outbox
- RawDataIngested → Slicing → ShipRequested 자동 생성
- 완전 자동화된 파이프라인

### 5. Tier 1 SOTA Features
- **Visibility Timeout**: Worker 크래시 복구
- **Dead Letter Queue**: 실패 메시지 격리
- **Priority Queue**: 긴급 메시지 우선 처리
- **Entity-Level Ordering**: 버전 순서 보장

---

## 📚 관련 문서

- [SDK 가이드](./sdk-guide.md) - SDK 사용법
- [Sink Data Flow](./sink-data-flow.md) - Sink 전달 상세
- [Slice → Sink Outbox Flow](./slice-to-sink-outbox-flow.md) - Outbox 흐름
- [RFC-IMPL-013](./rfc/rfcimpl013-ship-mandatory.md) - SinkRule 기반 자동 Ship

---

## 💾 실제 데이터베이스 기록 과정

### 1. PostgreSQL: RawData 저장

**테이블 스키마** (`raw_data`):

```sql
CREATE TABLE raw_data (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(64) NOT NULL,
    entity_key      VARCHAR(256) NOT NULL,
    version         BIGINT NOT NULL,
    schema_id       VARCHAR(128) NOT NULL,
    schema_version  VARCHAR(32) NOT NULL,
    content_hash    VARCHAR(64) NOT NULL,  -- SHA256 hex (접두사 제거)
    content         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT raw_data_idempotent_key UNIQUE (tenant_id, entity_key, version)
);
```

**실제 INSERT 쿼리** (`JooqIngestUnitOfWork.kt:123`):

```sql
-- Step 1: 멱등성 검사
SELECT * FROM raw_data
WHERE tenant_id = 'oliveyoung'
  AND entity_key = 'PRODUCT:SKU-001'
  AND version = 1234567890;

-- Step 2: 존재하지 않으면 INSERT
INSERT INTO raw_data (
    id, tenant_id, entity_key, version,
    schema_id, schema_version, content_hash, content
) VALUES (
    '550e8400-e29b-41d4-a716-446655440000',
    'oliveyoung',
    'PRODUCT:SKU-001',
    1234567890,
    'product.v1',
    '1.0.0',
    'a1b2c3d4e5f6...',  -- SHA256 hex (64자, "sha256:" 접두사 제거)
    '{"sku":"SKU-001","name":"비타민C","price":15000}'::jsonb
);
```

**실제 데이터 예시**:

| id | tenant_id | entity_key | version | schema_id | content_hash | content |
|----|-----------|------------|---------|-----------|--------------|---------|
| `550e8400-...` | `oliveyoung` | `PRODUCT:SKU-001` | `1234567890` | `product.v1` | `a1b2c3d4...` | `{"sku":"SKU-001",...}` |

---

### 2. PostgreSQL: Outbox 저장

**테이블 스키마** (`outbox`):

```sql
CREATE TABLE outbox (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    idempotency_key     VARCHAR(256) UNIQUE NOT NULL,
    aggregatetype       VARCHAR(128) NOT NULL,  -- RAW_DATA, SLICE
    aggregateid         VARCHAR(256) NOT NULL,  -- tenant:entity
    type                VARCHAR(128) NOT NULL,  -- RawDataIngested, ShipRequested
    payload             JSONB NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSING, PROCESSED, FAILED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    claimed_at          TIMESTAMPTZ,
    claimed_by          VARCHAR(128),
    retry_count         INT NOT NULL DEFAULT 0,
    priority            INT NOT NULL DEFAULT 100,
    entity_version      BIGINT,
    sequence_num        BIGSERIAL
);
```

**실제 INSERT 쿼리** (`JooqIngestUnitOfWork.kt:151`):

```sql
-- Step 1: 멱등성 검사
SELECT COUNT(*) FROM outbox
WHERE idempotency_key = 'raw_data:oliveyoung:PRODUCT:SKU-001:v1234567890';

-- Step 2: 존재하지 않으면 INSERT
INSERT INTO outbox (
    id, idempotency_key, aggregatetype, aggregateid, type, payload, status, created_at
) VALUES (
    '660e8400-e29b-41d4-a716-446655440000',
    'raw_data:oliveyoung:PRODUCT:SKU-001:v1234567890',
    'RAW_DATA',
    'oliveyoung:PRODUCT:SKU-001',
    'RawDataIngested',
    '{"payloadVersion":"1.0","tenantId":"oliveyoung","entityKey":"PRODUCT:SKU-001","version":1234567890}'::jsonb,
    'PENDING',
    NOW()
);
```

**실제 데이터 예시**:

| id | idempotency_key | aggregatetype | aggregateid | type | status | payload |
|----|----------------|---------------|-------------|------|--------|---------|
| `660e8400-...` | `raw_data:oliveyoung:PRODUCT:SKU-001:v1234567890` | `RAW_DATA` | `oliveyoung:PRODUCT:SKU-001` | `RawDataIngested` | `PENDING` | `{"payloadVersion":"1.0",...}` |

**ShipRequested Outbox 예시**:

```sql
INSERT INTO outbox (
    id, idempotency_key, aggregatetype, aggregateid, type, payload, status
) VALUES (
    '770e8400-e29b-41d4-a716-446655440000',
    'ship:oliveyoung:PRODUCT:SKU-001:v1234567890:opensearch',
    'SLICE',
    'oliveyoung:PRODUCT:SKU-001',
    'ShipRequested',
    '{"payloadVersion":"1.0","tenantId":"oliveyoung","entityKey":"PRODUCT:SKU-001","version":1234567890,"sink":"opensearch","sinkRuleId":"sinkrule.opensearch.product"}'::jsonb,
    'PENDING'
);
```

---

### 3. DynamoDB: Slice 저장

**테이블 구조** (`ivm-lite-data` - Single Table Design):

```
PK (Partition Key): TENANT#{tenantId}#ENTITY#{entityKey}
SK (Sort Key): SLICE#v{version}#{sliceType}
```

**buildPK/buildSK 함수** (`DynamoDbSliceRepository.kt:382`):

```kotlin
private fun buildPK(tenantId: TenantId, entityKey: EntityKey): String =
    "TENANT#${tenantId.value}#ENTITY#${entityKey.value}"

private fun buildSK(version: Long, sliceType: SliceType): String =
    "SLICE#v${version.toString().padStart(10, '0')}#${sliceType.name}"
```

**예시**:
- PK: `TENANT#oliveyoung#ENTITY#PRODUCT:SKU-001`
- SK: `SLICE#v0001234567890#CORE` (버전은 10자리로 zero-padding)

**실제 PUT Item** (`DynamoDbSliceRepository.kt:68`):

```json
{
  "PK": "TENANT#oliveyoung#ENTITY#PRODUCT:SKU-001",
  "SK": "SLICE#v1234567890#CORE",
  "tenant_id": "oliveyoung",
  "entity_key": "PRODUCT:SKU-001",
  "version": 1234567890,
  "slice_type": "CORE",
  "data": "{\"sku\":\"SKU-001\",\"name\":\"비타민C\",\"price\":15000,\"category\":\"건강식품\"}",
  "hash": "sha256:a1b2c3d4e5f6...",
  "rule_set_id": "ruleset.core.v1",
  "rule_set_version": "1.0.0"
}
```

**멱등성 체크** (`DynamoDbSliceRepository.kt:55`):

```kotlin
// 1. 기존 Item 조회
val existing = dynamoClient.getItem {
    it.tableName("ivm-lite-data")
    it.key(mapOf(
        "PK" to "TENANT#oliveyoung#ENTITY#PRODUCT:SKU-001",
        "SK" to "SLICE#v1234567890#CORE"
    ))
}.await()

// 2. 존재하고 hash가 다르면 에러
if (existing != null && existing["hash"] != newSlice.hash) {
    return Result.Err(InvariantViolation("Slice invariant mismatch"))
}

// 3. hash가 같으면 skip (멱등성)
if (existing != null && existing["hash"] == newSlice.hash) {
    return Result.Ok(Unit)  // 이미 존재, skip
}

// 4. 새 Item 저장
dynamoClient.putItem {
    it.tableName("ivm-lite-data")
    it.item(item)
}.await()
```

**실제 DynamoDB Item 예시**:

| PK | SK | tenant_id | entity_key | version | slice_type | data | hash |
|----|----|-----------|------------|--------|------------|------|------|
| `TENANT#oliveyoung#ENTITY#PRODUCT:SKU-001` | `SLICE#v1234567890#CORE` | `oliveyoung` | `PRODUCT:SKU-001` | `1234567890` | `CORE` | `{"sku":"SKU-001",...}` | `sha256:a1b2c3...` |
| `TENANT#oliveyoung#ENTITY#PRODUCT:SKU-001` | `SLICE#v1234567890#DISCOVERY` | `oliveyoung` | `PRODUCT:SKU-001` | `1234567890` | `DISCOVERY` | `{"sku":"SKU-001",...}` | `sha256:b2c3d4...` |

---

### 4. DynamoDB: InvertedIndex 저장

**테이블 구조** (`ivm-lite-data` - Single Table Design):

```
PK (Partition Key): TENANT#{tenantId}#INDEX#{indexType}#{indexValue}
SK (Sort Key): ENTITY#{refEntityKey}#SLICE#{refSliceType}
```

**buildPK/buildSK 함수** (`DynamoDbInvertedIndexRepository.kt`):

```kotlin
private fun buildPK(tenantId: TenantId, indexType: String, indexValue: String): String =
    "TENANT#${tenantId.value}#INDEX#$indexType#$indexValue"

private fun buildSK(refEntityKey: EntityKey, refSliceType: SliceType): String =
    "ENTITY#${refEntityKey.value}#SLICE#${refSliceType.name}"
```

**실제 PUT Item** (`DynamoDbInvertedIndexRepository.kt:49`):

```json
{
  "PK": "TENANT#oliveyoung#INDEX#BRAND#종근당",
  "SK": "ENTITY#PRODUCT:SKU-001#SLICE#CORE",
  "tenant_id": "oliveyoung",
  "ref_entity_key": "BRAND:종근당",
  "ref_version": 100,
  "target_entity_key": "PRODUCT:SKU-001",
  "target_version": 1234567890,
  "index_type": "BRAND",
  "index_value": "종근당",
  "slice_type": "CORE",
  "slice_hash": "sha256:a1b2c3d4...",
  "tombstone": false,
  "created_at": "2026-01-27T10:00:00Z"
}
```

**실제 DynamoDB Item 예시**:

| PK | SK | ref_entity_key | target_entity_key | index_type | index_value | tombstone |
|----|----|---------------|------------------|------------|-------------|-----------|
| `TENANT#oliveyoung#INDEX#BRAND#종근당` | `ENTITY#PRODUCT:SKU-001#SLICE#CORE` | `BRAND:종근당` | `PRODUCT:SKU-001` | `BRAND` | `종근당` | `false` |
| `TENANT#oliveyoung#INDEX#CATEGORY#건강식품` | `ENTITY#PRODUCT:SKU-001#SLICE#CORE` | `CATEGORY:건강식품` | `PRODUCT:SKU-001` | `CATEGORY` | `건강식품` | `false` |

---

## 🔄 전체 기록 흐름 (트랜잭션 포함)

### 시나리오: `ivm.product(product).deploy()` 실행

#### Step 1: Ingest (트랜잭션)

**파일**: `JooqIngestUnitOfWork.kt:89`

```kotlin
dsl.transaction { config ->
    val txDsl = DSL.using(config)
    
    // === Step 1-1: RawData 멱등성 검사 및 저장 ===
    val existing = txDsl.selectFrom(RAW_DATA)
        .where(RAW_TENANT_ID.eq("oliveyoung"))
        .and(RAW_ENTITY_KEY.eq("PRODUCT:SKU-001"))
        .and(RAW_VERSION.eq(1234567890L))
        .fetchOne()
    
    if (existing == null) {
        txDsl.insertInto(RAW_DATA)
            .set(RAW_ID, UUID.randomUUID())
            .set(RAW_TENANT_ID, "oliveyoung")
            .set(RAW_ENTITY_KEY, "PRODUCT:SKU-001")
            .set(RAW_VERSION, 1234567890L)
            .set(RAW_SCHEMA_ID, "product.v1")
            .set(RAW_SCHEMA_VERSION, "1.0.0")
            .set(RAW_CONTENT_HASH, "a1b2c3d4...")  // 접두사 제거
            .set(RAW_CONTENT, JSONB.valueOf(payload))
            .execute()
    }
    
    // === Step 1-2: Outbox 멱등성 검사 및 저장 ===
    val existingOutbox = txDsl.selectCount()
        .from(OUTBOX)
        .where(OUTBOX_IDEMPOTENCY_KEY.eq("raw_data:oliveyoung:PRODUCT:SKU-001:v1234567890"))
        .fetchOne(0, Int::class.java) ?: 0
    
    if (existingOutbox == 0) {
        txDsl.insertInto(OUTBOX)
            .set(OUTBOX_ID, UUID.randomUUID())
            .set(OUTBOX_IDEMPOTENCY_KEY, "raw_data:oliveyoung:PRODUCT:SKU-001:v1234567890")
            .set(OUTBOX_AGGREGATE_TYPE, "RAW_DATA")
            .set(OUTBOX_AGGREGATE_ID, "oliveyoung:PRODUCT:SKU-001")
            .set(OUTBOX_TYPE, "RawDataIngested")
            .set(OUTBOX_PAYLOAD, JSONB.valueOf(payload))
            .set(OUTBOX_STATUS, "PENDING")
            .set(OUTBOX_CREATED_AT, Instant.now())
            .execute()
    }
}
```

**결과**:
- ✅ RawData 저장 (PostgreSQL `raw_data` 테이블)
- ✅ Outbox 저장 (PostgreSQL `outbox` 테이블)
- ✅ **단일 트랜잭션으로 원자성 보장**

---

#### Step 2: Slicing (OutboxPollingWorker)

**파일**: `SlicingWorkflow.kt:85`

```kotlin
// 1. RawData 조회 (PostgreSQL)
val raw = rawRepo.get(tenantId, entityKey, version)

// 2. SlicingEngine으로 Slice 생성
val slicingResult = slicingEngine.slice(raw, ruleSetRef)
// → SliceRecord 리스트 생성

// 3. DynamoDB에 Slice 저장
sliceRepo.putAllIdempotent(slicingResult.slices)
// → 각 Slice마다 DynamoDB PUT Item

// 4. DynamoDB에 InvertedIndex 저장
invertedIndexRepo.putAllIdempotent(slicingResult.indexes)
// → 각 Index마다 DynamoDB PUT Item
```

**실제 DynamoDB 작업**:

```kotlin
// Slice 저장
for (slice in slices) {
    val pk = "TENANT#oliveyoung#ENTITY#PRODUCT:SKU-001"
    val sk = "SLICE#v1234567890#${slice.sliceType.name}"
    
    dynamoClient.putItem {
        it.tableName("ivm-lite-data")
        it.item(mapOf(
            "PK" to pk,
            "SK" to sk,
            "tenant_id" to "oliveyoung",
            "entity_key" to "PRODUCT:SKU-001",
            "version" to 1234567890,
            "slice_type" to slice.sliceType.name,
            "data" to slice.data,
            "hash" to slice.hash,
            ...
        ))
    }.await()
}

// InvertedIndex 저장
for (index in indexes) {
    val pk = "TENANT#oliveyoung#INDEX#${index.indexType}#${index.indexValue}"
    val sk = "ENTITY#${index.refEntityKey.value}#SLICE#${index.refSliceType.name}"
    
    dynamoClient.putItem {
        it.tableName("ivm-lite-data")
        it.item(mapOf(
            "PK" to pk,
            "SK" to sk,
            ...
        ))
    }.await()
}
```

**결과**:
- ✅ Slice 저장 (DynamoDB `ivm-lite-data` 테이블)
- ✅ InvertedIndex 저장 (DynamoDB `ivm-lite-data` 테이블)

---

#### Step 3: 자동 ShipRequested 생성 (OutboxPollingWorker)

**파일**: `OutboxPollingWorker.kt:349`

```kotlin
// 1. SinkRule 조회
val rules = sinkRuleRegistry.findByEntityAndSliceType("PRODUCT", SliceType.CORE)
// → SinkRule 리스트 반환

// 2. 각 SinkRule마다 ShipRequested outbox 생성
for (rule in rules) {
    val shipEntry = OutboxEntry.create(
        aggregateType = AggregateType.SLICE,
        aggregateId = "oliveyoung:PRODUCT:SKU-001",
        eventType = "ShipRequested",
        payload = {
            "tenantId": "oliveyoung",
            "entityKey": "PRODUCT:SKU-001",
            "version": 1234567890,
            "sink": "opensearch",
            "sinkRuleId": rule.id
        }
    )
    outboxRepository.insert(shipEntry)
}
```

**실제 PostgreSQL INSERT**:

```sql
INSERT INTO outbox (
    id, idempotency_key, aggregatetype, aggregateid, type, payload, status
) VALUES (
    '770e8400-e29b-41d4-a716-446655440000',
    'ship:oliveyoung:PRODUCT:SKU-001:v1234567890:opensearch',
    'SLICE',
    'oliveyoung:PRODUCT:SKU-001',
    'ShipRequested',
    '{"payloadVersion":"1.0","tenantId":"oliveyoung","entityKey":"PRODUCT:SKU-001","version":1234567890,"sink":"opensearch","sinkRuleId":"sinkrule.opensearch.product"}'::jsonb,
    'PENDING'
);
```

**결과**:
- ✅ ShipRequested outbox 생성 (PostgreSQL `outbox` 테이블)

---

#### Step 4: Ship 처리 (OutboxPollingWorker)

**파일**: `ShipWorkflow.kt:39`

```kotlin
// 1. DynamoDB에서 Slice 조회
val sliceResult = sliceRepo.getByVersion(tenantId, entityKey, version)
// → Query: PK = "TENANT#oliveyoung#ENTITY#PRODUCT:SKU-001", SK begins_with "SLICE#v1234567890#"

// 2. Slice 병합
val mergedPayload = mergeSlices(sliceResult.value)

// 3. Sink로 전달 (예: OpenSearch)
sink.ship(tenantId, entityKey, version, mergedPayload)
// → HTTP PUT: https://opensearch.example.com/ivm-products-oliveyoung/_doc/PRODUCT:SKU-001
```

**실제 DynamoDB Query**:

```kotlin
dynamoClient.query {
    it.tableName("ivm-lite-data")
    it.keyConditionExpression("PK = :pk AND begins_with(SK, :sk)")
    it.expressionAttributeValues(mapOf(
        ":pk" to "TENANT#oliveyoung#ENTITY#PRODUCT:SKU-001",
        ":sk" to "SLICE#v1234567890#"
    ))
}.await()
```

**결과**:
- ✅ Slice 조회 (DynamoDB)
- ✅ Sink 전달 (OpenSearch/Personalize)

---

## 📊 데이터베이스 상태 변화 요약

| 단계 | PostgreSQL `raw_data` | PostgreSQL `outbox` | DynamoDB `ivm-lite-data` |
|------|----------------------|-------------------|-------------------------|
| **1. Ingest** | ✅ INSERT (RawData) | ✅ INSERT (RawDataIngested) | - |
| **2. Slicing** | - | - | ✅ PUT (Slice Items) |
| **3. Auto Ship** | - | ✅ INSERT (ShipRequested) | ✅ PUT (InvertedIndex Items) |
| **4. Ship** | - | ✅ UPDATE (status = PROCESSED) | - |

---

## ✅ 검증 현황

- **Unit Tests**: 68개 통과
- **E2E Tests**: 17개 통과
- **Stress Tests**: 8개 통과 (1000개 메시지, 20 Workers 동시성)
- **Tier 1 Features**: Visibility Timeout, DLQ, Priority, Ordering 모두 구현

**Status**: Production Ready ✅
