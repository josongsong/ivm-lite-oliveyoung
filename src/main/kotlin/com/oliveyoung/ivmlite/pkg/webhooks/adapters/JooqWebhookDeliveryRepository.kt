package com.oliveyoung.ivmlite.pkg.webhooks.adapters

import com.oliveyoung.ivmlite.pkg.webhooks.domain.DeliveryStatus
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookDelivery
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEvent
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DeliveryFilter
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DeliveryStats
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookDeliveryRepositoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * jOOQ 기반 Webhook Delivery Repository (PostgreSQL)
 */
class JooqWebhookDeliveryRepository(
    private val dsl: DSLContext
) : WebhookDeliveryRepositoryPort {

    private val logger = LoggerFactory.getLogger(JooqWebhookDeliveryRepository::class.java)

    companion object {
        private val DELIVERIES = DSL.table("webhook_deliveries")
        private val ID = DSL.field("id", UUID::class.java)
        private val WEBHOOK_ID = DSL.field("webhook_id", UUID::class.java)
        private val EVENT_TYPE = DSL.field("event_type", String::class.java)
        private val EVENT_PAYLOAD = DSL.field("event_payload", JSONB::class.java)
        private val REQUEST_HEADERS = DSL.field("request_headers", JSONB::class.java)
        private val REQUEST_BODY = DSL.field("request_body", String::class.java)
        private val RESPONSE_STATUS = DSL.field("response_status", Int::class.java)
        private val RESPONSE_BODY = DSL.field("response_body", String::class.java)
        private val LATENCY_MS = DSL.field("latency_ms", Int::class.java)
        private val STATUS = DSL.field("status", String::class.java)
        private val ERROR_MESSAGE = DSL.field("error_message", String::class.java)
        private val ATTEMPT_COUNT = DSL.field("attempt_count", Int::class.java)
        private val NEXT_RETRY_AT = DSL.field("next_retry_at", OffsetDateTime::class.java)
        private val CREATED_AT = DSL.field("created_at", OffsetDateTime::class.java)

        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun save(delivery: WebhookDelivery): WebhookDelivery = withContext(Dispatchers.IO) {
        try {
            val existing = dsl.select(ID)
                .from(DELIVERIES)
                .where(ID.eq(delivery.id))
                .fetchOne()

            val payloadJson = JSONB.valueOf(delivery.eventPayload)
            val reqHeadersJson = if (delivery.requestHeaders.isNotEmpty()) {
                JSONB.valueOf(json.encodeToString(delivery.requestHeaders))
            } else null
            val nextRetryAt = delivery.nextRetryAt?.atOffset(ZoneOffset.UTC)

            if (existing != null) {
                // UPDATE
                dsl.update(DELIVERIES)
                    .set(STATUS, delivery.status.name)
                    .set(RESPONSE_STATUS, delivery.responseStatus)
                    .set(RESPONSE_BODY, delivery.responseBody)
                    .set(LATENCY_MS, delivery.latencyMs)
                    .set(ERROR_MESSAGE, delivery.errorMessage)
                    .set(ATTEMPT_COUNT, delivery.attemptCount)
                    .set(NEXT_RETRY_AT, nextRetryAt)
                    .where(ID.eq(delivery.id))
                    .execute()
            } else {
                // INSERT
                dsl.insertInto(DELIVERIES)
                    .set(ID, delivery.id)
                    .set(WEBHOOK_ID, delivery.webhookId)
                    .set(EVENT_TYPE, delivery.eventType.name)
                    .set(EVENT_PAYLOAD, payloadJson)
                    .set(REQUEST_HEADERS, reqHeadersJson)
                    .set(REQUEST_BODY, delivery.requestBody)
                    .set(RESPONSE_STATUS, delivery.responseStatus)
                    .set(RESPONSE_BODY, delivery.responseBody)
                    .set(LATENCY_MS, delivery.latencyMs)
                    .set(STATUS, delivery.status.name)
                    .set(ERROR_MESSAGE, delivery.errorMessage)
                    .set(ATTEMPT_COUNT, delivery.attemptCount)
                    .set(NEXT_RETRY_AT, nextRetryAt)
                    .execute()
            }

            delivery
        } catch (e: Exception) {
            logger.error("[JooqDeliveryRepo] save error: ${e.message}", e)
            delivery
        }
    }

    override suspend fun findById(id: UUID): WebhookDelivery? = withContext(Dispatchers.IO) {
        try {
            dsl.select()
                .from(DELIVERIES)
                .where(ID.eq(id))
                .fetchOne()
                ?.toDelivery()
        } catch (e: Exception) {
            logger.error("[JooqDeliveryRepo] findById error: ${e.message}", e)
            null
        }
    }

    override suspend fun findByWebhookId(webhookId: UUID, limit: Int, offset: Int): List<WebhookDelivery> = withContext(Dispatchers.IO) {
        try {
            dsl.select()
                .from(DELIVERIES)
                .where(WEBHOOK_ID.eq(webhookId))
                .orderBy(CREATED_AT.desc())
                .limit(limit)
                .offset(offset)
                .fetch()
                .map { it.toDelivery() }
        } catch (e: Exception) {
            logger.error("[JooqDeliveryRepo] findByWebhookId error: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun findByFilter(filter: DeliveryFilter): List<WebhookDelivery> = withContext(Dispatchers.IO) {
        try {
            var query = dsl.select()
                .from(DELIVERIES)
                .where(DSL.trueCondition())

            filter.webhookId?.let {
                query = query.and(WEBHOOK_ID.eq(it))
            }
            filter.status?.let {
                query = query.and(STATUS.eq(it.name))
            }
            filter.fromDate?.let {
                query = query.and(CREATED_AT.ge(it.atOffset(ZoneOffset.UTC)))
            }
            filter.toDate?.let {
                query = query.and(CREATED_AT.le(it.atOffset(ZoneOffset.UTC)))
            }

            query
                .orderBy(CREATED_AT.desc())
                .limit(filter.limit)
                .fetch()
                .map { it.toDelivery() }
        } catch (e: Exception) {
            logger.error("[JooqDeliveryRepo] findByFilter error: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun findPendingRetries(before: Instant): List<WebhookDelivery> = withContext(Dispatchers.IO) {
        try {
            val beforeTime = before.atOffset(ZoneOffset.UTC)
            dsl.select()
                .from(DELIVERIES)
                .where(STATUS.eq(DeliveryStatus.RETRYING.name))
                .and(NEXT_RETRY_AT.le(beforeTime))
                .orderBy(NEXT_RETRY_AT.asc())
                .fetch()
                .map { it.toDelivery() }
        } catch (e: Exception) {
            logger.error("[JooqDeliveryRepo] findPendingRetries error: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun getOverallStats(): DeliveryStats = withContext(Dispatchers.IO) {
        try {
            val total = dsl.selectCount().from(DELIVERIES).fetchOne(0, Long::class.java) ?: 0L
            val success = dsl.selectCount().from(DELIVERIES).where(STATUS.eq("SUCCESS")).fetchOne(0, Long::class.java) ?: 0L
            val failed = dsl.selectCount().from(DELIVERIES).where(STATUS.eq("FAILED")).fetchOne(0, Long::class.java) ?: 0L
            val pending = dsl.selectCount().from(DELIVERIES).where(STATUS.eq("PENDING")).fetchOne(0, Long::class.java) ?: 0L
            val retrying = dsl.selectCount().from(DELIVERIES).where(STATUS.eq("RETRYING")).fetchOne(0, Long::class.java) ?: 0L

            val avgLatency = dsl.select(DSL.avg(LATENCY_MS))
                .from(DELIVERIES)
                .where(LATENCY_MS.isNotNull)
                .fetchOne(0, Double::class.java) ?: 0.0

            val successRate = if (total > 0) (success.toDouble() / total) * 100 else 0.0

            DeliveryStats(
                total = total,
                success = success,
                failed = failed,
                pending = pending,
                retrying = retrying,
                averageLatencyMs = avgLatency,
                successRate = successRate
            )
        } catch (e: Exception) {
            logger.error("[JooqDeliveryRepo] getOverallStats error: ${e.message}", e)
            DeliveryStats.EMPTY
        }
    }

    override suspend fun getStats(webhookId: UUID): DeliveryStats = withContext(Dispatchers.IO) {
        try {
            val condition = WEBHOOK_ID.eq(webhookId)
            val total = dsl.selectCount().from(DELIVERIES).where(condition).fetchOne(0, Long::class.java) ?: 0L
            val success = dsl.selectCount().from(DELIVERIES).where(condition.and(STATUS.eq("SUCCESS"))).fetchOne(0, Long::class.java) ?: 0L
            val failed = dsl.selectCount().from(DELIVERIES).where(condition.and(STATUS.eq("FAILED"))).fetchOne(0, Long::class.java) ?: 0L
            val pending = dsl.selectCount().from(DELIVERIES).where(condition.and(STATUS.eq("PENDING"))).fetchOne(0, Long::class.java) ?: 0L
            val retrying = dsl.selectCount().from(DELIVERIES).where(condition.and(STATUS.eq("RETRYING"))).fetchOne(0, Long::class.java) ?: 0L

            val avgLatency = dsl.select(DSL.avg(LATENCY_MS))
                .from(DELIVERIES)
                .where(condition.and(LATENCY_MS.isNotNull))
                .fetchOne(0, Double::class.java) ?: 0.0

            val successRate = if (total > 0) (success.toDouble() / total) * 100 else 0.0

            DeliveryStats(
                total = total,
                success = success,
                failed = failed,
                pending = pending,
                retrying = retrying,
                averageLatencyMs = avgLatency,
                successRate = successRate
            )
        } catch (e: Exception) {
            logger.error("[JooqDeliveryRepo] getStats error: ${e.message}", e)
            DeliveryStats.EMPTY
        }
    }

    override suspend fun deleteOlderThan(before: Instant): Int = withContext(Dispatchers.IO) {
        try {
            val beforeTime = before.atOffset(ZoneOffset.UTC)
            dsl.deleteFrom(DELIVERIES)
                .where(CREATED_AT.lt(beforeTime))
                .execute()
        } catch (e: Exception) {
            logger.error("[JooqDeliveryRepo] deleteOlderThan error: ${e.message}", e)
            0
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Record.toDelivery(): WebhookDelivery {
        val reqHeadersJsonb = get(REQUEST_HEADERS)
        val requestHeaders: Map<String, String> = try {
            if (reqHeadersJsonb != null) {
                json.decodeFromString<Map<String, String>>(reqHeadersJsonb.toString())
            } else emptyMap()
        } catch (_: Exception) { emptyMap() }

        return WebhookDelivery(
            id = get(ID) ?: UUID.randomUUID(),
            webhookId = get(WEBHOOK_ID) ?: UUID.randomUUID(),
            eventType = WebhookEvent.fromString(get(EVENT_TYPE) ?: "") ?: WebhookEvent.ERROR,
            eventPayload = get(EVENT_PAYLOAD)?.toString() ?: "{}",
            requestHeaders = requestHeaders,
            requestBody = get(REQUEST_BODY),
            responseStatus = get(RESPONSE_STATUS),
            responseBody = get(RESPONSE_BODY),
            latencyMs = get(LATENCY_MS),
            status = DeliveryStatus.fromString(get(STATUS) ?: "PENDING"),
            errorMessage = get(ERROR_MESSAGE),
            attemptCount = get(ATTEMPT_COUNT) ?: 1,
            nextRetryAt = get(NEXT_RETRY_AT)?.toInstant(),
            createdAt = get(CREATED_AT)?.toInstant() ?: Instant.now()
        )
    }
}
