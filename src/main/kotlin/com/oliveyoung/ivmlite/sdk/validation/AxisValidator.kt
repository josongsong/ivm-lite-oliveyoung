package com.oliveyoung.ivmlite.sdk.validation

import com.oliveyoung.ivmlite.sdk.model.CompileMode
import com.oliveyoung.ivmlite.sdk.model.DeploySpec
import com.oliveyoung.ivmlite.sdk.model.ShipMode

object AxisValidator {
    fun validate(spec: DeploySpec): List<String> {
        val errors = mutableListOf<String>()

        // compile.async + ship.sync 차단
        if (spec.compileMode == CompileMode.Async &&
            spec.shipSpec?.mode == ShipMode.Sync) {
            errors.add("compile.async + ship.sync is not allowed")
        }

        return errors
    }
}
