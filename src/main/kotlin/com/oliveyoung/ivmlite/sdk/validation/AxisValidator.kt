package com.oliveyoung.ivmlite.sdk.validation

import com.oliveyoung.ivmlite.sdk.model.DeploySpec

/**
 * AxisValidator - Deploy 축 조합 검증 (RFC-IMPL-013 업데이트)
 *
 * RFC-IMPL-013 이후:
 * - ship.sync 제거됨 → compile.async + ship.sync 검증 불필요
 * - 모든 ship은 outbox를 통해 비동기 처리
 */
object AxisValidator {
    fun validate(spec: DeploySpec): List<String> {
        val errors = mutableListOf<String>()

        // RFC-IMPL-013: ship.sync 제거됨
        // 모든 ship은 자동으로 outbox를 통해 처리되므로 축 검증 불필요

        // 향후 추가 검증 로직이 필요하면 여기에 추가

        return errors
    }
}
