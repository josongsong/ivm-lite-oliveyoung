package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.model.CompileMode
import com.oliveyoung.ivmlite.sdk.model.CutoverMode
import com.oliveyoung.ivmlite.sdk.model.DeploySpec
import com.oliveyoung.ivmlite.sdk.model.ShipSpec

/**
 * DeployBuilder - Full Deploy DSL (RFC-IMPL-013: 자동 Ship + Outbox 전용)
 *
 * RFC-IMPL-013 핵심 변경:
 * - Ship은 SinkRule 기반으로 자동 트리거됨
 * - ship.to { } 는 선택적 override용 (특정 sink로만 보내고 싶을 때)
 * - 모든 ship은 outbox를 통해 비동기 처리
 *
 * 사용 예시:
 * ```kotlin
 * // 기본: SinkRule 기반 자동 Ship
 * ivm.product(product).deploy()
 *
 * // 또는 블록으로
 * ivm.product(product).deploy {
 *     compile.sync()
 *     // ship은 SinkRule 기반으로 자동 트리거됨
 * }
 *
 * // 특정 sink로 override하고 싶을 때만 명시
 * ivm.product(product).deploy {
 *     compile.sync()
 *     ship.to { personalize() }  // SinkRule 대신 personalize로만
 * }
 * ```
 */
@IvmDslMarker
class DeployBuilder internal constructor() {
    private var compileMode: CompileMode = CompileMode.Sync
    private var shipSpec: ShipSpec? = null
    private var cutoverMode: CutoverMode = CutoverMode.Ready

    val compile = CompileAccessor { compileMode = it }
    val ship = ShipAccessor { shipSpec = it }
    val cutover = CutoverAccessor { cutoverMode = it }

    internal fun build(): DeploySpec {
        // RFC-IMPL-013: shipSpec이 없으면 SinkRule 기반 자동 Ship
        // shipSpec이 있으면 명시적 override
        return if (shipSpec != null) {
            DeploySpec.Full(compileMode, shipSpec!!, cutoverMode)
        } else {
            // SinkRule 기반 자동 Ship (OutboxPollingWorker에서 처리)
            DeploySpec.CompileOnly(compileMode, cutoverMode)
        }
    }
}

/**
 * CompileOnlyBuilder - Compile Only DSL (Ship 없음)
 * 
 * 명시적으로 compile만 수행하고 ship을 생략합니다.
 * 
 * 사용 사례:
 * - 테스트/디버깅
 * - Ship을 나중에 별도로 트리거
 * - 배치 처리 후 일괄 Ship
 * 
 * 사용 예시:
 * ```kotlin
 * ivm.product(product).compileOnly {
 *     compile.sync()
 * }
 * ```
 */
@IvmDslMarker
class CompileOnlyBuilder internal constructor() {
    private var compileMode: CompileMode = CompileMode.Sync
    private var cutoverMode: CutoverMode = CutoverMode.Ready

    val compile = CompileAccessor { compileMode = it }
    val cutover = CutoverAccessor { cutoverMode = it }
    
    // ship은 의도적으로 제공하지 않음

    internal fun build(): DeploySpec.CompileOnly {
        return DeploySpec.CompileOnly(compileMode, cutoverMode)
    }
}
