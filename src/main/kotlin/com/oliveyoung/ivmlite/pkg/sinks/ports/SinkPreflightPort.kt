package com.oliveyoung.ivmlite.pkg.sinks.ports

import com.oliveyoung.ivmlite.shared.domain.types.Result

/**
 * Sink Preflight Port - Ingest 시점 Sink 설정 검증 (DX)
 *
 * SinkRule에서 resolve된 sinkTargets가 실제로 플러그인에 등록되어 있는지 검증.
 * **Workflow 실행 전**에 호출되어, 미등록 시 RawData/Slice 저장 없이 즉시 실패.
 *
 * 효과:
 * - Lambda 재시도/디버깅 비용 절감
 * - DynamoDB 부분 저장 방지 (Preflight → Workflow 순서 보장)
 * - 환경변수 힌트로 설정 오류 즉시 수정 가능
 *
 * @see SinkPreflightPluginRegistryAdapter
 * @see NoOpSinkPreflight (테스트용)
 */
interface SinkPreflightPort {

    /**
     * sinkTargets가 모두 resolve 가능한지 검증
     *
     * @param targets Sink 플러그인 ID 목록 (SinkTargetType.toPluginId())
     * @return Ok(Unit) 모두 등록됨, Err(ConfigError) 미등록 target 있음
     */
    suspend fun validate(targets: List<String>): Result<Unit>
}
