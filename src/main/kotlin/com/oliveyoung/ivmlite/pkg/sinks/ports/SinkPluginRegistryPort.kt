package com.oliveyoung.ivmlite.pkg.sinks.ports

import com.oliveyoung.ivmlite.sinks.contract.SinkPlugin

/**
 * SinkPlugin Registry Port - target type별 SinkPlugin 조회
 *
 * DynamoDB Streams Lambda에서 직접 SinkPlugin을 호출할 때 사용.
 * SQS 중간 단계 없이 Lambda → SinkPlugin 직접 실행.
 *
 * 구현체:
 * - SinkPluginRegistryAdapter: 프로덕션 (환경변수 기반 동적 로드)
 * - InMemorySinkPluginRegistry: 테스트용
 */
interface SinkPluginRegistryPort {

    /**
     * target 식별자로 SinkPlugin 조회
     *
     * @param target Sink 타겟 식별자 (예: "opensearch", "s3", "personalize")
     * @return SinkPlugin or null (미등록 target)
     */
    fun resolve(target: String): SinkPlugin?

    /**
     * 등록된 모든 plugin ID 목록
     */
    fun registeredTargets(): Set<String>
}
