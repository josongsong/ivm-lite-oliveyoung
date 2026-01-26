package com.oliveyoung.ivmlite.shared.ports

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * RFC-IMPL-010: HealthCheckable 인터페이스
 *
 * 동적 Readiness 체크를 위한 어댑터 공통 인터페이스.
 * K8s readiness probe에서 사용: 장애 시 트래픽 차단 (fail-closed).
 *
 * 구현 요구사항:
 * - healthName: 고유 식별자 (빈 문자열 금지 권장)
 * - healthCheck(): 빠른 응답 (타임아웃 내 완료)
 */
interface HealthCheckable {
    /**
     * 어댑터 식별자 (checks 맵의 key로 사용)
     * - 고유해야 함 (중복 시 마지막 값만 유지)
     * - 빈 문자열 금지 권장
     */
    val healthName: String

    /**
     * 헬스 체크 수행
     * @return true if healthy, false otherwise
     * @throws Exception 예외 발생 시 unhealthy로 처리 (CancellationException 제외)
     */
    suspend fun healthCheck(): Boolean
}

/**
 * 타임아웃 적용된 헬스 체크 유틸
 *
 * - CancellationException은 재전파 (structured concurrency 준수)
 * - 기타 예외 → false
 * - 타임아웃 → false
 */
suspend fun HealthCheckable.healthCheckWithTimeout(
    timeout: Duration = 5.seconds,
): Boolean {
    return try {
        withTimeoutOrNull(timeout) { healthCheck() } ?: false
    } catch (e: CancellationException) {
        throw e // structured concurrency 준수
    } catch (_: Exception) {
        false
    }
}
