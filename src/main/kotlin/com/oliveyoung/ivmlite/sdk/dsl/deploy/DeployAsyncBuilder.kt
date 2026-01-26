package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.model.CompileMode
import com.oliveyoung.ivmlite.sdk.model.CutoverMode
import com.oliveyoung.ivmlite.sdk.model.DeploySpec
import com.oliveyoung.ivmlite.sdk.model.ShipSpec

/**
 * compile.async 전용 Accessor (DeployAsyncBuilder용)
 */
@IvmDslMarker
class CompileAsyncOnlyAccessor internal constructor() {
    fun async() {} // 이미 async 모드, no-op
}

/**
 * compile.async 전용 Builder - ship.sync를 타입 레벨에서 차단
 */
@IvmDslMarker
class DeployAsyncBuilder internal constructor() {
    private var shipSpec: ShipSpec? = null
    private var cutoverMode: CutoverMode = CutoverMode.Ready

    val compile = CompileAsyncOnlyAccessor()
    val ship = ShipAsyncOnlyAccessor { shipSpec = it }
    val cutover = CutoverAccessor { cutoverMode = it }

    internal fun build(): DeploySpec {
        return DeploySpec(CompileMode.Async, shipSpec, cutoverMode)
    }
}
