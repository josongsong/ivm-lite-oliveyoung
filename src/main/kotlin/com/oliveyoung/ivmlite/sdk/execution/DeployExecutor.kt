package com.oliveyoung.ivmlite.sdk.execution

import com.oliveyoung.ivmlite.pkg.orchestration.application.IngestWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.ShipWorkflow
import com.oliveyoung.ivmlite.pkg.orchestration.application.SlicingWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.pkg.rawdata.ports.OutboxRepositoryPort
import com.oliveyoung.ivmlite.sdk.domain.CompileResult
import com.oliveyoung.ivmlite.sdk.domain.IngestResult
import com.oliveyoung.ivmlite.sdk.domain.ShipResult
import com.oliveyoung.ivmlite.sdk.dsl.entity.BrandInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.CategoryInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.EntityInput
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.model.*
import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.VersionGenerator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Deploy Executor (RFC-IMPL-011 Wave 5-L)
 *
 * 실제 Workflow 연동 및 Deploy 실행
 * - executeSync: RawData Ingest → Compile → Ship (동기/비동기)
 * - executeAsync: RawData Ingest → COMPILE_TASK Outbox 적재
 */
class DeployExecutor(
    private val ingestWorkflow: IngestWorkflow,
    private val slicingWorkflow: SlicingWorkflow,
    private val shipWorkflow: ShipWorkflow,
    private val outboxRepository: OutboxRepositoryPort
) {
    companion object {
        private val json = Json {
            prettyPrint = false
            encodeDefaults = true
        }
    }

    /**
     * 동기 Deploy 실행
     *
     * 1. RawData Ingest
     * 2. Compile (Slicing) - spec.compileMode에 따라 sync/async
     * 3. Ship - spec.shipSpec에 따라 sync/async
     */
    suspend fun <T : EntityInput> executeSync(input: T, spec: DeploySpec): DeployResult {
        // 1. EntityInput → RawData 변환
        val rawDataParams = convertToRawDataParams(input)

        // 2. RawData Ingest (항상 동기)
        val ingestResult = ingestWorkflow.execute(
            tenantId = rawDataParams.tenantId,
            entityKey = rawDataParams.entityKey,
            version = rawDataParams.version,
            schemaId = rawDataParams.schemaId,
            schemaVersion = rawDataParams.schemaVersion,
            payloadJson = rawDataParams.payloadJson
        )

        when (ingestResult) {
            is IngestWorkflow.Result.Err -> {
                return DeployResult.failure(
                    rawDataParams.entityKey.value,
                    rawDataParams.version.toString(),
                    ingestResult.error.toString()
                )
            }
            is IngestWorkflow.Result.Ok -> { /* continue */ }
        }

        // 3. Compile (Slicing)
        when (spec.compileMode) {
            is CompileMode.Sync -> {
                // 동기 Compile
                val slicingResult = slicingWorkflow.execute(
                    tenantId = rawDataParams.tenantId,
                    entityKey = rawDataParams.entityKey,
                    version = rawDataParams.version
                )

                when (slicingResult) {
                    is SlicingWorkflow.Result.Err -> {
                        return DeployResult.failure(
                            rawDataParams.entityKey.value,
                            rawDataParams.version.toString(),
                            slicingResult.error.toString()
                        )
                    }
                    is SlicingWorkflow.Result.Ok -> { /* continue */ }
                }
            }
            is CompileMode.SyncWithTargets -> {
                // 동기 Compile (SyncWithTargets는 v1에서 Sync와 동일하게 처리)
                val slicingResult = slicingWorkflow.execute(
                    tenantId = rawDataParams.tenantId,
                    entityKey = rawDataParams.entityKey,
                    version = rawDataParams.version
                )

                when (slicingResult) {
                    is SlicingWorkflow.Result.Err -> {
                        return DeployResult.failure(
                            rawDataParams.entityKey.value,
                            rawDataParams.version.toString(),
                            slicingResult.error.toString()
                        )
                    }
                    is SlicingWorkflow.Result.Ok -> { /* continue */ }
                }
            }
            is CompileMode.Async -> {
                // Async Compile → Outbox에 적재
                val compileTaskEntry = OutboxEntry.create(
                    aggregateType = AggregateType.RAW_DATA,
                    aggregateId = "${rawDataParams.tenantId.value}:${rawDataParams.entityKey.value}",
                    eventType = "CompileRequested",
                    payload = buildJsonObject {
                        put("payloadVersion", "1.0")
                        put("tenantId", rawDataParams.tenantId.value)
                        put("entityKey", rawDataParams.entityKey.value)
                        put("version", rawDataParams.version.toString())
                        put("compileMode", "async")
                    }.toString()
                )

                when (val r = outboxRepository.insert(compileTaskEntry)) {
                    is OutboxRepositoryPort.Result.Err -> {
                        return DeployResult.failure(
                            rawDataParams.entityKey.value,
                            rawDataParams.version.toString(),
                            r.error.toString()
                        )
                    }
                    is OutboxRepositoryPort.Result.Ok -> { /* continue */ }
                }
            }
        }

        // 4. Ship (항상 Outbox를 통해 비동기 처리)
        spec.shipSpec?.let { shipSpec ->
            // ShipMode.Sync도 Outbox를 통해 처리 (일관성 및 확장성)
            shipSpec.sinks.forEach { sink ->
                val shipTaskEntry = OutboxEntry.create(
                    aggregateType = AggregateType.SLICE,
                    aggregateId = "${rawDataParams.tenantId.value}:${rawDataParams.entityKey.value}",
                    eventType = "ShipRequested",
                    payload = buildJsonObject {
                        put("payloadVersion", "1.0")
                        put("tenantId", rawDataParams.tenantId.value)
                        put("entityKey", rawDataParams.entityKey.value)
                        put("version", rawDataParams.version.toString())
                        put("sink", sinkSpecToType(sink))
                        put("shipMode", "async")
                    }.toString()
                )

                when (val r = outboxRepository.insert(shipTaskEntry)) {
                    is OutboxRepositoryPort.Result.Err -> {
                        return DeployResult.failure(
                            rawDataParams.entityKey.value,
                            rawDataParams.version.toString(),
                            "Ship outbox insert failed: ${r.error}"
                        )
                    }
                    is OutboxRepositoryPort.Result.Ok -> { /* continue */ }
                }
            }
        }

        return DeployResult.success(
            rawDataParams.entityKey.value,
            rawDataParams.version.toString()
        )
    }

    /**
     * 비동기 Deploy 실행
     *
     * 1. RawData Ingest만 동기
     * 2. COMPILE_TASK Outbox 적재
     * 3. Job 정보 반환
     */
    suspend fun <T : EntityInput> executeAsync(input: T, spec: DeploySpec): DeployJob {
        // 1. EntityInput → RawData 변환
        val rawDataParams = convertToRawDataParams(input)

        // 2. RawData Ingest (항상 동기)
        val ingestResult = ingestWorkflow.execute(
            tenantId = rawDataParams.tenantId,
            entityKey = rawDataParams.entityKey,
            version = rawDataParams.version,
            schemaId = rawDataParams.schemaId,
            schemaVersion = rawDataParams.schemaVersion,
            payloadJson = rawDataParams.payloadJson
        )

        when (ingestResult) {
            is IngestWorkflow.Result.Err -> {
                throw RuntimeException("Ingest failed: ${ingestResult.error}")
            }
            is IngestWorkflow.Result.Ok -> { /* continue */ }
        }

        // 3. COMPILE_TASK Outbox 적재
        val compileTaskEntry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "${rawDataParams.tenantId.value}:${rawDataParams.entityKey.value}",
            eventType = "CompileRequested",
            payload = buildJsonObject {
                put("payloadVersion", "1.0")
                put("tenantId", rawDataParams.tenantId.value)
                put("entityKey", rawDataParams.entityKey.value)
                put("version", rawDataParams.version.toString())
                put("compileMode", spec.compileMode.toString())
                put("shipSpec", if (spec.shipSpec != null) "present" else "absent")
            }.toString()
        )

        val jobId = when (val r = outboxRepository.insert(compileTaskEntry)) {
            is OutboxRepositoryPort.Result.Ok -> r.value.id.toString()
            is OutboxRepositoryPort.Result.Err -> {
                throw RuntimeException("Outbox insert failed: ${r.error}")
            }
        }

        return DeployJob(
            jobId = jobId,
            entityKey = rawDataParams.entityKey.value,
            version = rawDataParams.version.toString(),
            state = DeployState.QUEUED
        )
    }

    /**
     * EntityInput을 RawData 파라미터로 변환
     */
    private fun <T : EntityInput> convertToRawDataParams(input: T): RawDataParams {
        val tenantId = TenantId(input.tenantId)
        val version = VersionGenerator.generate() // 충돌 없는 고유 버전 (SSOT)

        // EntityType별 처리
        val (entityKey, schemaId, schemaVersion, payloadJson) = when (input) {
            is ProductInput -> {
                val key = EntityKey("${input.entityType}:${input.sku}")
                val schema = "product.v1"
                val schemaVer = SemVer.parse("1.0.0")
                val payload = buildJsonObject {
                    put("sku", input.sku)
                    put("name", input.name)
                    put("price", input.price)
                    put("currency", input.currency)
                    input.category?.let { put("category", it) }
                    input.brand?.let { put("brand", it) }
                    put("attributes", buildJsonObject {
                        input.attributes.forEach { (k, v) ->
                            when (v) {
                                is String -> put(k, v)
                                is Number -> put(k, v.toDouble())
                                is Boolean -> put(k, v)
                                else -> put(k, v.toString())
                            }
                        }
                    })
                }.toString()
                Tuple4(key, schema, schemaVer, payload)
            }
            is BrandInput -> {
                val key = EntityKey("${input.entityType}:${input.brandId}")
                val schema = "brand.v1"
                val schemaVer = SemVer.parse("1.0.0")
                val payload = buildJsonObject {
                    put("brandId", input.brandId)
                    put("name", input.name)
                    input.logoUrl?.let { put("logoUrl", it) }
                    input.description?.let { put("description", it) }
                    input.country?.let { put("country", it) }
                    put("attributes", buildJsonObject {
                        input.attributes.forEach { (k, v) ->
                            when (v) {
                                is String -> put(k, v)
                                is Number -> put(k, v.toDouble())
                                is Boolean -> put(k, v)
                                else -> put(k, v.toString())
                            }
                        }
                    })
                }.toString()
                Tuple4(key, schema, schemaVer, payload)
            }
            is CategoryInput -> {
                val key = EntityKey("${input.entityType}:${input.categoryId}")
                val schema = "category.v1"
                val schemaVer = SemVer.parse("1.0.0")
                val payload = buildJsonObject {
                    put("categoryId", input.categoryId)
                    put("name", input.name)
                    input.parentId?.let { put("parentId", it) }
                    put("depth", input.depth)
                    put("displayOrder", input.displayOrder)
                    put("attributes", buildJsonObject {
                        input.attributes.forEach { (k, v) ->
                            when (v) {
                                is String -> put(k, v)
                                is Number -> put(k, v.toDouble())
                                is Boolean -> put(k, v)
                                else -> put(k, v.toString())
                            }
                        }
                    })
                }.toString()
                Tuple4(key, schema, schemaVer, payload)
            }
            else -> throw IllegalArgumentException("Unsupported EntityInput type: ${input::class}")
        }

        return RawDataParams(
            tenantId = tenantId,
            entityKey = entityKey,
            version = version,
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            payloadJson = payloadJson
        )
    }

    /**
     * RawData 변환 결과
     */
    private data class RawDataParams(
        val tenantId: TenantId,
        val entityKey: EntityKey,
        val version: Long,
        val schemaId: String,
        val schemaVersion: SemVer,
        val payloadJson: String
    )

    /**
     * Tuple4 helper
     */
    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    /**
     * SinkSpec을 sinkType 문자열로 변환
     */
    private fun sinkSpecToType(spec: SinkSpec): String = when (spec) {
        is OpenSearchSinkSpec -> "opensearch"
        is PersonalizeSinkSpec -> "personalize"
    }

    /**
     * EntityInput에서 EntityKey 추출 (DRY)
     */
    private fun <T : EntityInput> extractEntityKey(input: T): EntityKey = when (input) {
        is ProductInput -> EntityKey("${input.entityType}:${input.sku}")
        is BrandInput -> EntityKey("${input.entityType}:${input.brandId}")
        is CategoryInput -> EntityKey("${input.entityType}:${input.categoryId}")
        else -> throw IllegalArgumentException("Unsupported EntityInput type: ${input::class}")
    }

    // ===== 단계별 제어 API (DX 끝판왕) =====

    /**
     * Ingest만 실행 (동기)
     */
    suspend fun <T : EntityInput> ingestOnly(input: T): IngestResult {
        val rawDataParams = convertToRawDataParams(input)

        val ingestResult = ingestWorkflow.execute(
            tenantId = rawDataParams.tenantId,
            entityKey = rawDataParams.entityKey,
            version = rawDataParams.version,
            schemaId = rawDataParams.schemaId,
            schemaVersion = rawDataParams.schemaVersion,
            payloadJson = rawDataParams.payloadJson
        )

        return when (ingestResult) {
            is IngestWorkflow.Result.Err -> IngestResult(
                entityKey = rawDataParams.entityKey.value,
                version = rawDataParams.version,
                success = false,
                error = ingestResult.error.toString()
            )
            is IngestWorkflow.Result.Ok -> IngestResult(
                entityKey = rawDataParams.entityKey.value,
                version = rawDataParams.version,
                success = true
            )
        }
    }

    /**
     * Compile만 실행 (동기) - 이미 Ingest된 데이터
     */
    suspend fun <T : EntityInput> compileOnly(input: T, version: Long): CompileResult {
        val tenantId = TenantId(input.tenantId)
        val entityKey = extractEntityKey(input)

        val slicingResult = slicingWorkflow.execute(
            tenantId = tenantId,
            entityKey = entityKey,
            version = version
        )

        return when (slicingResult) {
            is SlicingWorkflow.Result.Err -> CompileResult(
                entityKey = entityKey.value,
                version = version,
                slices = emptyList(),
                success = false,
                error = slicingResult.error.toString()
            )
            is SlicingWorkflow.Result.Ok -> CompileResult(
                entityKey = entityKey.value,
                version = version,
                slices = slicingResult.value.map { it.sliceType.name.lowercase() },
                success = true
            )
        }
    }

    /**
     * Compile 비동기 실행 (Outbox)
     */
    suspend fun <T : EntityInput> compileAsync(input: T, version: Long): DeployJob {
        val tenantId = TenantId(input.tenantId)
        val entityKey = extractEntityKey(input)

        val compileTaskEntry = OutboxEntry.create(
            aggregateType = AggregateType.RAW_DATA,
            aggregateId = "${tenantId.value}:${entityKey.value}",
            eventType = "CompileRequested",
            payload = buildJsonObject {
                put("payloadVersion", "1.0")
                put("tenantId", tenantId.value)
                put("entityKey", entityKey.value)
                put("version", version.toString())
                put("compileMode", "async")
            }.toString()
        )

        val jobId = when (val r = outboxRepository.insert(compileTaskEntry)) {
            is OutboxRepositoryPort.Result.Ok -> r.value.id.toString()
            is OutboxRepositoryPort.Result.Err -> throw RuntimeException("Outbox insert failed: ${r.error}")
        }

        return DeployJob(
            jobId = jobId,
            entityKey = entityKey.value,
            version = version.toString(),
            state = DeployState.QUEUED
        )
    }

    /**
     * Ship 실행 (항상 Outbox를 통해 비동기 처리)
     * 
     * @deprecated 동기 실행은 제거되었습니다. 항상 Outbox를 통해 비동기로 처리됩니다.
     *             shipAsync()를 사용하세요.
     */
    @Deprecated("Ship은 항상 Outbox를 통해 비동기로 처리됩니다. shipAsync()를 사용하세요.", ReplaceWith("shipAsync(input, version)"))
    suspend fun <T : EntityInput> shipSync(input: T, version: Long, sinkTypes: List<String>): ShipResult {
        // Outbox를 통해 비동기로 처리 (일관성 유지)
        val job = shipAsync(input, version)
        return ShipResult(
            entityKey = job.entityKey,
            version = job.version.toLong(),
            sinks = sinkTypes,
            success = true,
            error = null
        )
    }

    /**
     * Ship 비동기 실행 (Outbox)
     */
    suspend fun <T : EntityInput> shipAsync(input: T, version: Long): DeployJob {
        val tenantId = TenantId(input.tenantId)
        val entityKey = extractEntityKey(input)

        val shipTaskEntry = OutboxEntry.create(
            aggregateType = AggregateType.SLICE,
            aggregateId = "${tenantId.value}:${entityKey.value}",
            eventType = "ShipRequested",
            payload = buildJsonObject {
                put("payloadVersion", "1.0")
                put("tenantId", tenantId.value)
                put("entityKey", entityKey.value)
                put("version", version.toString())
                put("shipMode", "async")
            }.toString()
        )

        val jobId = when (val r = outboxRepository.insert(shipTaskEntry)) {
            is OutboxRepositoryPort.Result.Ok -> r.value.id.toString()
            is OutboxRepositoryPort.Result.Err -> throw RuntimeException("Outbox insert failed: ${r.error}")
        }

        return DeployJob(
            jobId = jobId,
            entityKey = entityKey.value,
            version = version.toString(),
            state = DeployState.QUEUED
        )
    }

    /**
     * 특정 Sink만 Ship 실행 (항상 Outbox를 통해 비동기 처리)
     * 
     * @deprecated 동기 실행은 제거되었습니다. 항상 Outbox를 통해 비동기로 처리됩니다.
     *             shipAsyncTo()를 사용하세요.
     */
    @Deprecated("Ship은 항상 Outbox를 통해 비동기로 처리됩니다. shipAsyncTo()를 사용하세요.", ReplaceWith("shipAsyncTo(input, version, sinks)"))
    suspend fun <T : EntityInput> shipSyncTo(input: T, version: Long, sinks: List<SinkSpec>): ShipResult {
        // Outbox를 통해 비동기로 처리 (일관성 유지)
        val job = shipAsyncTo(input, version, sinks)
        val sinkTypes = sinks.map { sinkSpecToType(it) }
        return ShipResult(
            entityKey = job.entityKey,
            version = job.version.toLong(),
            sinks = sinkTypes,
            success = true,
            error = null
        )
    }

    /**
     * 특정 Sink만 비동기 Ship (Outbox)
     */
    suspend fun <T : EntityInput> shipAsyncTo(input: T, version: Long, sinks: List<SinkSpec>): DeployJob {
        val tenantId = TenantId(input.tenantId)
        val entityKey = extractEntityKey(input)

        val jobIds = mutableListOf<String>()

        sinks.forEach { sink ->
            val shipTaskEntry = OutboxEntry.create(
                aggregateType = AggregateType.SLICE,
                aggregateId = "${tenantId.value}:${entityKey.value}",
                eventType = "ShipRequested",
                payload = buildJsonObject {
                    put("payloadVersion", "1.0")
                    put("tenantId", tenantId.value)
                    put("entityKey", entityKey.value)
                    put("version", version.toString())
                    put("sink", sinkSpecToType(sink))
                    put("shipMode", "async")
                }.toString()
            )

            // fail-closed: 에러 발생 시 즉시 예외 던짐
            when (val r = outboxRepository.insert(shipTaskEntry)) {
                is OutboxRepositoryPort.Result.Ok -> jobIds.add(r.value.id.toString())
                is OutboxRepositoryPort.Result.Err -> throw RuntimeException("Ship outbox insert failed: ${r.error}")
            }
        }

        return DeployJob(
            jobId = jobIds.firstOrNull() ?: throw RuntimeException("No sinks specified"),
            entityKey = entityKey.value,
            version = version.toString(),
            state = DeployState.QUEUED
        )
    }
}
