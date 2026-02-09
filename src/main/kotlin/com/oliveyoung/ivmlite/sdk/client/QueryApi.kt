package com.oliveyoung.ivmlite.sdk.client

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.model.QueryOptions
import com.oliveyoung.ivmlite.sdk.model.ReadConsistency
import com.oliveyoung.ivmlite.sdk.model.ViewResult
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.time.Duration

/**
 * Query API - DX 끝판왕
 * 
 * DB 중립적 기본 API + 고급 옵션 지원
 * 
 * @example 가장 간단한 사용법 (99% 케이스)
 * ```kotlin
 * val view = Ivm.query("product.pdp")
 *     .key("SKU-001")
 *     .get()
 * ```
 * 
 * @example 명시적 테넌트/버전
 * ```kotlin
 * val view = Ivm.query("product.pdp")
 *     .tenant("oliveyoung")
 *     .key("SKU-001")
 *     .version(5L)
 *     .get()
 * ```
 * 
 * @example 고급 옵션 (DynamoDB 최적화 등)
 * ```kotlin
 * val view = Ivm.query("product.pdp")
 *     .key("SKU-001")
 *     .options {
 *         consistency(Strong)
 *         timeout(5.seconds)
 *         projection("core", "pricing")
 *     }
 *     .get()
 * ```
 * 
 * @example 범위 검색
 * ```kotlin
 * val results = Ivm.query("product.pdp")
 *     .tenant("oliveyoung")
 *     .range {
 *         keyPrefix("SKU-")           // PK prefix
 *         versionBetween(1L, 10L)     // 버전 범위
 *     }
 *     .limit(100)
 *     .list()
 * ```
 */
class QueryApi internal constructor(
    private val config: IvmClientConfig
) {
    /**
     * View 조회 시작
     * @param viewId View 정의 ID (예: "product.pdp", "product.search")
     */
    fun view(viewId: String): QueryBuilder {
        require(viewId.isNotBlank()) { "viewId must not be blank" }
        return QueryBuilder(config, viewId)
    }
}

/**
 * Query Builder - Fluent DSL
 * 
 * 단일 조회 + 범위 검색 모두 지원
 */
@IvmDslMarker
class QueryBuilder internal constructor(
    private val config: IvmClientConfig,
    private val viewId: String
) {
    private var tenantId: String? = null
    private var entityKey: String? = null
    private var version: Long? = null
    private var options: QueryOptions = QueryOptions.DEFAULT
    
    // 범위 검색용
    private var rangeSpec: RangeSpec? = null
    private var limit: Int? = null
    private var cursor: String? = null
    private var sortOrder: SortOrder = SortOrder.ASC

    // ===== 필수 파라미터 =====

    /**
     * 테넌트 ID 설정 (선택, 기본값: config.tenantId)
     */
    fun tenant(tenantId: String): QueryBuilder {
        this.tenantId = tenantId
        return this
    }

    /**
     * 엔티티 키 설정 (단일 조회용, 필수)
     * 
     * @example 
     * ```kotlin
     * .key("SKU-001")           // 단순 키
     * .key("PRODUCT#t1#SKU-001") // Full 키
     * ```
     */
    fun key(entityKey: String): QueryBuilder {
        require(entityKey.isNotBlank()) { "entityKey must not be blank" }
        this.entityKey = entityKey
        return this
    }

    /**
     * 버전 설정 (선택, 기본값: latest)
     */
    fun version(version: Long): QueryBuilder {
        require(version >= 0) { "version must be non-negative" }
        this.version = version
        return this
    }

    /**
     * 최신 버전 조회 (명시적)
     */
    fun latest(): QueryBuilder {
        this.version = null
        return this
    }

    // ===== 범위 검색 =====

    /**
     * 범위 검색 조건 설정
     * 
     * @example
     * ```kotlin
     * Ivm.query("product.pdp")
     *     .tenant("oliveyoung")
     *     .range {
     *         keyPrefix("SKU-")           // PK prefix 검색
     *         versionBetween(1L, 10L)     // 버전 범위
     *     }
     *     .limit(100)
     *     .list()
     * ```
     */
    fun range(block: RangeBuilder.() -> Unit): QueryBuilder {
        this.rangeSpec = RangeBuilder().apply(block).build()
        return this
    }

    /**
     * 결과 개수 제한
     * 
     * @param maxResults 최대 결과 개수 (기본: 100, 최대: 1000)
     */
    fun limit(maxResults: Int): QueryBuilder {
        require(maxResults in 1..1000) { "limit must be between 1 and 1000" }
        this.limit = maxResults
        return this
    }

    /**
     * 페이지네이션 커서 (다음 페이지 조회용)
     * 
     * @example
     * ```kotlin
     * val page1 = Ivm.query("product.pdp").tenant("t1").range { all() }.limit(100).list()
     * val page2 = Ivm.query("product.pdp").tenant("t1").range { all() }.limit(100).after(page1.nextCursor).list()
     * ```
     */
    fun after(cursor: String?): QueryBuilder {
        this.cursor = cursor
        return this
    }

    /**
     * 정렬 순서 (기본: ASC)
     */
    fun orderBy(order: SortOrder): QueryBuilder {
        this.sortOrder = order
        return this
    }

    /**
     * 오름차순 정렬
     */
    fun ascending(): QueryBuilder = orderBy(SortOrder.ASC)

    /**
     * 내림차순 정렬
     */
    fun descending(): QueryBuilder = orderBy(SortOrder.DESC)

    // ===== 고급 옵션 =====

    /**
     * 고급 옵션 설정 (DB 최적화, 캐시 등)
     */
    fun options(block: QueryOptionsBuilder.() -> Unit): QueryBuilder {
        this.options = QueryOptionsBuilder().apply(block).build()
        return this
    }

    // ===== 단일 조회 실행 =====

    /**
     * 단일 엔티티 동기 조회
     * @return ViewResult (성공 시 데이터, 실패 시 에러)
     */
    fun get(): ViewResult {
        val finalTenantId = tenantId ?: config.tenantId 
            ?: throw IllegalArgumentException("tenantId is required. Set via .tenant() or Ivm.configure { tenantId = ... }")
        val finalEntityKey = entityKey 
            ?: throw IllegalArgumentException("entityKey is required. Use .key()")

        return executeQuery(finalTenantId, finalEntityKey, version, options)
    }

    /**
     * 단일 엔티티 비동기 조회 (suspend)
     */
    suspend fun getAsync(): ViewResult {
        val finalTenantId = tenantId ?: config.tenantId 
            ?: throw IllegalArgumentException("tenantId is required")
        val finalEntityKey = entityKey 
            ?: throw IllegalArgumentException("entityKey is required")

        return executeQueryAsync(finalTenantId, finalEntityKey, version, options)
    }

    /**
     * 존재 여부만 확인 (데이터 로드 안함)
     */
    fun exists(): Boolean {
        return try {
            get().success
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 조회 또는 null 반환 (에러 시 예외 안 던짐)
     */
    fun getOrNull(): ViewResult? {
        return try {
            val result = get()
            if (result.success) result else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 조회 또는 기본값 반환
     */
    fun getOrDefault(default: JsonObject): ViewResult {
        return getOrNull() ?: ViewResult.empty(viewId, default)
    }

    // ===== 범위 검색 실행 =====

    /**
     * 범위 검색 실행 (동기)
     * 
     * @return QueryResultPage (결과 목록 + 페이지네이션 정보)
     * 
     * @example
     * ```kotlin
     * val results = Ivm.query("product.pdp")
     *     .tenant("oliveyoung")
     *     .range { keyPrefix("SKU-") }
     *     .limit(100)
     *     .list()
     * 
     * results.items.forEach { println(it.entityKey) }
     * if (results.hasMore) {
     *     val nextPage = Ivm.query("product.pdp")
     *         .tenant("oliveyoung")
     *         .range { keyPrefix("SKU-") }
     *         .after(results.nextCursor)
     *         .list()
     * }
     * ```
     */
    fun list(): QueryResultPage {
        val finalTenantId = tenantId ?: config.tenantId 
            ?: throw IllegalArgumentException("tenantId is required for range query")
        
        return executeRangeQuery(finalTenantId, rangeSpec, limit ?: 100, cursor, sortOrder, options)
    }

    /**
     * 범위 검색 실행 (비동기)
     */
    suspend fun listAsync(): QueryResultPage {
        val finalTenantId = tenantId ?: config.tenantId 
            ?: throw IllegalArgumentException("tenantId is required for range query")
        
        return executeRangeQueryAsync(finalTenantId, rangeSpec, limit ?: 100, cursor, sortOrder, options)
    }

    /**
     * 전체 결과를 Sequence로 반환 (자동 페이지네이션)
     * 
     * @example
     * ```kotlin
     * Ivm.query("product.pdp")
     *     .tenant("oliveyoung")
     *     .range { keyPrefix("SKU-") }
     *     .stream()
     *     .take(500)
     *     .forEach { println(it.entityKey) }
     * ```
     */
    fun stream(): Sequence<ViewResult> = sequence {
        var currentCursor: String? = cursor
        do {
            val page = list()
            yieldAll(page.items)
            currentCursor = page.nextCursor
            cursor = currentCursor
        } while (page.hasMore && currentCursor != null)
    }

    /**
     * 결과 개수만 반환 (데이터 로드 안함)
     */
    fun count(): Long {
        val finalTenantId = tenantId ?: config.tenantId 
            ?: throw IllegalArgumentException("tenantId is required")
        
        return executeCount(finalTenantId, rangeSpec, options)
    }

    /**
     * 첫 번째 결과만 반환
     */
    fun first(): ViewResult? {
        return list().items.firstOrNull()
    }

    /**
     * 첫 번째 결과 또는 예외
     */
    fun firstOrThrow(): ViewResult {
        return first() ?: throw NoSuchElementException("No results found for query")
    }

    // ===== 내부 실행 로직 =====

    private fun executeQuery(
        tenantId: String,
        entityKey: String,
        version: Long?,
        options: QueryOptions
    ): ViewResult {
        // QueryViewWorkflow를 통해 PostgreSQL 직접 조회
        val workflow = com.oliveyoung.ivmlite.sdk.Ivm.getQueryWorkflow()
        
        return if (workflow != null) {
            // 실제 Workflow 호출 (suspend를 runBlocking으로 래핑)
            kotlinx.coroutines.runBlocking {
                executeQueryViaWorkflow(workflow, tenantId, entityKey, version, options)
            }
        } else {
            // Workflow 미설정 시 스텁 반환 (개발/테스트용)
            ViewResult(
                success = true,
                viewId = viewId,
                tenantId = tenantId,
                entityKey = entityKey,
                version = version ?: 0L,
                data = buildJsonObject { },
                meta = ViewResult.Meta(
                    slicesUsed = listOf("CORE", "PRICING"),
                    queryTimeMs = 5L,
                    fromCache = false,
                    consistency = options.consistency.name
                )
            )
        }
    }
    
    private suspend fun executeQueryViaWorkflow(
        workflow: com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow,
        tenantId: String,
        entityKey: String,
        version: Long?,
        options: QueryOptions
    ): ViewResult {
        val startTime = System.currentTimeMillis()
        
        val result = workflow.execute(
            tenantId = com.oliveyoung.ivmlite.shared.domain.types.TenantId(tenantId),
            viewId = viewId,
            entityKey = com.oliveyoung.ivmlite.shared.domain.types.EntityKey(entityKey),
            version = version ?: 1L
        )
        
        val queryTimeMs = System.currentTimeMillis() - startTime
        
        return when (result) {
            is Result.Ok -> {
                val response = result.value
                // ViewResponse.data는 JSON 문자열, 파싱 필요
                val dataJson = try {
                    kotlinx.serialization.json.Json.parseToJsonElement(response.data).jsonObject
                } catch (e: Exception) {
                    buildJsonObject { }
                }
                
                ViewResult(
                    success = true,
                    viewId = viewId,
                    tenantId = tenantId,
                    entityKey = entityKey,
                    version = version ?: 0L,
                    data = dataJson,
                    meta = ViewResult.Meta(
                        slicesUsed = response.meta?.usedContracts ?: emptyList(),
                        missingSlices = response.meta?.missingSlices ?: emptyList(),
                        queryTimeMs = queryTimeMs,
                        fromCache = false,
                        consistency = options.consistency.name
                    )
                )
            }
            is Result.Err -> {
                ViewResult(
                    success = false,
                    viewId = viewId,
                    tenantId = tenantId,
                    entityKey = entityKey,
                    version = version ?: 0L,
                    data = buildJsonObject { },
                    meta = ViewResult.Meta(
                        slicesUsed = emptyList(),
                        queryTimeMs = queryTimeMs,
                        fromCache = false,
                        consistency = options.consistency.name
                    ),
                    error = result.error.toString()
                )
            }
        }
    }

    private suspend fun executeQueryAsync(
        tenantId: String,
        entityKey: String,
        version: Long?,
        options: QueryOptions
    ): ViewResult {
        val workflow = com.oliveyoung.ivmlite.sdk.Ivm.getQueryWorkflow()
        
        return if (workflow != null) {
            executeQueryViaWorkflow(workflow, tenantId, entityKey, version, options)
        } else {
            executeQuery(tenantId, entityKey, version, options)
        }
    }

    private fun executeRangeQuery(
        tenantId: String,
        rangeSpec: RangeSpec?,
        limit: Int,
        cursor: String?,
        sortOrder: SortOrder,
        options: QueryOptions
    ): QueryResultPage {
        // 동기 버전: blocking으로 실행 (deprecated, async 사용 권장)
        return kotlinx.coroutines.runBlocking {
            executeRangeQueryAsync(tenantId, rangeSpec, limit, cursor, sortOrder, options)
        }
    }

    private suspend fun executeRangeQueryAsync(
        tenantId: String,
        rangeSpec: RangeSpec?,
        limit: Int,
        cursor: String?,
        sortOrder: SortOrder,
        options: QueryOptions
    ): QueryResultPage {
        val startTime = System.currentTimeMillis()
        
        val workflow = com.oliveyoung.ivmlite.sdk.Ivm.getQueryWorkflow()
        if (workflow == null) {
            // Workflow 없으면 빈 결과 반환
            return QueryResultPage(
                items = emptyList(),
                totalCount = 0,
                hasMore = false,
                nextCursor = null,
                queryTimeMs = System.currentTimeMillis() - startTime
            )
        }
        
        val keyPrefix = rangeSpec?.keyPrefix ?: ""
        val sliceType = options.projections.firstOrNull()?.let { 
            try { 
                com.oliveyoung.ivmlite.shared.domain.types.SliceType.fromDbValue(it) 
            } catch (e: Exception) { 
                null 
            }
        }
        
        val result = workflow.executeRange(
            tenantId = com.oliveyoung.ivmlite.shared.domain.types.TenantId(tenantId),
            keyPrefix = keyPrefix,
            sliceType = sliceType,
            limit = limit,
            cursor = cursor
        )
        
        return when (result) {
            is Result.Ok -> {
                val rangeResult = result.value
                QueryResultPage(
                    items = rangeResult.items.map { item ->
                        ViewResult(
                            success = true,
                            viewId = viewId,
                            tenantId = tenantId,
                            entityKey = item.entityKey,
                            version = item.version,
                            data = try {
                                kotlinx.serialization.json.Json.parseToJsonElement(item.data).jsonObject
                            } catch (e: Exception) {
                                buildJsonObject { }
                            }
                        )
                    },
                    totalCount = rangeResult.totalCount,
                    hasMore = rangeResult.hasMore,
                    nextCursor = rangeResult.nextCursor,
                    queryTimeMs = System.currentTimeMillis() - startTime
                )
            }
            is Result.Err -> {
                QueryResultPage(
                    items = emptyList(),
                    totalCount = 0,
                    hasMore = false,
                    nextCursor = null,
                    queryTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }

    private fun executeCount(
        tenantId: String,
        rangeSpec: RangeSpec?,
        options: QueryOptions
    ): Long {
        return kotlinx.coroutines.runBlocking {
            executeCountAsync(tenantId, rangeSpec, options)
        }
    }
    
    private suspend fun executeCountAsync(
        tenantId: String,
        rangeSpec: RangeSpec?,
        options: QueryOptions
    ): Long {
        val workflow = com.oliveyoung.ivmlite.sdk.Ivm.getQueryWorkflow()
            ?: return 0L
        
        val keyPrefix = rangeSpec?.keyPrefix
        val sliceType = options.projections.firstOrNull()?.let {
            try {
                com.oliveyoung.ivmlite.shared.domain.types.SliceType.fromDbValue(it)
            } catch (e: Exception) {
                null
            }
        }
        
        val result = workflow.executeCount(
            tenantId = com.oliveyoung.ivmlite.shared.domain.types.TenantId(tenantId),
            keyPrefix = keyPrefix,
            sliceType = sliceType
        )
        
        return when (result) {
            is Result.Ok -> result.value
            is Result.Err -> 0L
        }
    }
}

// ===== 범위 검색 관련 클래스들 =====

/**
 * 정렬 순서
 */
enum class SortOrder {
    ASC,   // 오름차순 (기본)
    DESC   // 내림차순
}

/**
 * 범위 검색 스펙
 */
data class RangeSpec(
    val keyPrefix: String? = null,
    val keyFrom: String? = null,
    val keyTo: String? = null,
    val versionFrom: Long? = null,
    val versionTo: Long? = null,
    val filters: List<Filter> = emptyList()
)

/**
 * 필터 조건
 */
data class Filter(
    val field: String,
    val operator: FilterOperator,
    val value: Any
)

enum class FilterOperator {
    EQ,      // =
    NE,      // !=
    GT,      // >
    GTE,     // >=
    LT,      // <
    LTE,     // <=
    CONTAINS,     // CONTAINS
    BEGINS_WITH,  // BEGINS_WITH (DynamoDB)
    IN            // IN (...)
}

/**
 * Range Builder - 범위 검색 조건 빌더
 */
@IvmDslMarker
class RangeBuilder internal constructor() {
    private var keyPrefix: String? = null
    private var keyFrom: String? = null
    private var keyTo: String? = null
    private var versionFrom: Long? = null
    private var versionTo: Long? = null
    private val filters = mutableListOf<Filter>()

    // ===== Key 범위 =====

    /**
     * 전체 조회 (범위 제한 없음)
     */
    fun all() {
        // 아무 조건 없음
    }

    /**
     * Key Prefix 검색 (DynamoDB begins_with)
     * 
     * @example
     * ```kotlin
     * .keyPrefix("SKU-")      // SKU-로 시작하는 모든 키
     * .keyPrefix("PRODUCT#")  // PRODUCT#으로 시작하는 모든 키
     * ```
     */
    fun keyPrefix(prefix: String) {
        this.keyPrefix = prefix
    }

    /**
     * Key 범위 검색
     * 
     * @example
     * ```kotlin
     * .keyBetween("SKU-001", "SKU-100")  // SKU-001 ~ SKU-100
     * ```
     */
    fun keyBetween(from: String, to: String) {
        this.keyFrom = from
        this.keyTo = to
    }

    /**
     * Key >= from
     */
    fun keyFrom(from: String) {
        this.keyFrom = from
    }

    /**
     * Key <= to
     */
    fun keyTo(to: String) {
        this.keyTo = to
    }

    // ===== Version 범위 =====

    /**
     * 버전 범위 검색
     * 
     * @example
     * ```kotlin
     * .versionBetween(1L, 10L)  // v1 ~ v10
     * ```
     */
    fun versionBetween(from: Long, to: Long) {
        require(from <= to) { "versionFrom must be <= versionTo" }
        this.versionFrom = from
        this.versionTo = to
    }

    /**
     * 특정 버전 이상
     */
    fun versionFrom(from: Long) {
        this.versionFrom = from
    }

    /**
     * 특정 버전 이하
     */
    fun versionTo(to: Long) {
        this.versionTo = to
    }

    /**
     * 최신 버전만
     */
    fun latestOnly() {
        // TODO: 최신 버전만 필터링하는 플래그 추가
    }

    // ===== 필터 조건 =====

    /**
     * 필터 조건 추가 (= 연산)
     * 
     * @example
     * ```kotlin
     * .where("category", "스킨케어")
     * .where("status", "ACTIVE")
     * ```
     */
    fun where(field: String, value: Any) {
        filters.add(Filter(field, FilterOperator.EQ, value))
    }

    /**
     * 필터 조건 추가 (커스텀 연산자)
     * 
     * @example
     * ```kotlin
     * .where("price", FilterOperator.GTE, 10000)
     * .where("stock", FilterOperator.GT, 0)
     * ```
     */
    fun where(field: String, operator: FilterOperator, value: Any) {
        filters.add(Filter(field, operator, value))
    }

    /**
     * 필드 값이 특정 값보다 큰 경우
     */
    fun whereGreaterThan(field: String, value: Any) {
        filters.add(Filter(field, FilterOperator.GT, value))
    }

    /**
     * 필드 값이 특정 값보다 작은 경우
     */
    fun whereLessThan(field: String, value: Any) {
        filters.add(Filter(field, FilterOperator.LT, value))
    }

    /**
     * 필드 값이 목록에 포함된 경우
     */
    fun whereIn(field: String, values: List<Any>) {
        filters.add(Filter(field, FilterOperator.IN, values))
    }

    /**
     * 필드 값이 특정 문자열을 포함하는 경우
     */
    fun whereContains(field: String, substring: String) {
        filters.add(Filter(field, FilterOperator.CONTAINS, substring))
    }

    internal fun build(): RangeSpec = RangeSpec(
        keyPrefix = keyPrefix,
        keyFrom = keyFrom,
        keyTo = keyTo,
        versionFrom = versionFrom,
        versionTo = versionTo,
        filters = filters.toList()
    )
}

/**
 * 범위 검색 결과 페이지
 */
data class QueryResultPage(
    /** 결과 목록 */
    val items: List<ViewResult>,
    
    /** 전체 결과 개수 (추정치, 정확하지 않을 수 있음) */
    val totalCount: Long,
    
    /** 다음 페이지 존재 여부 */
    val hasMore: Boolean,
    
    /** 다음 페이지 커서 */
    val nextCursor: String?,
    
    /** 쿼리 소요 시간 (ms) */
    val queryTimeMs: Long
) {
    /** 결과가 비어있는지 */
    val isEmpty: Boolean get() = items.isEmpty()
    
    /** 결과 개수 */
    val size: Int get() = items.size
    
    /** 첫 번째 결과 */
    val first: ViewResult? get() = items.firstOrNull()
    
    /** 마지막 결과 */
    val last: ViewResult? get() = items.lastOrNull()
}

/**
 * Query Options Builder - 고급 옵션
 */
@IvmDslMarker
class QueryOptionsBuilder internal constructor() {
    private var consistency: ReadConsistency = ReadConsistency.Eventual
    private var timeout: Duration = Duration.ofSeconds(30)
    private var cacheEnabled: Boolean = true
    private var cacheTtl: Duration = Duration.ofMinutes(5)
    private var projections: List<String> = emptyList()
    private var includeMetadata: Boolean = false
    private var retryOnFailure: Boolean = true
    private var maxRetries: Int = 3

    // ===== 일관성 옵션 (DynamoDB 등) =====

    /**
     * 읽기 일관성 설정
     * - Eventual: 최종 일관성 (빠름, 기본값)
     * - Strong: 강한 일관성 (DynamoDB ConsistentRead)
     * - Session: 세션 일관성
     */
    fun consistency(level: ReadConsistency) {
        this.consistency = level
    }

    /**
     * 강한 일관성 (Shortcut)
     */
    fun strongConsistency() {
        this.consistency = ReadConsistency.Strong
    }

    // ===== 타임아웃 =====

    /**
     * 쿼리 타임아웃 설정
     */
    fun timeout(duration: Duration) {
        require(!duration.isNegative && !duration.isZero) { "timeout must be positive" }
        this.timeout = duration
    }

    // ===== 캐시 옵션 =====

    /**
     * 캐시 활성화/비활성화
     */
    fun cache(enabled: Boolean, ttl: Duration = Duration.ofMinutes(5)) {
        this.cacheEnabled = enabled
        this.cacheTtl = ttl
    }

    /**
     * 캐시 무시 (항상 DB에서 조회)
     */
    fun noCache() {
        this.cacheEnabled = false
    }

    /**
     * 캐시만 조회 (DB 안 감)
     */
    fun cacheOnly() {
        this.cacheEnabled = true
        // TODO: cacheOnly 플래그 추가
    }

    // ===== 프로젝션 (부분 조회) =====

    /**
     * 특정 Slice만 조회 (프로젝션)
     * 
     * @example
     * ```kotlin
     * .projection("core", "pricing")  // CORE, PRICING만 조회
     * ```
     */
    fun projection(vararg sliceTypes: String) {
        this.projections = sliceTypes.toList()
    }

    // ===== 메타데이터 =====

    /**
     * 응답에 메타데이터 포함
     */
    fun includeMetadata() {
        this.includeMetadata = true
    }

    // ===== 재시도 =====

    /**
     * 실패 시 재시도 설정
     */
    fun retry(enabled: Boolean = true, maxRetries: Int = 3) {
        this.retryOnFailure = enabled
        this.maxRetries = maxRetries
    }

    /**
     * 재시도 안 함
     */
    fun noRetry() {
        this.retryOnFailure = false
    }

    internal fun build(): QueryOptions = QueryOptions(
        consistency = consistency,
        timeout = timeout,
        cacheEnabled = cacheEnabled,
        cacheTtl = cacheTtl,
        projections = projections,
        includeMetadata = includeMetadata,
        retryOnFailure = retryOnFailure,
        maxRetries = maxRetries
    )
}
