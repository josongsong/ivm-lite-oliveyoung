package com.oliveyoung.ivmlite.sdk.model

data class DeploySpec(
    val compileMode: CompileMode = CompileMode.Sync,
    val shipSpec: ShipSpec? = null,
    val cutoverMode: CutoverMode = CutoverMode.Ready
)
