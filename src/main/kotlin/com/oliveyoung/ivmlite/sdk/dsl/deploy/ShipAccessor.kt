package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.dsl.sink.SinkBuilder
import com.oliveyoung.ivmlite.sdk.model.ShipMode
import com.oliveyoung.ivmlite.sdk.model.ShipSpec

/**
 * ShipAccessor - Ship 설정 (RFC-IMPL-013: 모든 ship은 outbox를 통해 처리)
 *
 * 모든 ship은 자동으로 outbox를 통해 비동기 처리됩니다.
 * sync 모드는 제거되었습니다.
 *
 * 사용 예시:
 * ```kotlin
 * ivm.product(product).deploy {
 *     compile.sync()
 *     ship.to { opensearch() }  // 권장
 *     // 또는
 *     ship.async { opensearch() }  // 기존 API (동일 동작)
 * }
 * ```
 */
@IvmDslMarker
class ShipAccessor internal constructor(
    private val onSet: (ShipSpec) -> Unit
) {
    /**
     * Ship 대상 설정 (권장)
     * 모든 ship은 outbox를 통해 비동기 처리됩니다.
     */
    fun to(block: SinkBuilder.() -> Unit) {
        val sinks = SinkBuilder().apply(block).build()
        @Suppress("DEPRECATION")
        onSet(ShipSpec(ShipMode.Async, sinks))
    }

    /**
     * 기존 API 호환 (async는 이제 기본 동작)
     * ship.to { } 사용을 권장합니다.
     */
    fun async(block: SinkBuilder.() -> Unit) {
        to(block)
    }

    // sync() 메서드 제거됨 - 모든 ship은 outbox를 통해 처리
}
