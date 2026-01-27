package com.oliveyoung.ivmlite.pkg.fanout.application

import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutConfig
import com.oliveyoung.ivmlite.pkg.fanout.domain.FanoutPriority
import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import com.oliveyoung.ivmlite.pkg.rawdata.domain.OutboxEntry
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * RFC-IMPL-012: Fanout Event Handler
 *
 * Outbox에서 엔티티 변경 이벤트를 수신하여 fanout을 트리거.
 *
 * ## 이벤트 타입
 * - **EntityUpdated**: 엔티티가 업데이트됨 → downstream fanout 트리거
 * - **EntityCreated**: 엔티티가 생성됨 → downstream fanout 트리거 (optional)
 * - **EntityDeleted**: 엔티티가 삭제됨 → downstream tombstone 전파
 * - **FanoutRequested**: 명시적 fanout 요청 (수동 트리거)
 *
 * ## 사용 시나리오
 * 1. Brand 업데이트 → 이 Brand를 참조하는 모든 Product 재슬라이싱
 * 2. Category 업데이트 → 이 Category를 참조하는 모든 Product 재슬라이싱
 * 3. 수동 fanout 요청 → 특정 엔티티의 downstream 전체 재처리
 */
class FanoutEventHandler(
    private val fanoutWorkflow: FanoutWorkflow,
    private val defaultConfig: FanoutConfig = FanoutConfig.DEFAULT,
) : OutboxPollingWorker.EventHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 슬라이스 이벤트 처리 (downstream fanout 트리거)
     */
    override suspend fun handleSliceEvent(entry: OutboxEntry) {
        when (entry.eventType) {
            "EntityUpdated" -> processEntityUpdated(entry)
            "EntityCreated" -> processEntityCreated(entry)
            "EntityDeleted" -> processEntityDeleted(entry)
            "FanoutRequested" -> processFanoutRequested(entry)
            else -> {
                logger.trace("Unknown SLICE event type for fanout: {}", entry.eventType)
            }
        }
    }

    /**
     * ChangeSet 이벤트 처리 (no-op, 필요시 확장)
     */
    override suspend fun handleChangeSetEvent(entry: OutboxEntry) {
        // ChangeSet 이벤트는 fanout과 직접 관련 없음
        logger.trace("ChangeSetEvent received (no-op for fanout): {}", entry.id)
    }

    /**
     * EntityUpdated 이벤트 처리
     *
     * 엔티티가 업데이트되면 이를 참조하는 downstream 엔티티들을 찾아 재슬라이싱.
     */
    private suspend fun processEntityUpdated(entry: OutboxEntry) {
        val payload = try {
            json.decodeFromString<EntityUpdatedPayload>(entry.payload)
        } catch (e: Exception) {
            logger.error("Failed to parse EntityUpdated payload: {}", e.message)
            throw OutboxPollingWorker.ProcessingException("Invalid EntityUpdated payload: ${e.message}")
        }

        logger.info(
            "Processing EntityUpdated: type={}, key={}, version={}",
            payload.entityType, payload.entityKey, payload.version
        )

        // Fanout 실행
        val config = determineConfig(payload.entityType, payload.priority)
        val result = fanoutWorkflow.onEntityChange(
            tenantId = TenantId(payload.tenantId),
            upstreamEntityType = payload.entityType,
            upstreamEntityKey = EntityKey(payload.entityKey),
            upstreamVersion = payload.version,
            overrideConfig = config,
        )

        when (result) {
            is FanoutWorkflow.Result.Ok -> {
                val r = result.value
                logger.info(
                    "Fanout completed: type={}, key={}, processed={}, skipped={}, failed={}",
                    payload.entityType, payload.entityKey,
                    r.processedCount, r.skippedCount, r.failedCount
                )

                if (r.failedCount > 0) {
                    logger.warn("Fanout had {} failures for {}#{}", r.failedCount, payload.entityType, payload.entityKey)
                }
            }
            is FanoutWorkflow.Result.Err -> {
                logger.error("Fanout failed for {}#{}: {}", payload.entityType, payload.entityKey, result.error)
                throw OutboxPollingWorker.ProcessingException("Fanout failed: ${result.error}")
            }
        }
    }

    /**
     * EntityCreated 이벤트 처리
     *
     * 새 엔티티가 생성되면 이를 참조할 수 있는 downstream은 아직 없음.
     * 하지만 일부 시나리오에서는 기존 downstream에 영향을 줄 수 있음 (예: 새 카테고리).
     */
    private suspend fun processEntityCreated(entry: OutboxEntry) {
        val payload = try {
            json.decodeFromString<EntityCreatedPayload>(entry.payload)
        } catch (e: Exception) {
            logger.error("Failed to parse EntityCreated payload: {}", e.message)
            throw OutboxPollingWorker.ProcessingException("Invalid EntityCreated payload: ${e.message}")
        }

        logger.info(
            "Processing EntityCreated: type={}, key={}, version={}",
            payload.entityType, payload.entityKey, payload.version
        )

        // 대부분의 경우 새 엔티티는 아직 참조하는 downstream이 없음
        // fanout 실행하지만 결과는 빈 것으로 예상
        val result = fanoutWorkflow.onEntityChange(
            tenantId = TenantId(payload.tenantId),
            upstreamEntityType = payload.entityType,
            upstreamEntityKey = EntityKey(payload.entityKey),
            upstreamVersion = payload.version,
        )

        when (result) {
            is FanoutWorkflow.Result.Ok -> {
                val r = result.value
                if (r.totalAffected > 0) {
                    logger.info(
                        "EntityCreated triggered fanout: type={}, affected={}",
                        payload.entityType, r.totalAffected
                    )
                }
            }
            is FanoutWorkflow.Result.Err -> {
                logger.error("Fanout failed for new entity {}#{}: {}", payload.entityType, payload.entityKey, result.error)
                // 새 엔티티의 경우 fanout 실패는 치명적이지 않음 - 경고만 기록
            }
        }
    }

    /**
     * EntityDeleted 이벤트 처리
     *
     * 엔티티가 삭제되면 이를 참조하는 downstream 엔티티들에 tombstone 전파.
     * 실제 삭제 여부는 downstream의 join.required 설정에 따라 다름.
     */
    private suspend fun processEntityDeleted(entry: OutboxEntry) {
        val payload = try {
            json.decodeFromString<EntityDeletedPayload>(entry.payload)
        } catch (e: Exception) {
            logger.error("Failed to parse EntityDeleted payload: {}", e.message)
            throw OutboxPollingWorker.ProcessingException("Invalid EntityDeleted payload: ${e.message}")
        }

        logger.warn(
            "Processing EntityDeleted: type={}, key={} - downstream may be affected",
            payload.entityType, payload.entityKey
        )

        // 삭제된 엔티티도 fanout 트리거 (downstream은 join fail로 처리해야 함)
        val result = fanoutWorkflow.onEntityChange(
            tenantId = TenantId(payload.tenantId),
            upstreamEntityType = payload.entityType,
            upstreamEntityKey = EntityKey(payload.entityKey),
            upstreamVersion = payload.version,
        )

        when (result) {
            is FanoutWorkflow.Result.Ok -> {
                val r = result.value
                if (r.totalAffected > 0) {
                    logger.warn(
                        "EntityDeleted affected {} downstream entities for {}#{}",
                        r.totalAffected, payload.entityType, payload.entityKey
                    )
                }
            }
            is FanoutWorkflow.Result.Err -> {
                logger.error("Fanout failed for deleted entity {}#{}: {}", payload.entityType, payload.entityKey, result.error)
                throw OutboxPollingWorker.ProcessingException("Fanout failed for deleted entity: ${result.error}")
            }
        }
    }

    /**
     * FanoutRequested 이벤트 처리 (명시적 수동 트리거)
     */
    private suspend fun processFanoutRequested(entry: OutboxEntry) {
        val payload = try {
            json.decodeFromString<FanoutRequestedPayload>(entry.payload)
        } catch (e: Exception) {
            logger.error("Failed to parse FanoutRequested payload: {}", e.message)
            throw OutboxPollingWorker.ProcessingException("Invalid FanoutRequested payload: ${e.message}")
        }

        logger.info(
            "Processing FanoutRequested: type={}, key={}, priority={}",
            payload.entityType, payload.entityKey, payload.priority
        )

        val config = payload.configOverride?.toFanoutConfig() ?: determineConfig(payload.entityType, payload.priority)

        val result = fanoutWorkflow.onEntityChange(
            tenantId = TenantId(payload.tenantId),
            upstreamEntityType = payload.entityType,
            upstreamEntityKey = EntityKey(payload.entityKey),
            upstreamVersion = payload.version,
            overrideConfig = config,
        )

        when (result) {
            is FanoutWorkflow.Result.Ok -> {
                val r = result.value
                logger.info(
                    "Manual fanout completed: type={}, key={}, processed={}, status={}",
                    payload.entityType, payload.entityKey, r.processedCount, r.status
                )
            }
            is FanoutWorkflow.Result.Err -> {
                logger.error("Manual fanout failed for {}#{}: {}", payload.entityType, payload.entityKey, result.error)
                throw OutboxPollingWorker.ProcessingException("Manual fanout failed: ${result.error}")
            }
        }
    }

    /**
     * 엔티티 타입과 우선순위에 따른 설정 결정
     */
    private fun determineConfig(entityType: String, priority: String?): FanoutConfig {
        val fanoutPriority = when (priority?.uppercase()) {
            "CRITICAL" -> FanoutPriority.CRITICAL
            "HIGH" -> FanoutPriority.HIGH
            "NORMAL", null -> FanoutPriority.NORMAL
            "LOW" -> FanoutPriority.LOW
            "BACKGROUND" -> FanoutPriority.BACKGROUND
            else -> FanoutPriority.NORMAL
        }

        // 엔티티 타입에 따른 기본 설정 조정
        return when (entityType.lowercase()) {
            "brand", "category" -> {
                // 브랜드/카테고리는 많은 Product에 영향 줄 수 있음 → 보수적 설정
                defaultConfig.copy(
                    priority = fanoutPriority,
                    batchSize = minOf(defaultConfig.batchSize, 50),  // 작은 배치
                )
            }
            "price", "stock", "inventory" -> {
                // 가격/재고는 실시간성 중요 → 빠른 처리
                defaultConfig.copy(
                    priority = if (fanoutPriority.weight < FanoutPriority.HIGH.weight) {
                        FanoutPriority.HIGH
                    } else {
                        fanoutPriority
                    },
                )
            }
            else -> defaultConfig.copy(priority = fanoutPriority)
        }
    }

    // ===== Payload DTOs =====

    @Serializable
    data class EntityUpdatedPayload(
        val tenantId: String,
        val entityType: String,
        val entityKey: String,
        val version: Long,
        val priority: String? = null,
        val changedFields: List<String> = emptyList(),
    )

    @Serializable
    data class EntityCreatedPayload(
        val tenantId: String,
        val entityType: String,
        val entityKey: String,
        val version: Long,
    )

    @Serializable
    data class EntityDeletedPayload(
        val tenantId: String,
        val entityType: String,
        val entityKey: String,
        val version: Long,
    )

    @Serializable
    data class FanoutRequestedPayload(
        val tenantId: String,
        val entityType: String,
        val entityKey: String,
        val version: Long,
        val priority: String? = null,
        val configOverride: FanoutConfigOverride? = null,
    )

    @Serializable
    data class FanoutConfigOverride(
        val batchSize: Int? = null,
        val maxFanout: Int? = null,
        val enabled: Boolean? = null,
    ) {
        fun toFanoutConfig(): FanoutConfig {
            return FanoutConfig(
                enabled = enabled ?: true,
                batchSize = batchSize ?: FanoutConfig.DEFAULT_BATCH_SIZE,
                maxFanout = maxFanout ?: FanoutConfig.DEFAULT_MAX_FANOUT,
            )
        }
    }
}
