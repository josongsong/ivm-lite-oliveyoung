package com.oliveyoung.ivmlite.sinks.contract

/**
 * Sink 라우팅 테이블 (SSOT)
 *
 * RFC-017: Sink Plugin Architecture
 *
 * SinkTarget → QueueUrl 매핑 관리
 */
interface SinkRoutingTable {
    /**
     * Target에 해당하는 SQS Queue URL 조회
     *
     * @param target Sink 타겟 식별자 (예: "s3-sink")
     * @return Queue URL or null
     */
    fun queueUrlOf(target: String): String?

    /**
     * 모든 등록된 라우팅 조회
     */
    fun allRoutes(): Map<String, String>
}

/**
 * In-Memory 구현 (개발/테스트용)
 */
class InMemorySinkRoutingTable(
    private val routes: Map<String, String>
) : SinkRoutingTable {

    override fun queueUrlOf(target: String): String? = routes[target]

    override fun allRoutes(): Map<String, String> = routes.toMap()

    companion object {
        /**
         * 설정 맵으로부터 생성
         */
        fun fromConfig(config: Map<String, String>): InMemorySinkRoutingTable {
            return InMemorySinkRoutingTable(config)
        }
    }
}
