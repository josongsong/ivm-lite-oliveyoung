RFC-IMPL-010 — Gap Closure: v1 스캐폴딩 → v4 Core Business Logic 완성

Status: Draft
Created: 2026-01-25
Scope: RFC-001/002/003 대비 누락된 핵심 비즈니스 로직 + IMPL 누락 항목 마무리
Depends on: RFC-V4-001, RFC-V4-002, RFC-V4-003, RFC-IMPL-001~009
Audience: Runtime Developers / Platform
Non-Goals: CDC/Sink (v4.1), UI, codegen 완전 자동화

---

## 0. Executive Summary

현재 ivm-lite는 **v1 인프라/스캐폴딩이 완료**된 상태이나,
RFC-V4-001/002/003이 정의한 **핵심 비즈니스 로직은 대부분 TODO 상태**이다.

본 RFC는 두 가지를 정의한다:

1. **RFC-IMPL 누락 항목 마무리** (P2, 경미)
2. **RFC-V4 핵심 비즈니스 로직 구현 로드맵** (P0~P1, 핵심)

---

## 1. 현재 상태 분석

### 1-1. 완료된 것 (v1 인프라)

| 영역 | 상태 | 비고 |
|------|------|------|
| Gradle Wrapper + CI | ✅ | checkAll, detekt, ArchUnit |
| Contract YAML 로딩 | ✅ | LocalYamlContractRegistryAdapter |
| IngestWorkflow | ✅ | canonicalize + hash + Outbox |
| SlicingWorkflow | ⚠️ | **raw → CORE 복사만** (RuleSet 미적용) |
| QueryViewWorkflow | ⚠️ | **fail-closed만** (ViewDefinition 없음) |
| Outbox + PollingWorker | ✅ | Transactional Outbox 패턴 |
| DynamoDB Adapter | ⚠️ | 로딩만 (캐싱/checksum 없음) |
| Health/Readiness | ⚠️ | 하드코딩 (동적 wiring 없음) |

### 1-2. 누락된 것 (핵심 비즈니스)

| RFC | 항목 | 현재 상태 | 심각도 |
|-----|------|----------|--------|
| RFC-001/003 | SliceRecord.tombstone | ❌ 필드 없음 | 🔴 P0 |
| RFC-001/003 | JoinSpec 실행 | ❌ 도메인만 (실행 없음) | 🔴 P0 |
| RFC-001 | Inverted Index 생성 | ❌ 포트만 (빌더 없음) | 🟡 P1 |
| RFC-001/003 | ImpactMap 계산 | ❌ 필드만 (계산 없음) | 🟡 P1 |
| RFC-001 | INCREMENTAL slicing | ❌ FULL만 | 🟡 P1 |
| RFC-003 | ViewDefinition | ❌ 도메인 없음 | 🟡 P1 |
| RFC-003 | MissingPolicy/PartialPolicy | ❌ fail-closed만 | 🟡 P1 |
| RFC-003 | ContractStatusGate | ❌ 없음 | 🟡 P1 |
| RFC-001/003 | RuleSet 로딩/실행 | ❌ 하드코딩 고정값 | 🔴 P0 |
| IMPL-007 | DynamoDB 캐싱 | ❌ 없음 | 🟡 P2 |
| IMPL-007 | DynamoDB checksum | ❌ 없음 | 🟡 P2 |
| IMPL-009 | Readiness 동적 wiring | ❌ 하드코딩 | 🟢 P3 |

---

## 2. Phase C: RFC-IMPL 마무리 (경미한 누락)

### C-1. DynamoDB 캐싱 (IMPL-007)

**현재**: `DynamoDBContractRegistryAdapter`에 캐싱 없음

**목표**: RFC-IMPL-007 요구사항 충족

```kotlin
class DynamoDBContractRegistryAdapter(
    private val dynamoClient: DynamoDbAsyncClient,
    private val tableName: String,
    private val cache: ContractCache,  // 추가
    private val config: CacheConfig,   // 추가
) : ContractRegistryPort {

    override suspend fun loadChangeSetContract(ref: ContractRef): Result<ChangeSetContract> {
        // 1. 캐시 확인
        cache.get(ref)?.let { return Result.Ok(it) }
        
        // 2. DynamoDB 조회
        val contract = fetchFromDynamoDB(ref)
        
        // 3. 캐시 저장
        cache.put(ref, contract, config.ttl)
        
        return Result.Ok(contract)
    }
}

data class CacheConfig(
    val ttl: Duration = 5.minutes,
    val maxSize: Int = 1000,
)
```

**Acceptance Criteria**:
- [ ] `ContractCache` 인터페이스 정의
- [ ] `InMemoryContractCache` 구현 (LRU + TTL)
- [ ] 캐시 hit 시 DynamoDB 호출 없음 테스트
- [ ] TTL 만료 후 재조회 테스트

---

### C-2. DynamoDB checksum 검증 (IMPL-007)

**현재**: checksum 필드 무시

**목표**: 무결성 검증 추가

```kotlin
private fun verifyChecksum(item: Map<String, AttributeValue>, ref: ContractRef): Result<Unit> {
    val storedChecksum = item["checksum"]?.s()
    val data = item["data"]?.s() ?: return err("missing data")
    
    val computedChecksum = "sha256:" + Hashing.sha256Hex(data)
    
    return if (storedChecksum == computedChecksum) {
        Result.Ok(Unit)
    } else {
        Result.Err(DomainError.ContractIntegrityError(
            ref = ref,
            expected = storedChecksum,
            actual = computedChecksum
        ))
    }
}
```

**Acceptance Criteria**:
- [ ] `DomainError.ContractIntegrityError` 추가
- [ ] checksum 불일치 시 Err 반환 테스트
- [ ] checksum 누락 시 경고 로그 (fail-open for migration)

---

### C-3. Readiness 동적 wiring (IMPL-009 P2)

**현재**: 하드코딩된 true

**목표**: 실제 어댑터 상태 기반

```kotlin
interface HealthCheckable {
    val name: String
    suspend fun healthCheck(): Boolean
}

fun Route.readinessRoutes(
    healthCheckables: List<HealthCheckable>,  // Koin에서 주입
) {
    get("/ready") {
        val checks = healthCheckables.associate { 
            it.name to runCatching { it.healthCheck() }.getOrDefault(false)
        }
        
        val allHealthy = checks.values.all { it }
        val status = if (allHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        
        call.respond(status, ReadinessResponse(
            status = if (allHealthy) "UP" else "DOWN",
            checks = checks,
        ))
    }
}
```

**Acceptance Criteria**:
- [ ] `HealthCheckable` 인터페이스
- [ ] 각 어댑터에 `HealthCheckable` 구현
- [ ] Koin에서 `List<HealthCheckable>` 주입
- [ ] 어댑터 장애 시 /ready DOWN 테스트

---

## 3. Phase D: Core Business Logic 구현 (핵심)

### D-1. SliceRecord.tombstone 추가 (P0)

**RFC-001 요구사항**: "증분 업데이트 시 삭제된 결과를 표현하기 위한 논리적 삭제 플래그"

**RFC-003 요구사항**: "모든 Slice 타입에 tombstone 필드 필수"

```kotlin
data class SliceRecord(
    val tenantId: TenantId,
    val entityKey: EntityKey,
    val version: Long,
    val sliceType: SliceType,
    val data: String,
    val hash: String,
    val ruleSetId: String,
    val ruleSetVersion: SemVer,
    // 추가
    val tombstone: Tombstone? = null,
)

data class Tombstone(
    val isDeleted: Boolean,
    val deletedAtVersion: Long?,
    val deleteReason: DeleteReason?,
)

enum class DeleteReason {
    USER_DELETE,
    POLICY_HIDE,
    VALIDATION_FAIL,
    ARCHIVED,
}
```

**Migration**:
- DB: `ALTER TABLE slices ADD COLUMN tombstone JSONB NULL`
- 기존 데이터: `tombstone = null` (삭제되지 않음)

**Acceptance Criteria**:
- [ ] SliceRecord.tombstone 필드 추가
- [ ] Flyway migration V008
- [ ] QueryViewWorkflow에서 tombstone 필터링
- [ ] INCREMENTAL slicing에서 tombstone 생성

---

### D-2. RuleSet 도메인 + 로딩 (P0)

**현재**: 하드코딩된 `V1_RULESET_ID = "ruleset.core.v1"`

**목표**: RuleSet 계약에서 로딩 + 실행

```kotlin
// 도메인 모델
data class RuleSetContract(
    val meta: ContractMeta,
    val entityType: String,
    val sliceKeySpec: SliceKeySpec,
    val impactMap: Map<String, List<String>>,  // sliceType → paths
    val joins: List<JoinSpec>,
    val slices: List<SliceDefinition>,
)

data class SliceDefinition(
    val sliceType: SliceType,
    val outputSchemaRef: ContractRef?,
    val buildRules: SliceBuildRules,
)

sealed class SliceBuildRules {
    data class MapFields(val mappings: Map<String, String>) : SliceBuildRules()
    data class PassThrough(val fields: List<String>) : SliceBuildRules()
}
```

**Port 확장**:
```kotlin
interface ContractRegistryPort {
    // 기존
    suspend fun loadChangeSetContract(ref: ContractRef): Result<ChangeSetContract>
    suspend fun loadJoinSpecContract(ref: ContractRef): Result<JoinSpecContract>
    suspend fun loadInvertedIndexContract(ref: ContractRef): Result<InvertedIndexContract>
    // 추가
    suspend fun loadRuleSetContract(ref: ContractRef): Result<RuleSetContract>
}
```

**YAML 추가**: `src/main/resources/contracts/v1/ruleset.v1.yaml`

**Acceptance Criteria**:
- [ ] RuleSetContract 도메인 모델
- [ ] ContractRegistryPort.loadRuleSetContract
- [ ] LocalYamlContractRegistryAdapter 구현
- [ ] DynamoDBContractRegistryAdapter 구현
- [ ] ruleset.v1.yaml 생성

---

### D-3. SlicingEngine + RuleSet 실행 (P0)

**현재**: SlicingWorkflow가 raw payload를 그대로 CORE로 복사

**목표**: RuleSet 기반 슬라이싱

```kotlin
class SlicingEngine(
    private val contractRegistry: ContractRegistryPort,
) {
    suspend fun slice(
        rawData: RawDataRecord,
        ruleSetRef: ContractRef,
    ): Result<List<SliceRecord>> {
        // 1. RuleSet 로드
        val ruleSet = contractRegistry.loadRuleSetContract(ruleSetRef)
            .getOrElse { return Result.Err(it) }
        
        // 2. 각 SliceDefinition 처리
        val slices = ruleSet.slices.map { def ->
            buildSlice(rawData, def, ruleSet)
        }
        
        return Result.Ok(slices)
    }
    
    private fun buildSlice(
        rawData: RawDataRecord,
        def: SliceDefinition,
        ruleSet: RuleSetContract,
    ): SliceRecord {
        val data = when (val rules = def.buildRules) {
            is SliceBuildRules.MapFields -> applyFieldMappings(rawData.payload, rules.mappings)
            is SliceBuildRules.PassThrough -> extractFields(rawData.payload, rules.fields)
        }
        
        val canonical = CanonicalJson.canonicalize(data)
        val hash = Hashing.sha256Tagged(canonical)
        
        return SliceRecord(
            tenantId = rawData.tenantId,
            entityKey = rawData.entityKey,
            version = rawData.version,
            sliceType = def.sliceType,
            data = canonical,
            hash = hash,
            ruleSetId = ruleSet.meta.id,
            ruleSetVersion = ruleSet.meta.version,
        )
    }
}
```

**SlicingWorkflow 수정**:
```kotlin
class SlicingWorkflow(
    private val rawRepo: RawDataRepositoryPort,
    private val sliceRepo: SliceRepositoryPort,
    private val slicingEngine: SlicingEngine,  // 추가
    private val defaultRuleSetRef: ContractRef, // config에서 주입
) {
    suspend fun execute(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        ruleSetRef: ContractRef? = null,  // 선택적 override
    ): Result<List<SliceRepositoryPort.SliceKey>> {
        val raw = rawRepo.get(tenantId, entityKey, version)
            .getOrElse { return Result.Err(it) }
        
        val ref = ruleSetRef ?: defaultRuleSetRef
        val slices = slicingEngine.slice(raw, ref)
            .getOrElse { return Result.Err(it) }
        
        sliceRepo.putAllIdempotent(slices)
            .getOrElse { return Result.Err(it) }
        
        return Result.Ok(slices.map { it.toKey() })
    }
}
```

**Acceptance Criteria**:
- [ ] SlicingEngine 도메인 서비스
- [ ] RuleSet 기반 슬라이싱 테스트
- [ ] 동일 입력 → 동일 결과 (결정성 테스트)

---

### D-4. JoinSpec 실행 (P0)

**현재**: JoinSpec 도메인만 존재, 실행 없음

**목표**: Light JOIN 실행

```kotlin
class JoinExecutor(
    private val rawRepo: RawDataRepositoryPort,
    private val sliceRepo: SliceRepositoryPort,
) {
    suspend fun executeJoin(
        rawData: RawDataRecord,
        joinSpec: JoinSpec,
    ): Result<Map<String, Any?>> {
        // 1. 소스 필드에서 타겟 키 추출
        val sourceValue = extractField(rawData.payload, joinSpec.sourceFieldPath)
            ?: return if (joinSpec.required) {
                Result.Err(DomainError.JoinError("required join source missing"))
            } else {
                Result.Ok(emptyMap())
            }
        
        // 2. 타겟 키 생성
        val targetKey = interpolateKey(joinSpec.targetKeyPattern, sourceValue, rawData.tenantId)
        
        // 3. 타겟 조회 (RawData 또는 Slice)
        val targetData = rawRepo.getLatest(rawData.tenantId, EntityKey(targetKey))
            .getOrElse { 
                return if (joinSpec.required) Result.Err(it) else Result.Ok(emptyMap())
            }
        
        // 4. 결과 추출
        return Result.Ok(mapOf(
            joinSpec.name to targetData.payload
        ))
    }
}
```

**SlicingEngine에 통합**:
```kotlin
class SlicingEngine(
    private val contractRegistry: ContractRegistryPort,
    private val joinExecutor: JoinExecutor,  // 추가
) {
    suspend fun slice(rawData: RawDataRecord, ruleSetRef: ContractRef): Result<List<SliceRecord>> {
        val ruleSet = contractRegistry.loadRuleSetContract(ruleSetRef).getOrElse { return Result.Err(it) }
        
        // JOIN 실행
        val joinResults = mutableMapOf<String, Any?>()
        for (join in ruleSet.joins) {
            val result = joinExecutor.executeJoin(rawData, join)
                .getOrElse { return Result.Err(it) }
            joinResults.putAll(result)
        }
        
        // Slice 생성 (JOIN 결과 포함)
        val enrichedPayload = mergePayloads(rawData.payload, joinResults)
        // ...
    }
}
```

**Acceptance Criteria**:
- [ ] JoinExecutor 도메인 서비스
- [ ] LOOKUP join 실행 테스트
- [ ] required=true 시 실패 테스트
- [ ] required=false 시 빈값 테스트

---

### D-5. ViewDefinition + MissingPolicy/PartialPolicy (P1)

**RFC-003 요구사항**: ViewDefinition 계약으로 조회 정책 정의

```kotlin
data class ViewDefinitionContract(
    val meta: ContractMeta,
    val requiredSlices: List<SliceType>,
    val optionalSlices: List<SliceType>,
    val missingPolicy: MissingPolicy,
    val partialPolicy: PartialPolicy,
    val fallbackPolicy: FallbackPolicy,
    val ruleSetRef: ContractRef,
)

enum class MissingPolicy {
    FAIL_CLOSED,
    PARTIAL_ALLOWED,
}

data class PartialPolicy(
    val allowed: Boolean,
    val optionalOnly: Boolean,
    val responseMeta: ResponseMeta,
)

data class ResponseMeta(
    val includeMissingSlices: Boolean,
    val includeUsedContracts: Boolean,
)

enum class FallbackPolicy {
    NONE,
    DEFAULT_VALUE,
}
```

**QueryViewWorkflow 수정**:
```kotlin
class QueryViewWorkflow(
    private val sliceRepo: SliceRepositoryPort,
    private val contractRegistry: ContractRegistryPort,  // 추가
) {
    suspend fun execute(
        tenantId: TenantId,
        viewRef: ContractRef,  // viewId → viewRef로 변경
        entityKey: EntityKey,
        version: Long,
    ): Result<ViewResponse> {
        // 1. ViewDefinition 로드
        val viewDef = contractRegistry.loadViewDefinitionContract(viewRef)
            .getOrElse { return Result.Err(it) }
        
        // 2. 필요한 Slice 조회
        val allTypes = viewDef.requiredSlices + viewDef.optionalSlices
        val slices = sliceRepo.batchGet(tenantId, allTypes.map { ... })
        
        // 3. MissingPolicy 적용
        val gotTypes = slices.map { it.sliceType }.toSet()
        val missingRequired = viewDef.requiredSlices.filter { it !in gotTypes }
        val missingOptional = viewDef.optionalSlices.filter { it !in gotTypes }
        
        when (viewDef.missingPolicy) {
            MissingPolicy.FAIL_CLOSED -> {
                if (missingRequired.isNotEmpty()) {
                    return Result.Err(DomainError.MissingSliceError(missingRequired))
                }
            }
            MissingPolicy.PARTIAL_ALLOWED -> {
                if (!viewDef.partialPolicy.allowed) {
                    if (missingRequired.isNotEmpty()) {
                        return Result.Err(DomainError.MissingSliceError(missingRequired))
                    }
                }
            }
        }
        
        // 4. 응답 생성
        val meta = if (viewDef.partialPolicy.responseMeta.includeMissingSlices) {
            ViewMeta(missingSlices = missingRequired + missingOptional)
        } else null
        
        return Result.Ok(ViewResponse(data = ..., meta = meta))
    }
}
```

**Acceptance Criteria**:
- [ ] ViewDefinitionContract 도메인 모델
- [ ] ContractRegistryPort.loadViewDefinitionContract
- [ ] view-definition.v1.yaml 생성
- [ ] MissingPolicy.FAIL_CLOSED 테스트
- [ ] MissingPolicy.PARTIAL_ALLOWED 테스트
- [ ] ResponseMeta 포함 테스트

---

### D-6. ContractStatusGate (P1)

**RFC-003 요구사항**: 계약 상태(DRAFT/ACTIVE/DEPRECATED/ARCHIVED) 검증 필수

```kotlin
interface ContractStatusGate {
    fun allow(status: ContractStatus): Result<Unit>
}

object DefaultContractStatusGate : ContractStatusGate {
    override fun allow(status: ContractStatus): Result<Unit> = when (status) {
        ContractStatus.ACTIVE -> Result.Ok(Unit)
        ContractStatus.DEPRECATED -> {
            logger.warn { "Using DEPRECATED contract" }
            Result.Ok(Unit)  // 경고 후 허용
        }
        ContractStatus.DRAFT -> Result.Err(DomainError.ContractStatusError("DRAFT not allowed in production"))
        ContractStatus.ARCHIVED -> Result.Err(DomainError.ContractStatusError("ARCHIVED contracts are blocked"))
    }
}
```

**ContractRegistryPort 통합**:
```kotlin
class GatedContractRegistryAdapter(
    private val delegate: ContractRegistryPort,
    private val statusGate: ContractStatusGate,
) : ContractRegistryPort {
    
    override suspend fun loadChangeSetContract(ref: ContractRef): Result<ChangeSetContract> {
        val contract = delegate.loadChangeSetContract(ref)
            .getOrElse { return Result.Err(it) }
        
        statusGate.allow(contract.meta.status)
            .getOrElse { return Result.Err(it) }
        
        return Result.Ok(contract)
    }
    // ... 다른 메서드도 동일
}
```

**Acceptance Criteria**:
- [ ] ContractStatusGate 인터페이스
- [ ] DefaultContractStatusGate 구현
- [ ] DRAFT 차단 테스트
- [ ] ARCHIVED 차단 테스트
- [ ] DEPRECATED 경고 로그 테스트

---

### D-7. ImpactMap 계산 (P1)

**RFC-001/003 요구사항**: ChangeSet → ImpactedSliceTypes 계산

```kotlin
class ImpactCalculator {
    fun calculate(
        changeSet: ChangeSet,
        ruleSet: RuleSetContract,
    ): Map<String, ImpactDetail> {
        val result = mutableMapOf<String, ImpactDetail>()
        
        for ((sliceType, impactPaths) in ruleSet.impactMap) {
            val matchedPaths = changeSet.changedPaths
                .filter { changed -> impactPaths.any { changed.path.startsWith(it) } }
            
            if (matchedPaths.isNotEmpty()) {
                result[sliceType] = ImpactDetail(
                    reason = "FIELD_CHANGE",
                    paths = matchedPaths.map { it.path },
                )
            }
        }
        
        // fail-closed: 매칭 안 된 변경 경로가 있으면 에러
        val allImpactPaths = ruleSet.impactMap.values.flatten().toSet()
        val unmatchedPaths = changeSet.changedPaths
            .filter { changed -> allImpactPaths.none { changed.path.startsWith(it) } }
        
        if (unmatchedPaths.isNotEmpty()) {
            throw DomainError.UnmappedChangePathError(unmatchedPaths.map { it.path })
        }
        
        return result
    }
}
```

**Acceptance Criteria**:
- [ ] ImpactCalculator 도메인 서비스
- [ ] RuleSet.impactMap 기반 계산 테스트
- [ ] unmapped path → fail-closed 테스트

---

### D-8. INCREMENTAL Slicing (P1)

**RFC-001 요구사항**: FULL_REBUILD == INCREMENTAL 결과

```kotlin
class SlicingWorkflow(
    private val slicingEngine: SlicingEngine,
    private val changeSetBuilder: ChangeSetBuilder,
    private val impactCalculator: ImpactCalculator,
) {
    suspend fun executeIncremental(
        tenantId: TenantId,
        entityKey: EntityKey,
        fromVersion: Long,
        toVersion: Long,
        ruleSetRef: ContractRef,
    ): Result<List<SliceRecord>> {
        // 1. RawData 로드
        val fromRaw = rawRepo.get(tenantId, entityKey, fromVersion).getOrNull()
        val toRaw = rawRepo.get(tenantId, entityKey, toVersion)
            .getOrElse { return Result.Err(it) }
        
        // 2. RuleSet 로드
        val ruleSet = contractRegistry.loadRuleSetContract(ruleSetRef)
            .getOrElse { return Result.Err(it) }
        
        // 3. ChangeSet 생성
        val changeSet = changeSetBuilder.build(
            tenantId = tenantId,
            entityType = ruleSet.entityType,
            entityKey = entityKey,
            fromVersion = fromVersion,
            toVersion = toVersion,
            fromPayload = fromRaw?.payload,
            toPayload = toRaw.payload,
            impactedSliceTypes = emptySet(),  // 아래에서 계산
            impactMap = emptyMap(),
        )
        
        // 4. ImpactMap 계산
        val impactMap = impactCalculator.calculate(changeSet, ruleSet)
        val impactedTypes = impactMap.keys.map { SliceType.valueOf(it) }
        
        // 5. 영향받는 Slice만 재생성
        val slices = slicingEngine.slicePartial(toRaw, ruleSetRef, impactedTypes)
            .getOrElse { return Result.Err(it) }
        
        // 6. 기존 Slice 중 영향 없는 것은 유지, 결과 0건인 것은 tombstone
        // ...
        
        return Result.Ok(slices)
    }
}
```

**Acceptance Criteria**:
- [ ] executeIncremental 구현
- [ ] FULL vs INCREMENTAL 결과 동치 테스트
- [ ] tombstone 생성 테스트

---

### D-9. Inverted Index 빌더 (P1)

**RFC-001 요구사항**: Slice 생성 시 Inverted Index 동시 생성

```kotlin
class InvertedIndexBuilder {
    fun build(
        slice: SliceRecord,
        indexSpecs: List<IndexSpec>,
    ): List<InvertedIndexEntry> {
        return indexSpecs.flatMap { spec ->
            val values = extractValues(slice.data, spec.selector)
            values.map { value ->
                InvertedIndexEntry(
                    tenantId = slice.tenantId,
                    refEntityKey = slice.entityKey,
                    refVersion = VersionLong(slice.version),
                    targetEntityKey = slice.entityKey,  // self-reference for now
                    targetVersion = VersionLong(slice.version),
                    indexType = spec.type,
                    indexValue = canonicalizeValue(value),
                    sliceType = slice.sliceType,
                    sliceHash = slice.hash,
                    tombstone = false,
                )
            }
        }
    }
}
```

**Acceptance Criteria**:
- [ ] InvertedIndexBuilder 도메인 서비스
- [ ] SlicingEngine에 통합
- [ ] Index 생성 결정성 테스트

---

## 4. 구현 로드맵

```
Phase C: RFC-IMPL 마무리 (1주)
├── C-1: DynamoDB 캐싱
├── C-2: DynamoDB checksum
└── C-3: Readiness 동적 wiring

Phase D: Core Business Logic (3주)
├── D-1: SliceRecord.tombstone (P0)
├── D-2: RuleSet 도메인 + 로딩 (P0)
├── D-3: SlicingEngine + RuleSet 실행 (P0)
├── D-4: JoinSpec 실행 (P0)
├── D-5: ViewDefinition + Policy (P1)
├── D-6: ContractStatusGate (P1)
├── D-7: ImpactMap 계산 (P1)
├── D-8: INCREMENTAL slicing (P1)
└── D-9: Inverted Index 빌더 (P1)
```

### 의존성
```
D-2 (RuleSet) → D-3 (SlicingEngine) → D-4 (JoinSpec)
                                    → D-7 (ImpactMap) → D-8 (INCREMENTAL)
                                    → D-9 (InvertedIndex)
D-5 (ViewDefinition) ← D-6 (StatusGate)
D-1 (tombstone) ← D-8 (INCREMENTAL)
```

---

## 5. Acceptance Criteria (전체)

### Phase C 완료 조건
- [ ] DynamoDB 캐시 hit 시 호출 없음
- [ ] checksum 불일치 시 ContractIntegrityError
- [ ] /ready가 실제 어댑터 상태 반영

### Phase D 완료 조건
- [ ] SliceRecord.tombstone 필드 존재
- [ ] RuleSet 계약에서 슬라이싱 규칙 로드
- [ ] JoinSpec 실행 (LOOKUP)
- [ ] ViewDefinition 기반 조회 정책
- [ ] ACTIVE만 기본 허용, DRAFT/ARCHIVED 차단
- [ ] FULL == INCREMENTAL 동치 테스트 통과
- [ ] Inverted Index 동시 생성

---

## 6. 테스트 전략

### 단위 테스트 (MockK)
- SlicingEngine
- JoinExecutor
- ImpactCalculator
- InvertedIndexBuilder
- ContractStatusGate

### 통합 테스트 (Testcontainers)
- 전체 Ingest → Slicing → Query 플로우
- FULL vs INCREMENTAL 동치
- 캐싱 동작

### 속성 테스트 (Kotest)
- 결정성: 동일 입력 → 동일 결과
- 멱등성: 재실행 시 부작용 없음

---

## 7. Migration

### DB Migration
- V008: `ALTER TABLE slices ADD COLUMN tombstone JSONB NULL`

### Contract Migration
- `ruleset.v1.yaml` 추가
- `view-definition.v1.yaml` 추가

### Config Migration
- `defaultRuleSetRef` 설정 추가
- `cache.ttl` 설정 추가

---

## 8. Rollback Plan

- Phase C: 캐싱/checksum 비활성화 플래그
- Phase D: 기존 하드코딩 로직 유지, 피처 플래그로 전환

---

## 9. Summary

| Phase | 범위 | 소요 |
|-------|------|------|
| C | RFC-IMPL 마무리 (3개) | 1주 |
| D | Core Business Logic (9개) | 3주 |

**한 줄 요약**: v1 인프라 완료 → RFC-V4 핵심 비즈니스 로직으로 "진짜 ivm-lite" 완성
