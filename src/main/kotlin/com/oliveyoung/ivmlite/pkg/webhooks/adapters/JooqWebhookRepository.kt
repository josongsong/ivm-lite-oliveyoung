package com.oliveyoung.ivmlite.pkg.webhooks.adapters

import com.oliveyoung.ivmlite.pkg.webhooks.domain.RetryPolicy
import com.oliveyoung.ivmlite.pkg.webhooks.domain.Webhook
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEvent
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * jOOQ 기반 Webhook Repository (PostgreSQL)
 */
class JooqWebhookRepository(
    private val dsl: DSLContext
) : WebhookRepositoryPort {

    private val logger = LoggerFactory.getLogger(JooqWebhookRepository::class.java)

    companion object {
        private val WEBHOOKS = DSL.table("webhooks")
        private val ID = DSL.field("id", UUID::class.java)
        private val NAME = DSL.field("name", String::class.java)
        private val URL = DSL.field("url", String::class.java)
        private val EVENTS = DSL.field("events", Array<String>::class.java)
        private val FILTERS = DSL.field("filters", JSONB::class.java)
        private val HEADERS = DSL.field("headers", JSONB::class.java)
        private val PAYLOAD_TEMPLATE = DSL.field("payload_template", String::class.java)
        private val IS_ACTIVE = DSL.field("is_active", Boolean::class.java)
        private val RETRY_POLICY = DSL.field("retry_policy", JSONB::class.java)
        private val SECRET_TOKEN = DSL.field("secret_token", String::class.java)
        private val CREATED_AT = DSL.field("created_at", OffsetDateTime::class.java)
        private val UPDATED_AT = DSL.field("updated_at", OffsetDateTime::class.java)

        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun findById(id: UUID): Webhook? = withContext(Dispatchers.IO) {
        try {
            dsl.select()
                .from(WEBHOOKS)
                .where(ID.eq(id))
                .fetchOne()
                ?.toWebhook()
        } catch (e: Exception) {
            logger.error("[JooqWebhookRepo] findById error: ${e.message}", e)
            null
        }
    }

    override suspend fun findAll(): List<Webhook> = withContext(Dispatchers.IO) {
        try {
            dsl.select()
                .from(WEBHOOKS)
                .orderBy(CREATED_AT.desc())
                .fetch()
                .map { it.toWebhook() }
        } catch (e: Exception) {
            logger.error("[JooqWebhookRepo] findAll error: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun findActive(): List<Webhook> = withContext(Dispatchers.IO) {
        try {
            dsl.select()
                .from(WEBHOOKS)
                .where(IS_ACTIVE.eq(true))
                .orderBy(CREATED_AT.desc())
                .fetch()
                .map { it.toWebhook() }
        } catch (e: Exception) {
            logger.error("[JooqWebhookRepo] findActive error: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun findByEvent(event: WebhookEvent): List<Webhook> = withContext(Dispatchers.IO) {
        try {
            // PostgreSQL array contains: events @> ARRAY['SLICE_CREATED']
            dsl.select()
                .from(WEBHOOKS)
                .where(IS_ACTIVE.eq(true))
                .and(DSL.condition("{0} @> ARRAY[{1}]::text[]", EVENTS, event.name))
                .fetch()
                .map { it.toWebhook() }
        } catch (e: Exception) {
            logger.error("[JooqWebhookRepo] findByEvent error: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun save(webhook: Webhook): Result<Webhook> = withContext(Dispatchers.IO) {
        try {
            val eventsArray = webhook.events.map { it.name }.toTypedArray()
            val filtersJson = JSONB.valueOf(json.encodeToString(webhook.filters))
            val headersJson = JSONB.valueOf(json.encodeToString(webhook.headers))
            val retryPolicyJson = JSONB.valueOf(json.encodeToString(webhook.retryPolicy.toMap()))

            val existing = dsl.select(ID)
                .from(WEBHOOKS)
                .where(ID.eq(webhook.id))
                .fetchOne()

            if (existing != null) {
                // UPDATE
                dsl.update(WEBHOOKS)
                    .set(NAME, webhook.name)
                    .set(URL, webhook.url)
                    .set(DSL.field("events", Any::class.java), eventsArray)
                    .set(FILTERS, filtersJson)
                    .set(HEADERS, headersJson)
                    .set(PAYLOAD_TEMPLATE, webhook.payloadTemplate)
                    .set(IS_ACTIVE, webhook.isActive)
                    .set(RETRY_POLICY, retryPolicyJson)
                    .set(SECRET_TOKEN, webhook.secretToken)
                    .where(ID.eq(webhook.id))
                    .execute()
            } else {
                // INSERT
                dsl.insertInto(WEBHOOKS)
                    .set(ID, webhook.id)
                    .set(NAME, webhook.name)
                    .set(URL, webhook.url)
                    .set(DSL.field("events", Any::class.java), eventsArray)
                    .set(FILTERS, filtersJson)
                    .set(HEADERS, headersJson)
                    .set(PAYLOAD_TEMPLATE, webhook.payloadTemplate)
                    .set(IS_ACTIVE, webhook.isActive)
                    .set(RETRY_POLICY, retryPolicyJson)
                    .set(SECRET_TOKEN, webhook.secretToken)
                    .execute()
            }

            Result.Ok(webhook)
        } catch (e: Exception) {
            logger.error("[JooqWebhookRepo] save error: ${e.message}", e)
            Result.Err(com.oliveyoung.ivmlite.shared.domain.errors.DomainError.StorageError(e.message ?: "Unknown error"))
        }
    }

    override suspend fun delete(id: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            val deleted = dsl.deleteFrom(WEBHOOKS)
                .where(ID.eq(id))
                .execute()
            deleted > 0
        } catch (e: Exception) {
            logger.error("[JooqWebhookRepo] delete error: ${e.message}", e)
            false
        }
    }

    override suspend fun exists(id: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            dsl.fetchExists(
                dsl.selectOne()
                    .from(WEBHOOKS)
                    .where(ID.eq(id))
            )
        } catch (e: Exception) {
            logger.error("[JooqWebhookRepo] exists error: ${e.message}", e)
            false
        }
    }

    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        try {
            dsl.selectCount()
                .from(WEBHOOKS)
                .fetchOne(0, Long::class.java) ?: 0L
        } catch (e: Exception) {
            logger.error("[JooqWebhookRepo] count error: ${e.message}", e)
            0L
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Record.toWebhook(): Webhook {
        val eventsArray = get("events") as? Array<String> ?: emptyArray()
        val events = eventsArray.mapNotNull { WebhookEvent.fromString(it) }.toSet()

        val filtersJsonb = get(FILTERS)
        val filters: Map<String, String> = try {
            if (filtersJsonb != null) {
                json.decodeFromString<Map<String, String>>(filtersJsonb.toString())
            } else emptyMap()
        } catch (_: Exception) { emptyMap() }

        val headersJsonb = get(HEADERS)
        val headers: Map<String, String> = try {
            if (headersJsonb != null) {
                json.decodeFromString<Map<String, String>>(headersJsonb.toString())
            } else emptyMap()
        } catch (_: Exception) { emptyMap() }

        val retryPolicyJsonb = get(RETRY_POLICY)
        val retryPolicy = try {
            if (retryPolicyJsonb != null) {
                val map = json.decodeFromString<Map<String, Double>>(retryPolicyJsonb.toString())
                RetryPolicy(
                    maxRetries = map["maxRetries"]?.toInt() ?: 5,
                    initialDelayMs = map["initialDelayMs"]?.toLong() ?: 1000L,
                    maxDelayMs = map["maxDelayMs"]?.toLong() ?: 60000L,
                    multiplier = map["multiplier"] ?: 2.0
                )
            } else RetryPolicy.DEFAULT
        } catch (_: Exception) { RetryPolicy.DEFAULT }

        return Webhook(
            id = get(ID) ?: UUID.randomUUID(),
            name = get(NAME) ?: "",
            url = get(URL) ?: "",
            events = events,
            filters = filters,
            headers = headers,
            payloadTemplate = get(PAYLOAD_TEMPLATE),
            isActive = get(IS_ACTIVE) ?: true,
            retryPolicy = retryPolicy,
            secretToken = get(SECRET_TOKEN),
            createdAt = get(CREATED_AT)?.toInstant() ?: java.time.Instant.now(),
            updatedAt = get(UPDATED_AT)?.toInstant() ?: java.time.Instant.now()
        )
    }

    private fun RetryPolicy.toMap(): Map<String, Any> = mapOf(
        "maxRetries" to maxRetries,
        "initialDelayMs" to initialDelayMs,
        "maxDelayMs" to maxDelayMs,
        "multiplier" to multiplier
    )
}
