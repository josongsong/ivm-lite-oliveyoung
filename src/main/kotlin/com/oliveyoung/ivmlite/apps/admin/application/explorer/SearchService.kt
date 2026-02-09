package com.oliveyoung.ivmlite.apps.admin.application.explorer

import com.oliveyoung.ivmlite.apps.admin.config.AdminConstants
import com.oliveyoung.ivmlite.apps.admin.ports.ExplorerRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.TenantId

/**
 * 검색 서비스
 *
 * P0: SRP 준수 - 검색/자동완성 기능만 담당
 */
class SearchService(
    private val explorerRepo: ExplorerRepositoryPort
) {

    /**
     * 통합 검색 (엔티티 키 검색)
     */
    suspend fun search(
        tenantId: String,
        query: String,
        limit: Int = AdminConstants.DEFAULT_PAGE_SIZE
    ): Result<SearchResult> {
        if (query.isBlank()) {
            return Result.Ok(SearchResult(query = query, items = emptyList(), count = 0))
        }

        val safeLimit = limit.coerceIn(1, AdminConstants.MAX_PAGE_SIZE)
        val tenant = TenantId(tenantId)

        return explorerRepo.search(tenant, query, safeLimit)
            .fold(
                { error -> Result.Err(error) },
                { results ->
                    val items = mutableListOf<SearchItem>()

                    // RawData 결과
                    results.rawData.forEach { item ->
                        items.add(SearchItem(entityKey = item.entityKey, tenantId = tenantId, type = TYPE_RAWDATA))
                    }

                    // Slice 결과
                    results.slices.forEach { item ->
                        items.add(SearchItem(entityKey = item.sourceKey, tenantId = tenantId, type = TYPE_SLICE))
                    }

                    // View 결과
                    results.views.forEach { item ->
                        items.add(SearchItem(entityKey = item.viewId, tenantId = tenantId, type = TYPE_VIEW))
                    }

                    Result.Ok(
                        SearchResult(
                            query = query,
                            items = items.distinctBy { it.entityKey }.take(safeLimit),
                            count = items.size
                        )
                    )
                }
            )
    }

    /**
     * 자동완성 (prefix 검색)
     */
    suspend fun autocomplete(
        tenantId: String,
        prefix: String,
        limit: Int = AdminConstants.DEFAULT_PAGE_SIZE
    ): Result<List<String>> {
        if (prefix.isBlank()) {
            return Result.Ok(emptyList())
        }

        val safeLimit = limit.coerceIn(1, AdminConstants.MAX_PAGE_SIZE)
        val tenant = TenantId(tenantId)

        return explorerRepo.autocomplete(tenant, prefix, safeLimit)
            .fold(
                { error -> Result.Err(error) },
                { items ->
                    Result.Ok(items.map { it.value }.distinct().take(safeLimit))
                }
            )
    }

    companion object {
        private const val TYPE_RAWDATA = "rawdata"
        private const val TYPE_SLICE = "slice"
        private const val TYPE_VIEW = "view"
    }
}
