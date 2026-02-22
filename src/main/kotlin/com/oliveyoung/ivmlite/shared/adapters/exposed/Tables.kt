package com.oliveyoung.ivmlite.shared.adapters.exposed

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Exposed Table 정의 - Flyway 마이그레이션 스키마의 Kotlin 매핑
 *
 * 코드 생성 불필요: 스키마를 직접 정의하여 타입 안전성 확보
 */

// ===== Alerts (V026) =====
object AlertsTable : Table("alerts") {
    val id = uuid("id")
    val ruleId = varchar("rule_id", 255)
    val name = varchar("name", 500)
    val description = text("description")
    val severity = varchar("severity", 50)
    val status = varchar("status", 50)
    val context = jsonb("context")
    val firedAt = timestampWithTimeZone("fired_at")
    val acknowledgedAt = timestampWithTimeZone("acknowledged_at").nullable()
    val acknowledgedBy = varchar("acknowledged_by", 255).nullable()
    val resolvedAt = timestampWithTimeZone("resolved_at").nullable()
    val silencedUntil = timestampWithTimeZone("silenced_until").nullable()
    val occurrences = integer("occurrences").default(1)
    val labels = jsonb("labels")

    override val primaryKey = PrimaryKey(id)
}

// ===== BackfillJobs (V027) =====
object BackfillJobsTable : Table("backfill_jobs") {
    val id = uuid("id")
    val name = varchar("name", 500)
    val description = text("description")
    val type = varchar("type", 100)
    val scope = jsonb("scope")
    val status = varchar("status", 50)
    val priority = integer("priority").default(5)
    val config = jsonb("config")
    val progress = jsonb("progress")
    val createdBy = varchar("created_by", 255)
    val createdAt = timestampWithTimeZone("created_at")
    val startedAt = timestampWithTimeZone("started_at").nullable()
    val completedAt = timestampWithTimeZone("completed_at").nullable()
    val failureReason = text("failure_reason").nullable()
    val dryRunResult = jsonb("dry_run_result").nullable()

    override val primaryKey = PrimaryKey(id)
}

