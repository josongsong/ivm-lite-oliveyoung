package com.oliveyoung.ivmlite.sdk.model

/**
 * 상태 전이 오류
 * RFC-IMPL-011 Wave 1-B
 */
sealed interface StateError {
    /** 유효하지 않은 상태 전이 */
    data class InvalidTransition(
        val current: DeployState,
        val event: DeployEvent
    ) : StateError
}
