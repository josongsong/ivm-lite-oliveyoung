package com.oliveyoung.ivmlite.pkg.alerts.adapters

import com.oliveyoung.ivmlite.pkg.alerts.domain.Alert
import com.oliveyoung.ivmlite.pkg.alerts.domain.AlertSeverity
import com.oliveyoung.ivmlite.pkg.alerts.domain.AlertStatus
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertFilter
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertRepositoryPort
import com.oliveyoung.ivmlite.pkg.alerts.ports.AlertStats
import com.oliveyoung.ivmlite.shared.adapters.exposed.AlertsTable
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Exposed 기반 Alert Repository (PostgreSQL)
 *
 * upsert: ID 기반 insert or update
 */
class ExposedAlertRepository(
    private val database: Database
) : AlertRepositoryPort {

    private val logger = LoggerFactory.getLogger(ExposedAlertRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(alert: Alert): Result<Alert> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val exists = AlertsTable.selectAll()
                        .where { AlertsTable.id eq alert.id }
                        .count() > 0

                    if (exists) {
                        AlertsTable.update({ AlertsTable.id eq alert.id }) {
                            it[ruleId] = alert.ruleId
                            it[name] = alert.name
                            it[description] = alert.description
                            it[severity] = alert.severity.name
                            it[status] = alert.status.name
                            it[context] = serializeMap(alert.context)
                            it[firedAt] = alert.firedAt.atOffset(ZoneOffset.UTC)
                            it[acknowledgedAt] = alert.acknowledgedAt?.atOffset(ZoneOffset.UTC)
                            it[acknowledgedBy] = alert.acknowledgedBy
                            it[resolvedAt] = alert.resolvedAt?.atOffset(ZoneOffset.UTC)
                            it[silencedUntil] = alert.silencedUntil?.atOffset(ZoneOffset.UTC)
                            it[occurrences] = alert.occurrences
                            it[labels] = serializeMap(alert.labels)
                        }
                    } else {
                        AlertsTable.insert {
                            it[id] = alert.id
                            it[ruleId] = alert.ruleId
                            it[name] = alert.name
                            it[description] = alert.description
                            it[severity] = alert.severity.name
                            it[status] = alert.status.name
                            it[context] = serializeMap(alert.context)
                            it[firedAt] = alert.firedAt.atOffset(ZoneOffset.UTC)
                            it[acknowledgedAt] = alert.acknowledgedAt?.atOffset(ZoneOffset.UTC)
                            it[acknowledgedBy] = alert.acknowledgedBy
                            it[resolvedAt] = alert.resolvedAt?.atOffset(ZoneOffset.UTC)
                            it[silencedUntil] = alert.silencedUntil?.atOffset(ZoneOffset.UTC)
                            it[occurrences] = alert.occurrences
                            it[labels] = serializeMap(alert.labels)
                        }
                    }
                }
                Result.Ok(alert)
            } catch (e: Exception) {
                logger.error("Failed to save alert: ${alert.id}", e)
                Result.Err(DomainError.StorageError("Failed to save alert: ${e.message}"))
            }
        }

    override suspend fun findById(id: UUID): Result<Alert?> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val row = AlertsTable.selectAll()
                        .where { AlertsTable.id eq id }
                        .firstOrNull()
                    Result.Ok(row?.let { mapToAlert(it) })
                }
            } catch (e: Exception) {
                logger.error("Failed to find alert: $id", e)
                Result.Err(DomainError.StorageError("Failed to find alert: ${e.message}"))
            }
        }

    override suspend fun findActiveByRuleId(ruleId: String): Result<Alert?> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val row = AlertsTable.selectAll()
                        .where {
                            AlertsTable.ruleId eq ruleId and
                                (AlertsTable.status inList listOf(
                                    AlertStatus.FIRING.name,
                                    AlertStatus.ACKNOWLEDGED.name
                                ))
                        }
                        .orderBy(AlertsTable.firedAt, SortOrder.DESC)
                        .limit(1)
                        .firstOrNull()
                    Result.Ok(row?.let { mapToAlert(it) })
                }
            } catch (e: Exception) {
                logger.error("Failed to find active alert by ruleId: $ruleId", e)
                Result.Err(DomainError.StorageError("Failed to find active alert: ${e.message}"))
            }
        }

    override suspend fun findByStatus(status: AlertStatus, limit: Int): Result<List<Alert>> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val records = AlertsTable.selectAll()
                        .where { AlertsTable.status eq status.name }
                        .orderBy(AlertsTable.firedAt, SortOrder.DESC)
                        .limit(limit)
                        .map { mapToAlert(it) }
                    Result.Ok(records)
                }
            } catch (e: Exception) {
                logger.error("Failed to find alerts by status: $status", e)
                Result.Err(DomainError.StorageError("Failed to find alerts: ${e.message}"))
            }
        }

    override suspend fun findAllActive(): Result<List<Alert>> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val records = AlertsTable.selectAll()
                        .where {
                            AlertsTable.status inList listOf(
                                AlertStatus.FIRING.name,
                                AlertStatus.ACKNOWLEDGED.name
                            )
                        }
                        .orderBy(AlertsTable.firedAt, SortOrder.DESC)
                        .map { mapToAlert(it) }
                    Result.Ok(records)
                }
            } catch (e: Exception) {
                logger.error("Failed to find active alerts", e)
                Result.Err(DomainError.StorageError("Failed to find active alerts: ${e.message}"))
            }
        }

    override suspend fun findByFilter(filter: AlertFilter): Result<List<Alert>> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    var query = AlertsTable.selectAll()

                    filter.statuses?.let { statuses ->
                        query = query.andWhere {
                            AlertsTable.status inList statuses.map { it.name }
                        }
                    }
                    filter.severities?.let { severities ->
                        query = query.andWhere {
                            AlertsTable.severity inList severities.map { it.name }
                        }
                    }
                    filter.ruleIds?.let { ruleIds ->
                        query = query.andWhere {
                            AlertsTable.ruleId inList ruleIds
                        }
                    }
                    filter.fromTime?.let { from ->
                        query = query.andWhere {
                            AlertsTable.firedAt greaterEq from.atOffset(ZoneOffset.UTC)
                        }
                    }
                    filter.toTime?.let { to ->
                        query = query.andWhere {
                            AlertsTable.firedAt lessEq to.atOffset(ZoneOffset.UTC)
                        }
                    }

                    val records = query
                        .orderBy(AlertsTable.firedAt, SortOrder.DESC)
                        .limit(filter.limit, filter.offset.toLong())
                        .map { mapToAlert(it) }

                    Result.Ok(records)
                }
            } catch (e: Exception) {
                logger.error("Failed to find alerts by filter", e)
                Result.Err(DomainError.StorageError("Failed to find alerts: ${e.message}"))
            }
        }

    override suspend fun findRecent(limit: Int): Result<List<Alert>> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val records = AlertsTable.selectAll()
                        .orderBy(AlertsTable.firedAt, SortOrder.DESC)
                        .limit(limit)
                        .map { mapToAlert(it) }
                    Result.Ok(records)
                }
            } catch (e: Exception) {
                logger.error("Failed to find recent alerts", e)
                Result.Err(DomainError.StorageError("Failed to find recent alerts: ${e.message}"))
            }
        }

    override suspend fun findExpiredSilenced(now: Instant): Result<List<Alert>> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val records = AlertsTable.selectAll()
                        .where {
                            AlertsTable.status eq AlertStatus.SILENCED.name and
                                (AlertsTable.silencedUntil less now.atOffset(ZoneOffset.UTC))
                        }
                        .map { mapToAlert(it) }
                    Result.Ok(records)
                }
            } catch (e: Exception) {
                logger.error("Failed to find expired silenced alerts", e)
                Result.Err(DomainError.StorageError("Failed to find expired silenced alerts: ${e.message}"))
            }
        }

    override suspend fun getStats(): Result<AlertStats> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val allAlerts = AlertsTable.selectAll().map { mapToAlert(it) }
                    val active = allAlerts.filter { it.isActive() }
                    val yesterday = Instant.now().minus(24, ChronoUnit.HOURS)

                    val stats = AlertStats(
                        totalActive = active.size,
                        byStatus = allAlerts.groupBy { it.status }.mapValues { it.value.size },
                        bySeverity = active.groupBy { it.severity }.mapValues { it.value.size },
                        recentFiringCount24h = allAlerts.count {
                            it.status == AlertStatus.FIRING && it.firedAt.isAfter(yesterday)
                        }
                    )
                    Result.Ok(stats)
                }
            } catch (e: Exception) {
                logger.error("Failed to get alert stats", e)
                Result.Err(DomainError.StorageError("Failed to get alert stats: ${e.message}"))
            }
        }

    override suspend fun deleteOlderThan(before: Instant): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                transaction(database) {
                    val count = AlertsTable.deleteWhere {
                        AlertsTable.status eq AlertStatus.RESOLVED.name and
                            (AlertsTable.resolvedAt less before.atOffset(ZoneOffset.UTC))
                    }
                    Result.Ok(count)
                }
            } catch (e: Exception) {
                logger.error("Failed to delete old alerts", e)
                Result.Err(DomainError.StorageError("Failed to delete old alerts: ${e.message}"))
            }
        }

    // ===== Mapping =====

    private fun mapToAlert(row: ResultRow): Alert {
        return Alert(
            id = row[AlertsTable.id],
            ruleId = row[AlertsTable.ruleId],
            name = row[AlertsTable.name],
            description = row[AlertsTable.description],
            severity = AlertSeverity.valueOf(row[AlertsTable.severity]),
            status = AlertStatus.valueOf(row[AlertsTable.status]),
            context = deserializeMap(row[AlertsTable.context]),
            firedAt = row[AlertsTable.firedAt].toInstant(),
            acknowledgedAt = row[AlertsTable.acknowledgedAt]?.toInstant(),
            acknowledgedBy = row[AlertsTable.acknowledgedBy],
            resolvedAt = row[AlertsTable.resolvedAt]?.toInstant(),
            silencedUntil = row[AlertsTable.silencedUntil]?.toInstant(),
            occurrences = row[AlertsTable.occurrences],
            labels = deserializeMap(row[AlertsTable.labels]),
        )
    }

    // ===== JSON Serialization =====

    private fun serializeMap(map: Map<String, String>): String =
        buildJsonObject {
            map.forEach { (k, v) -> put(k, v) }
        }.toString()

    private fun deserializeMap(jsonStr: String): Map<String, String> {
        if (jsonStr.isBlank() || jsonStr == "{}") return emptyMap()
        return json.parseToJsonElement(jsonStr).jsonObject.entries.associate { (k, v) ->
            k to (v.jsonPrimitive.contentOrNull ?: "")
        }
    }
}
