package com.oliveyoung.ivmlite.apps.admin.application.explorer

import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.pkg.rawdata.ports.RawDataRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * View 탐색 서비스
 *
 * P0: SRP 준수 - View 조회/조합 기능만 담당
 */
class ViewExplorerService(
    private val rawDataRepo: RawDataRepositoryPort,
    private val queryViewWorkflow: QueryViewWorkflow?
) {
    private val logger = LoggerFactory.getLogger(ViewExplorerService::class.java)

    /**
     * View 조합 미리보기
     */
    suspend fun getView(
        tenantId: String,
        entityKey: String,
        viewDefId: String
    ): Result<ViewResult> {
        val workflow = queryViewWorkflow
            ?: return Result.Err(DomainError.ConfigError("QueryViewWorkflow not configured"))

        val tenant = TenantId(tenantId)
        val entity = EntityKey(entityKey)

        return Result.catch {
            val latestVersion = when (val result = rawDataRepo.getLatest(tenant, entity)) {
                is Result.Ok -> result.value.version
                is Result.Err -> throw result.error
            }

            when (val result = workflow.execute(tenant, viewDefId, entity, latestVersion)) {
                is Result.Ok -> ViewResult(
                    tenantId = tenantId,
                    entityKey = entityKey,
                    viewDefId = viewDefId,
                    data = ExplorerUtils.parseJsonSafe(result.value.data),
                    dataRaw = result.value.data,
                    slicesUsed = result.value.meta?.usedContracts ?: emptyList(),
                    version = latestVersion,
                    assembledAt = Instant.now().toString()
                )
                is Result.Err -> throw result.error
            }
        }
    }

}
