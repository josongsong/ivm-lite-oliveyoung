package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.dsl.markers.IvmDslMarker
import com.oliveyoung.ivmlite.sdk.model.CompileMode
import com.oliveyoung.ivmlite.sdk.model.CutoverMode
import com.oliveyoung.ivmlite.sdk.model.DeploySpec
import com.oliveyoung.ivmlite.sdk.model.ShipMode
import com.oliveyoung.ivmlite.sdk.model.ShipSpec

@IvmDslMarker
class DeployBuilder internal constructor() {
    private var compileMode: CompileMode = CompileMode.Sync
    private var shipSpec: ShipSpec? = null
    private var cutoverMode: CutoverMode = CutoverMode.Ready

    val compile = CompileAccessor { compileMode = it }
    val ship = ShipAccessor { shipSpec = it }
    val cutover = CutoverAccessor { cutoverMode = it }

    internal fun build(): DeploySpec {
        // Axis Validation: compile.async + ship.sync 차단
        if (compileMode == CompileMode.Async && shipSpec?.mode == ShipMode.Sync) {
            throw IllegalStateException(
                "Invalid axis combination: compile.async + ship.sync is not allowed. " +
                "Use ship.async instead."
            )
        }
        return DeploySpec(compileMode, shipSpec, cutoverMode)
    }
}
