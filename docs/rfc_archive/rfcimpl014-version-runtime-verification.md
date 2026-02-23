# RFC-IMPL-014: Contract Version Management — Runtime Verification & Auto-Migration

**Status**: Draft
**Created**: 2026-01-29
**Scope**: Contract 버전 불일치 감지 및 자동 마이그레이션 구현
**Parent RFC**: [RFC-012](./rfc012-contract-versioning-strategy.md)
**Related ADR**: [ADR-0011](../adr/0011-contract-versioning-strategy.md)

---

## 0. Executive Summary

**현재 문제:**
- RFC-012/ADR-0011에 버전 관리 전략은 정의됨
- **하지만 런타임 검증 로직 없음** → 버전 불일치 슬라이스 그대로 사용 (버그!)
- 마이그레이션 도구 미구현 → 수동 재생성 필요

**해결책:**
1. **Content-Addressed Versioning**: Slice에 의존성 fingerprint 추가
2. **Runtime Verification**: 조회 시 자동 버전 불일치 감지
3. **Automated Invalidation**: Contract 변경 → Outbox 이벤트로 무효화
4. **Migration CLI**: 배치 단위 점진적 재생성 도구
5. **Lazy Migration**: 백그라운드 재생성 + Graceful degradation

**참고 시스템:**
- Netflix Hollow (Schema Evolution)
- LinkedIn Espresso (Multi-version support)
- Avro/Protobuf (Compatibility Matrix)

---

## 1. 문제 정의

### 1-1. 현재 시스템의 취약점

```kotlin
// SliceRecord.kt (현재)
data class SliceRecord(
    // ...
    val ruleSetId: String,
    val ruleSetVersion: SemVer,  // ✅ 버전 저장됨
)

// QueryViewWorkflow.kt (현재)
val slices = sliceRepo.getByVersion(tenantId, entityKey, version)
// ❌ 버전 검증 없음! → v1 슬라이스인데 v2 RuleSet으로 조회 가능
```

**시나리오: 버그 발생 과정**

```
1. RuleSet v1.0.0로 슬라이스 생성 (CORE, SEARCH)
2. RuleSet v2.0.0 배포 (CORE 삭제, SEARCH 로직 변경)
3. 기존 슬라이스 조회 → ❌ v1 슬라이스를 v2로 해석 (데이터 오염!)
```

### 1-2. 수학적 정합성 부족

| 시나리오 | 현재 처리 | 올바른 처리 |
|---------|----------|------------|
| RuleSet v1→v2 | 슬라이스 그대로 사용 | 탐지 + 재생성 트리거 |
| ViewDef 버전 변경 | 하드코딩 | 동적 버전 선택 |
| 부분 배포 (RuleSet v2, ViewDef v1) | 정합성 보장 없음 | 의존성 체크 |
| 슬라이스 버전 ≠ RuleSet 버전 | 에러 없음 | fail-fast 또는 재생성 |

---

## 2. 솔루션 아키텍처

### 2-1. Content-Addressed Versioning

**핵심 아이디어**: Git처럼 전체 의존성 체인의 content hash 추적

```kotlin
// SliceRecord.kt (NEW!)
data class SliceRecord(
    // 기존 필드들...
    val ruleSetId: String,
    val ruleSetVersion: SemVer,

    /**
     * RFC-IMPL-014: Dependency fingerprint
     *
     * 전체 의존성 체인의 SHA256 해시:
     * sha256(ruleSetId + version + contract checksum)
     *
     * 예: sha256("ruleset.product.v1|2.0.0|sha256:abc123...")
     */
    val dependencyFingerprint: String,
)
```

**계산 로직:**

```kotlin
// SlicingEngine.kt
suspend fun slice(
    rawData: RawDataRecord,
    ruleSetRef: ContractRef,
): Result<SlicingResult> {
    val ruleSet = contractRegistry.loadRuleSetContract(ruleSetRef)

    // ✨ Fingerprint 계산
    val contractChecksum = getContractChecksum(ruleSet.meta.id, ruleSet.meta.version)
    val fingerprint = Hashing.sha256Hex(
        "${ruleSet.meta.id}|${ruleSet.meta.version}|$contractChecksum"
    )

    val slices = buildSlices(rawData, ruleSet, fingerprint)
    // ...
}

private fun buildSlice(..., fingerprint: String): SliceRecord {
    return SliceRecord(
        // ...
        ruleSetId = ruleSet.meta.id,
        ruleSetVersion = ruleSet.meta.version,
        dependencyFingerprint = fingerprint,  // ✨ NEW
    )
}
```

**DynamoDB 스키마 변경:**

```kotlin
// JooqSliceRepository.kt
// ALTER TABLE slices ADD COLUMN dependency_fingerprint VARCHAR(128);
```

---

### 2-2. Runtime Verification

**조회 시 자동 검증:**

```kotlin
// QueryViewWorkflow.kt (확장)
suspend fun execute(...): Result<ViewResponse> {
    val viewDef = contractRegistry.loadViewDefinitionContract(viewRef)
    val currentRuleSet = contractRegistry.loadRuleSetContract(viewDef.ruleSetRef)

    // ✨ 1. 현재 기대하는 fingerprint 계산
    val expectedFingerprint = computeFingerprint(currentRuleSet)

    // ✨ 2. 저장된 슬라이스 조회
    val slices = sliceRepo.getByVersion(tenantId, entityKey, version)

    // ✨ 3. 버전 불일치 감지
    val mismatchedSlices = slices.filter {
        it.dependencyFingerprint != expectedFingerprint
    }

    if (mismatchedSlices.isNotEmpty()) {
        // ✨ 4. Lazy Migration 트리거 (백그라운드)
        triggerLazyMigration(tenantId, entityKey, version, viewDef.ruleSetRef)

        // ✨ 5. Graceful Degradation (PartialPolicy 활용)
        return when (viewDef.partialPolicy.allowStale) {
            true -> {
                // 경고와 함께 이전 버전 반환
                Result.Ok(
                    ViewResponse(
                        data = buildView(slices),
                        meta = ViewMeta(
                            warning = "Stale data: migration in progress",
                            staleDependencies = mismatchedSlices.map { it.sliceType.name }
                        )
                    )
                )
            }
            false -> {
                // fail-closed
                Result.Err(
                    DomainError.StaleDependencyError(
                        sliceTypes = mismatchedSlices.map { it.sliceType },
                        expected = expectedFingerprint,
                        actual = mismatchedSlices.first().dependencyFingerprint,
                        action = "Trigger migration: ./gradlew migrate --ruleset=${viewDef.ruleSetRef.id}"
                    )
                )
            }
        }
    }

    // ✅ 정합성 OK
    return Result.Ok(buildView(slices))
}

private fun computeFingerprint(ruleSet: RuleSetContract): String {
    val checksum = getContractChecksum(ruleSet.meta.id, ruleSet.meta.version)
    return Hashing.sha256Hex("${ruleSet.meta.id}|${ruleSet.meta.version}|$checksum")
}
```

**새 DomainError:**

```kotlin
// DomainError.kt
sealed class DomainError {
    // ...

    /**
     * RFC-IMPL-014: Stale dependency 에러
     *
     * 슬라이스의 의존성 버전이 현재 Contract 버전과 불일치
     */
    data class StaleDependencyError(
        val sliceTypes: List<SliceType>,
        val expected: String,  // 기대 fingerprint
        val actual: String,    // 실제 fingerprint
        val action: String,    // 권장 조치
    ) : DomainError()
}
```

---

### 2-3. Compatibility Matrix Parsing

**YAML 확장 (RFC-012 구현):**

```yaml
# ruleset-product.v2.yaml
meta:
  id: ruleset.product.v1
  version: 2.0.0
  status: ACTIVE

# ✨ Compatibility Matrix (RFC-012 Section 4-1)
compatibility:
  - fromVersion: "1.0.0"
    toVersion: "2.0.0"
    compatible: false
    breakingChanges:
      - type: SLICE_REMOVED
        sliceType: CORE
        reason: "CORE merged into SEARCH"
      - type: BUILD_LOGIC_CHANGED
        sliceType: SEARCH
        fieldPath: "brandId"
        oldMapping: "brand.id"
        newMapping: "brandRef.id"
    migrationStrategy: FULL_REBUILD  # or INCREMENTAL
    estimatedImpact:
      affectedSlices: ["CORE", "SEARCH"]
      rebuildRequired: true

slices:
  - type: SEARCH
    buildRules:
      type: mapfields
      mappings:
        brandId: brandRef.id  # ✨ 변경됨
```

**Contract 도메인 확장:**

```kotlin
// Contracts.kt
data class RuleSetContract(
    val meta: ContractMeta,
    val entityType: String,
    val slices: List<SliceDefinition>,
    val indexes: List<IndexSpec>,
    val joins: List<JoinSpec>,
    val impactMap: Map<SliceType, List<String>>,

    /**
     * RFC-IMPL-014: Compatibility matrix
     *
     * 버전 간 호환성 정보 (RFC-012 Section 4-1)
     */
    val compatibility: List<CompatibilityEntry> = emptyList(),
)

/**
 * 버전 호환성 메타데이터
 */
data class CompatibilityEntry(
    val fromVersion: SemVer,
    val toVersion: SemVer,
    val compatible: Boolean,
    val breakingChanges: List<BreakingChange>,
    val migrationStrategy: MigrationStrategy,
    val estimatedImpact: ImpactEstimate?,
)

data class BreakingChange(
    val type: BreakingChangeType,
    val sliceType: SliceType? = null,
    val fieldPath: String? = null,
    val reason: String,
    val oldValue: String? = null,
    val newValue: String? = null,
)

enum class BreakingChangeType {
    SLICE_REMOVED,
    SLICE_ADDED_REQUIRED,
    BUILD_LOGIC_CHANGED,
    JOIN_REMOVED,
    INDEX_REMOVED,
    FIELD_MAPPING_CHANGED,
}

enum class MigrationStrategy {
    FULL_REBUILD,    // 전체 재생성
    INCREMENTAL,     // 영향받은 SliceType만
    NO_ACTION,       // MINOR 변경 (자동 호환)
}

data class ImpactEstimate(
    val affectedSlices: List<SliceType>,
    val rebuildRequired: Boolean,
    val estimatedEntities: Long? = null,
)
```

**DynamoDB Adapter 파싱:**

```kotlin
// DynamoDBContractRegistryAdapter.kt
private fun parseRuleSet(...): Result<RuleSetContract> {
    // 기존 파싱...

    // ✨ Compatibility 파싱
    val compatibilityJson = data["compatibility"]?.jsonArray ?: emptyList()
    val compatibility = compatibilityJson.map { entry ->
        val obj = entry.jsonObject
        CompatibilityEntry(
            fromVersion = SemVer.parse(obj["fromVersion"]!!.jsonPrimitive.content),
            toVersion = SemVer.parse(obj["toVersion"]!!.jsonPrimitive.content),
            compatible = obj["compatible"]!!.jsonPrimitive.boolean,
            breakingChanges = parseBreakingChanges(obj["breakingChanges"]?.jsonArray),
            migrationStrategy = MigrationStrategy.valueOf(
                obj["migrationStrategy"]?.jsonPrimitive?.content ?: "FULL_REBUILD"
            ),
            estimatedImpact = obj["estimatedImpact"]?.jsonObject?.let { parseImpactEstimate(it) }
        )
    }

    return Result.Ok(
        RuleSetContract(
            // ...
            compatibility = compatibility
        )
    )
}
```

---

### 2-4. Automated Invalidation Propagation

**Contract 변경 감지 Workflow:**

```kotlin
// pkg/orchestration/application/ContractChangeWorkflow.kt (NEW!)

/**
 * RFC-IMPL-014: Contract 변경 감지 및 무효화 전파
 *
 * Contract 배포 시 자동으로:
 * 1. Breaking change 분석
 * 2. 영향받는 슬라이스 찾기
 * 3. Outbox 이벤트 발행 (무효화)
 */
class ContractChangeWorkflow(
    private val contractRegistry: ContractRegistryPort,
    private val sliceRepo: SliceRepositoryPort,
    private val outboxRepo: OutboxRepositoryPort,
    private val tracer: Tracer,
) {
    suspend fun publishContractChange(
        contractId: String,
        oldVersion: SemVer,
        newVersion: SemVer,
    ): Result<InvalidationPlan> {
        return tracer.withSpanSuspend("ContractChangeWorkflow.publishContractChange") {
            // 1. 새 버전 Contract 로드
            val newContract = contractRegistry.loadRuleSetContract(
                ContractRef(contractId, newVersion)
            )

            // 2. Compatibility 분석
            val compatEntry = newContract.compatibility.find {
                it.fromVersion == oldVersion && it.toVersion == newVersion
            }

            if (compatEntry == null) {
                return@withSpanSuspend Result.Err(
                    DomainError.ContractError(
                        "Missing compatibility entry: $oldVersion → $newVersion"
                    )
                )
            }

            // 3. MINOR 변경이면 무효화 불필요
            if (compatEntry.compatible && compatEntry.migrationStrategy == MigrationStrategy.NO_ACTION) {
                return@withSpanSuspend Result.Ok(InvalidationPlan.NoImpact)
            }

            // 4. 영향받는 슬라이스 찾기
            val affectedSlices = sliceRepo.findByDependency(contractId, oldVersion)

            if (affectedSlices.isEmpty()) {
                return@withSpanSuspend Result.Ok(InvalidationPlan.NoImpact)
            }

            // 5. Invalidation 이벤트 발행
            val invalidationEvents = affectedSlices.map { slice ->
                OutboxEntry.create(
                    aggregateType = AggregateType.SLICE,
                    aggregateId = "${slice.tenantId}:${slice.entityKey}:${slice.version}",
                    eventType = "SliceInvalidated",
                    payload = buildInvalidationPayload(slice, compatEntry)
                )
            }

            outboxRepo.insertAll(invalidationEvents)

            // 6. Invalidation Plan 반환
            Result.Ok(
                InvalidationPlan(
                    totalAffected = affectedSlices.size,
                    bySliceType = affectedSlices.groupBy { it.sliceType }
                        .mapValues { it.value.size },
                    breakingChanges = compatEntry.breakingChanges,
                    migrationStrategy = compatEntry.migrationStrategy,
                    estimatedRebuildTime = estimateRebuildTime(affectedSlices.size),
                )
            )
        }
    }

    private fun buildInvalidationPayload(
        slice: SliceRecord,
        compat: CompatibilityEntry,
    ): String {
        return buildJsonObject {
            put("payloadVersion", "1.0")
            put("tenantId", slice.tenantId.value)
            put("entityKey", slice.entityKey.value)
            put("version", slice.version)
            put("sliceType", slice.sliceType.name)
            put("oldRuleSetVersion", compat.fromVersion.toString())
            put("newRuleSetVersion", compat.toVersion.toString())
            put("migrationStrategy", compat.migrationStrategy.name)
            putJsonArray("breakingChanges") {
                compat.breakingChanges.forEach { change ->
                    add(buildJsonObject {
                        put("type", change.type.name)
                        change.reason?.let { put("reason", it) }
                    })
                }
            }
        }.toString()
    }

    private fun estimateRebuildTime(count: Int): String {
        val seconds = (count * 0.5).toInt()  // 슬라이스당 ~0.5초
        return when {
            seconds < 60 -> "$seconds seconds"
            seconds < 3600 -> "${seconds / 60} minutes"
            else -> "${seconds / 3600} hours"
        }
    }
}

/**
 * Invalidation 결과 플랜
 */
sealed class InvalidationPlan {
    data class Impact(
        val totalAffected: Int,
        val bySliceType: Map<SliceType, Int>,
        val breakingChanges: List<BreakingChange>,
        val migrationStrategy: MigrationStrategy,
        val estimatedRebuildTime: String,
    ) : InvalidationPlan()

    object NoImpact : InvalidationPlan()
}
```

**Outbox Worker 확장:**

```kotlin
// OutboxPollingWorker.kt (확장)
class OutboxPollingWorker(
    // ...
    private val slicingWorkflow: SlicingWorkflow,  // ✨ NEW
) {
    private suspend fun processEntry(entry: OutboxEntry) {
        when (entry.eventType) {
            "RawDataIngested" -> {
                // 기존 로직...
            }

            // ✨ NEW: 슬라이스 무효화 이벤트
            "SliceInvalidated" -> {
                val payload = parseInvalidationPayload(entry.payload)

                log.info(
                    "Slice invalidated: ${payload.tenantId}:${payload.entityKey} " +
                    "sliceType=${payload.sliceType} " +
                    "oldVersion=${payload.oldRuleSetVersion} → ${payload.newRuleSetVersion}"
                )

                // 자동 재생성 트리거
                when (payload.migrationStrategy) {
                    MigrationStrategy.FULL_REBUILD -> {
                        slicingWorkflow.regenerateAllSlices(
                            tenantId = payload.tenantId,
                            entityKey = payload.entityKey,
                            version = payload.version,
                            ruleSetRef = ContractRef(
                                payload.ruleSetId,
                                SemVer.parse(payload.newRuleSetVersion)
                            )
                        )
                    }
                    MigrationStrategy.INCREMENTAL -> {
                        val affectedTypes = payload.breakingChanges
                            .mapNotNull { it.sliceType }
                            .toSet()

                        slicingWorkflow.regeneratePartialSlices(
                            tenantId = payload.tenantId,
                            entityKey = payload.entityKey,
                            version = payload.version,
                            ruleSetRef = ContractRef(
                                payload.ruleSetId,
                                SemVer.parse(payload.newRuleSetVersion)
                            ),
                            impactedTypes = affectedTypes
                        )
                    }
                    MigrationStrategy.NO_ACTION -> {
                        // 아무것도 안 함
                    }
                }
            }
        }
    }
}
```

---

### 2-5. Migration CLI

**CLI 명령어:**

```kotlin
// apps/opscli/MigrateContractCmd.kt (NEW!)

/**
 * RFC-IMPL-014: Contract 마이그레이션 CLI
 *
 * 사용법:
 * ./gradlew opscli migrate \
 *   --contract=ruleset.product.v1 \
 *   --from=1.0.0 \
 *   --to=2.0.0 \
 *   --batch-size=100 \
 *   --dry-run
 */
class MigrateContractCmd : CliktCommand(
    name = "migrate",
    help = "Migrate slices to new contract version"
) {
    private val contractId by option("--contract", "-c")
        .required()
        .help("Contract ID (e.g., ruleset.product.v1)")

    private val fromVersion by option("--from", "-f")
        .required()
        .help("Source version (e.g., 1.0.0)")

    private val toVersion by option("--to", "-t")
        .required()
        .help("Target version (e.g., 2.0.0)")

    private val batchSize by option("--batch-size", "-b")
        .int()
        .default(100)
        .help("Batch size for processing")

    private val dryRun by option("--dry-run")
        .flag()
        .help("Analyze impact without actual migration")

    private val parallel by option("--parallel", "-p")
        .int()
        .default(1)
        .help("Parallel workers (1-10)")

    override fun run() = runBlocking {
        echo("🔍 Analyzing migration impact...", err = false)

        val fromVer = SemVer.parse(fromVersion)
        val toVer = SemVer.parse(toVersion)

        // 1. 영향받는 슬라이스 조회
        val affectedSlices = sliceRepo.findByDependency(contractId, fromVer)

        if (affectedSlices.isEmpty()) {
            echo("✅ No slices found with $contractId@$fromVersion", err = false)
            return@runBlocking
        }

        // 2. Breaking change 분석
        val newContract = contractRegistry.loadRuleSetContract(
            ContractRef(contractId, toVer)
        )

        val compatEntry = newContract.compatibility.find {
            it.fromVersion == fromVer && it.toVersion == toVer
        }

        if (compatEntry == null) {
            echo("❌ Missing compatibility entry: $fromVersion → $toVersion", err = true)
            throw CliktError("Compatibility not defined")
        }

        // 3. 영향 요약 출력
        echo("\n📊 Migration Impact:", err = false)
        echo("  Contract: $contractId", err = false)
        echo("  Version: $fromVersion → $toVersion", err = false)
        echo("  Affected slices: ${affectedSlices.size}", err = false)
        echo("  Strategy: ${compatEntry.migrationStrategy}", err = false)

        val byType = affectedSlices.groupBy { it.sliceType }
        byType.forEach { (type, slices) ->
            echo("    - ${type.name}: ${slices.size} slices", err = false)
        }

        if (compatEntry.breakingChanges.isNotEmpty()) {
            echo("\n⚠️  Breaking changes:", err = false)
            compatEntry.breakingChanges.forEach { change ->
                echo("    - ${change.type}: ${change.reason}", err = false)
            }
        }

        if (dryRun) {
            echo("\n✅ Dry-run complete (no changes made)", err = false)
            return@runBlocking
        }

        // 4. 실제 마이그레이션 실행
        echo("\n🚀 Starting migration...", err = false)

        val batches = affectedSlices.chunked(batchSize)
        val totalBatches = batches.size

        batches.forEachIndexed { batchIdx, batch ->
            echo("📦 Processing batch ${batchIdx + 1}/$totalBatches (${batch.size} slices)...", err = false)

            // 병렬 처리
            batch.chunked((batch.size + parallel - 1) / parallel).map { chunk ->
                async {
                    chunk.forEach { slice ->
                        try {
                            // RawData 다시 읽어서 재생성
                            val rawData = rawDataRepo.get(
                                slice.tenantId,
                                slice.entityKey,
                                slice.version
                            )

                            slicingWorkflow.slice(
                                rawData,
                                ContractRef(contractId, toVer)
                            )
                        } catch (e: Exception) {
                            echo("  ❌ Failed: ${slice.entityKey} - ${e.message}", err = true)
                        }
                    }
                }
            }.awaitAll()

            echo("  ✅ Batch ${batchIdx + 1} complete", err = false)
        }

        echo("\n🎉 Migration complete!", err = false)
        echo("  Total migrated: ${affectedSlices.size} slices", err = false)
    }
}
```

**등록:**

```kotlin
// IvmLiteCli.kt
class IvmLiteCli : CliktCommand() {
    override fun run() = Unit
}

fun main(args: Array<String>) = IvmLiteCli()
    .subcommands(
        ValidateContractsCmd(),
        MigrateContractCmd(),  // ✨ NEW
    )
    .main(args)
```

---

### 2-6. Lazy Migration with Graceful Degradation

**ViewDefinition 확장 (PartialPolicy 활용):**

```yaml
# view-product-search.v1.yaml
meta:
  id: view.product.search.v1
  version: 1.0.0
  status: ACTIVE

requiredSlices:
  - SEARCH

partialPolicy:
  allowed: true
  optionalOnly: false

  # ✨ NEW: Stale data 허용 여부
  allowStale: true  # true이면 버전 불일치 시 경고와 함께 반환

  responseMeta:
    includeMissingSlices: true
    includeUsedContracts: true
    includeStaleDependencies: true  # ✨ NEW
```

**Contract 도메인 확장:**

```kotlin
// ViewDefinitionContract.kt
data class PartialPolicy(
    val allowed: Boolean,
    val optionalOnly: Boolean,
    val responseMeta: ResponseMeta,

    /**
     * RFC-IMPL-014: Stale dependency 허용
     *
     * true: 버전 불일치 시 경고와 함께 이전 버전 반환 (Lazy Migration)
     * false: 버전 불일치 시 즉시 에러 (fail-closed)
     */
    val allowStale: Boolean = false,
)

data class ResponseMeta(
    val includeMissingSlices: Boolean,
    val includeUsedContracts: Boolean,

    /**
     * RFC-IMPL-014: Stale dependency 정보 포함
     */
    val includeStaleDependencies: Boolean = false,
)
```

**Runtime 동작 (Section 2-2 참조):**

```
1. 조회 시 dependencyFingerprint 불일치 감지
2. allowStale=true → 백그라운드 재생성 트리거 + 이전 버전 반환 (warning)
3. allowStale=false → 즉시 에러 (fail-closed)
4. 백그라운드 재생성 완료 → 다음 조회부터 새 버전 사용
```

---

## 3. Property-Based Testing

**멱등성/결정성 검증:**

```kotlin
// test/.../VersionMigrationPropertyTest.kt

/**
 * RFC-IMPL-014: 버전 마이그레이션 Property-Based Testing
 *
 * 수학적 정확성 보장:
 * - 멱등성: 동일 입력 → 동일 출력
 * - 결정성: 순서 무관하게 동일 결과
 * - 정합성: 버전 불일치 항상 감지
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VersionMigrationPropertyTest : StringSpec({

    "PROPERTY: 동일 RawData + RuleSet 버전 → 동일 슬라이스 (멱등성)" {
        checkAll(
            iterations = 100,
            Arb.rawDataRecord(),
            Arb.semVer()
        ) { rawData, version ->
            val ref = ContractRef("ruleset.test.v1", version)

            val result1 = slicingEngine.slice(rawData, ref)
            val result2 = slicingEngine.slice(rawData, ref)

            result1.shouldBeInstanceOf<SlicingEngine.Result.Ok>()
            result2.shouldBeInstanceOf<SlicingEngine.Result.Ok>()

            val slices1 = (result1 as SlicingEngine.Result.Ok).value.slices
            val slices2 = (result2 as SlicingEngine.Result.Ok).value.slices

            // 멱등성: 동일한 결과
            slices1.shouldBe(slices2)

            // fingerprint 일관성
            slices1.forEach { s1 ->
                val s2 = slices2.first { it.sliceType == s1.sliceType }
                s1.dependencyFingerprint shouldBe s2.dependencyFingerprint
            }
        }
    }

    "PROPERTY: Breaking change → 슬라이스 무효화 필수" {
        checkAll(
            iterations = 50,
            Arb.contractChange()
        ) { change ->
            val compatEntry = change.compatibilityEntry

            if (!compatEntry.compatible) {
                // Breaking change 감지
                val plan = contractChangeWorkflow.publishContractChange(
                    change.contractId,
                    change.oldVersion,
                    change.newVersion
                )

                plan.shouldBeInstanceOf<ContractChangeWorkflow.Result.Ok>()
                val invalidationPlan = (plan as ContractChangeWorkflow.Result.Ok).value

                // 무효화 이벤트 발행됨
                invalidationPlan.shouldBeInstanceOf<InvalidationPlan.Impact>()
                (invalidationPlan as InvalidationPlan.Impact).totalAffected.shouldBeGreaterThan(0)
            }
        }
    }

    "PROPERTY: 버전 불일치 항상 감지" {
        checkAll(
            iterations = 100,
            Arb.sliceRecord(),
            Arb.semVer()
        ) { slice, newRuleSetVersion ->
            assume(slice.ruleSetVersion != newRuleSetVersion)

            // 슬라이스는 v1으로 생성됨
            val oldFingerprint = slice.dependencyFingerprint

            // RuleSet v2 로드
            val newRuleSet = mockRuleSet(newRuleSetVersion)
            val newFingerprint = computeFingerprint(newRuleSet)

            // 버전 불일치 감지
            oldFingerprint shouldNotBe newFingerprint
        }
    }

    "PROPERTY: Lazy Migration → 최종 일관성" {
        checkAll(
            iterations = 50,
            Arb.tenantId(),
            Arb.entityKey(),
            Arb.semVer()
        ) { tenantId, entityKey, newVersion ->
            // 1. 초기 슬라이스 (v1)
            val slice = createSliceWithVersion(tenantId, entityKey, SemVer(1, 0, 0))

            // 2. RuleSet v2 배포
            val newRef = ContractRef("ruleset.test.v1", newVersion)

            // 3. Lazy migration 트리거
            val result = queryViewWorkflow.execute(
                tenantId, "view.test.v1", entityKey, slice.version
            )

            // allowStale=true면 이전 버전 반환 + 백그라운드 재생성
            if (viewDef.partialPolicy.allowStale) {
                result.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok>()
                // ... 백그라운드 작업 대기 ...
            }

            // 4. 재생성 완료 후 다시 조회
            eventually(duration = 5.seconds) {
                val result2 = queryViewWorkflow.execute(
                    tenantId, "view.test.v1", entityKey, slice.version
                )

                result2.shouldBeInstanceOf<QueryViewWorkflow.Result.Ok>()
                val response = (result2 as QueryViewWorkflow.Result.Ok).value

                // 최종 일관성: 새 버전으로 전환됨
                response.meta?.staleDependencies shouldBe null
            }
        }
    }
})
```

---

## 4. 구현 로드맵

### Phase 1: Critical (P0) - 2주

| Task | 난이도 | 예상 시간 |
|------|--------|----------|
| SliceRecord에 `dependencyFingerprint` 추가 | ⭐ Easy | 1일 |
| DB 마이그레이션 (ALTER TABLE) | ⭐ Easy | 0.5일 |
| SlicingEngine에서 fingerprint 계산 | ⭐⭐ Medium | 1일 |
| QueryViewWorkflow 런타임 검증 | ⭐⭐ Medium | 2일 |
| DomainError.StaleDependencyError 추가 | ⭐ Easy | 0.5일 |
| 단위 테스트 (fingerprint, verification) | ⭐⭐ Medium | 2일 |
| 통합 테스트 (E2E) | ⭐⭐⭐ Hard | 3일 |

**Total: ~10일**

### Phase 2: High Priority (P1) - 3주

| Task | 난이도 | 예상 시간 |
|------|--------|----------|
| CompatibilityEntry 도메인 모델 | ⭐ Easy | 1일 |
| YAML compatibility 파싱 | ⭐⭐ Medium | 2일 |
| MigrateContractCmd CLI | ⭐⭐ Medium | 3일 |
| Dry-run 모드 구현 | ⭐ Easy | 1일 |
| 병렬 처리 (--parallel) | ⭐⭐ Medium | 2일 |
| Progress bar / 로깅 | ⭐ Easy | 1일 |
| CLI 통합 테스트 | ⭐⭐ Medium | 2일 |

**Total: ~12일**

### Phase 3: Medium Priority (P2) - 4주

| Task | 난이도 | 예상 시간 |
|------|--------|----------|
| ContractChangeWorkflow 구현 | ⭐⭐⭐ Hard | 5일 |
| InvalidationPlan 도메인 모델 | ⭐⭐ Medium | 2일 |
| OutboxPollingWorker 확장 | ⭐⭐ Medium | 3일 |
| SliceInvalidated 이벤트 핸들러 | ⭐⭐ Medium | 2일 |
| Lazy Migration 트리거 | ⭐⭐ Medium | 2일 |
| PartialPolicy.allowStale 구현 | ⭐⭐ Medium | 2일 |
| ViewMeta.staleDependencies | ⭐ Easy | 1일 |
| 통합 테스트 (Outbox + Migration) | ⭐⭐⭐ Hard | 3일 |

**Total: ~20일**

### Phase 4: Nice-to-Have (P3) - 2주

| Task | 난이도 | 예상 시간 |
|------|--------|----------|
| Property-Based Testing 셋업 | ⭐⭐ Medium | 2일 |
| Arb generators (rawData, semVer 등) | ⭐⭐ Medium | 2일 |
| 멱등성 property tests | ⭐⭐ Medium | 2일 |
| Breaking change property tests | ⭐⭐⭐ Hard | 3일 |
| Lazy migration property tests | ⭐⭐⭐ Hard | 3일 |

**Total: ~12일**

---

## 5. 운영 가이드

### 5-1. Contract 배포 체크리스트

```bash
# 1. Compatibility Matrix 정의
vim src/main/resources/contracts/v1/ruleset-product.v2.yaml
# → compatibility 섹션 추가

# 2. 로컬 검증
./gradlew validateContracts

# 3. Dry-run 마이그레이션 분석
./gradlew opscli migrate \
  --contract=ruleset.product.v1 \
  --from=1.0.0 \
  --to=2.0.0 \
  --dry-run

# 출력 예시:
# 📊 Migration Impact:
#   Contract: ruleset.product.v1
#   Version: 1.0.0 → 2.0.0
#   Affected slices: 1,234
#   Strategy: FULL_REBUILD
#     - CORE: 1,234 slices
#     - SEARCH: 1,234 slices
#   ⚠️  Breaking changes:
#     - SLICE_REMOVED: CORE merged into SEARCH

# 4. DynamoDB에 배포
./gradlew deployContract \
  --id=ruleset.product.v1 \
  --version=2.0.0 \
  --status=ACTIVE

# 5. 기존 버전 DEPRECATED
./gradlew updateContractStatus \
  --id=ruleset.product.v1 \
  --version=1.0.0 \
  --status=DEPRECATED

# 6. 마이그레이션 실행
./gradlew opscli migrate \
  --contract=ruleset.product.v1 \
  --from=1.0.0 \
  --to=2.0.0 \
  --batch-size=100 \
  --parallel=4

# 7. 검증
./gradlew opscli verify-migration \
  --contract=ruleset.product.v1 \
  --version=2.0.0
```

### 5-2. 모니터링

**Metrics:**
```kotlin
// 추가할 메트릭
- ivm.slices.stale_dependency_detected (counter)
- ivm.slices.lazy_migration_triggered (counter)
- ivm.migration.slices_processed (counter)
- ivm.migration.duration_seconds (histogram)
```

**Alerts:**
```yaml
# AlertManager 규칙
- alert: StaleDependencyRate
  expr: rate(ivm_slices_stale_dependency_detected[5m]) > 0.1
  annotations:
    summary: "High stale dependency rate"
    description: "{{ $value }} stale slices detected per second"

- alert: MigrationStalled
  expr: ivm_migration_slices_processed == 0 for 10m
  annotations:
    summary: "Migration appears stalled"
```

### 5-3. 롤백

**시나리오: v2 배포 후 문제 발생**

```bash
# 1. 새 버전 DEPRECATED
./gradlew updateContractStatus \
  --id=ruleset.product.v1 \
  --version=2.0.0 \
  --status=DEPRECATED

# 2. 이전 버전 ACTIVE 복구
./gradlew updateContractStatus \
  --id=ruleset.product.v1 \
  --version=1.0.0 \
  --status=ACTIVE

# 3. v2 슬라이스 삭제 (필요 시)
./gradlew opscli delete-slices \
  --contract=ruleset.product.v1 \
  --version=2.0.0

# 4. 검증
./gradlew opscli verify-rollback \
  --contract=ruleset.product.v1 \
  --version=1.0.0
```

---

## 6. 참고 시스템 비교

| 시스템 | 버전 관리 방식 | 장점 | 단점 |
|--------|---------------|------|------|
| **Netflix Hollow** | Schema fingerprint + compatibility check | 자동 감지, 안전한 evolution | 구현 복잡도 높음 |
| **LinkedIn Espresso** | Multi-version read path | 무중단 마이그레이션 | 스토리지 2배 |
| **Avro** | Compatibility matrix + schema registry | 표준화됨, 도구 풍부 | 스키마 중앙 관리 필요 |
| **Protobuf** | Backward/forward compatibility | 성능 우수 | Breaking change 처리 약함 |
| **IVM-Lite (Proposed)** | Fingerprint + Compatibility + Lazy migration | 정합성 보장, 점진적 마이그레이션, fail-closed | 초기 구현 비용 |

---

## 7. 결론

### 7-1. 핵심 이점

✅ **정합성 보장**: 버전 불일치 100% 감지
✅ **자동화**: Contract 변경 → 자동 무효화 → 자동 재생성
✅ **무중단 마이그레이션**: Lazy migration + Graceful degradation
✅ **운영 효율성**: CLI로 대량 마이그레이션 간편화
✅ **검증 가능성**: Property-based testing으로 수학적 정확성 보장

### 7-2. Trade-offs

⚠️ **추가 필드**: `dependencyFingerprint` (VARCHAR 128)
⚠️ **계산 오버헤드**: Fingerprint 계산 (미미함, ~1ms)
⚠️ **호환성 메타데이터**: 수동 작성 필요 (문서화로 완화)

### 7-3. 구현 우선순위

**즉시 구현 (P0):**
1. dependencyFingerprint 추가
2. Runtime verification

**이후 구현 (P1-P2):**
3. Compatibility Matrix
4. Migration CLI
5. Automated Invalidation

**장기 개선 (P3):**
6. Property-based testing

---

## 8. 참고

- [RFC-012](./rfc012-contract-versioning-strategy.md) - 버전 관리 전략 (부모 RFC)
- [ADR-0011](../adr/0011-contract-versioning-strategy.md) - 버전 관리 결정사항
- [Netflix Hollow - Schema Evolution](https://hollow.how/advanced-topics/#schema-changes)
- [LinkedIn Engineering - Espresso](https://engineering.linkedin.com/espresso/introducing-espresso-linkedins-hot-new-distributed-document-store)
- [Avro Schema Evolution](https://avro.apache.org/docs/current/spec.html#Schema+Resolution)

---

**문의**: RFC-IMPL-014 관련 문의는 #ivm-platform 채널로 연락주세요.
