package com.oliveyoung.ivmlite.sdk.model

sealed interface CompileMode {
    object Sync : CompileMode
    object Async : CompileMode
    data class SyncWithTargets(val targets: List<TargetRef>) : CompileMode
}
