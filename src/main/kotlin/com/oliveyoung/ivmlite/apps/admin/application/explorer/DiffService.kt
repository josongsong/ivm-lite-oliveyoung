package com.oliveyoung.ivmlite.apps.admin.application.explorer

import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory

/**
 * 버전 비교(Diff) 서비스
 *
 * P0: SRP 준수 - 버전 비교 기능만 담당
 */
class DiffService(
    private val rawDataRepo: RawDataRepositoryPort
) {
    private val logger = LoggerFactory.getLogger(DiffService::class.java)

    /**
     * 두 버전 간 Diff 비교
     */
    suspend fun diffVersions(
        tenantId: String,
        entityKey: String,
        fromVersion: Long,
        toVersion: Long
    ): Result<VersionDiffResult> {
        val tenant = TenantId(tenantId)
        val entity = EntityKey(entityKey)

        return Result.catch {
            val fromRecord = when (val result = rawDataRepo.get(tenant, entity, fromVersion)) {
                is Result.Ok -> result.value
                is Result.Err -> throw result.error
            }

            val toRecord = when (val result = rawDataRepo.get(tenant, entity, toVersion)) {
                is Result.Ok -> result.value
                is Result.Err -> throw result.error
            }

            val changes = computeJsonDiff(fromRecord.payload, toRecord.payload)

            VersionDiffResult(
                entityKey = entityKey,
                fromVersion = fromVersion,
                toVersion = toVersion,
                fromData = ExplorerUtils.parseJsonSafe(fromRecord.payload),
                toData = ExplorerUtils.parseJsonSafe(toRecord.payload),
                changes = changes
            )
        }
    }

    /**
     * JSON Diff 계산
     */
    private fun computeJsonDiff(from: String, to: String): List<DiffChange> {
        val changes = mutableListOf<DiffChange>()

        val fromJson = ExplorerUtils.parseJsonSafe(from)?.jsonObject ?: return changes
        val toJson = ExplorerUtils.parseJsonSafe(to)?.jsonObject ?: return changes

        // 추가/수정된 키
        toJson.keys.forEach { key ->
            val toValue = toJson[key]?.toString()
            val fromValue = fromJson[key]?.toString()

            when {
                fromValue == null -> changes.add(
                    DiffChange(path = key, type = DIFF_ADDED, oldValue = null, newValue = toValue)
                )
                fromValue != toValue -> changes.add(
                    DiffChange(path = key, type = DIFF_MODIFIED, oldValue = fromValue, newValue = toValue)
                )
            }
        }

        // 삭제된 키
        fromJson.keys.forEach { key ->
            if (key !in toJson.keys) {
                changes.add(
                    DiffChange(
                        path = key,
                        type = DIFF_REMOVED,
                        oldValue = fromJson[key]?.toString(),
                        newValue = null
                    )
                )
            }
        }

        return changes
    }

    companion object {
        private const val DIFF_ADDED = "added"
        private const val DIFF_REMOVED = "removed"
        private const val DIFF_MODIFIED = "modified"
    }
}
