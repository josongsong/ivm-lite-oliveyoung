package com.oliveyoung.ivmlite.sdk.model

/**
 * Deploy 상태 전이 이벤트
 * RFC-IMPL-011 Wave 1-B
 */
sealed interface DeployEvent {
    /** 컴파일 시작 */
    data class StartRunning(val workerId: String) : DeployEvent

    /** 컴파일 완료 */
    data object CompileComplete : DeployEvent

    /** Ship 시작 */
    data object StartSinking : DeployEvent

    /** 완료 */
    data object Complete : DeployEvent

    /** 실패 */
    data class Failed(val error: String) : DeployEvent
}
