package com.oliveyoung.ivmlite.sdk.model

/**
 * Deploy 상태 머신 상태
 * RFC-IMPL-011 Wave 1-B
 */
enum class DeployState {
    /** 대기열에 추가됨 */
    QUEUED,

    /** 컴파일 실행 중 */
    RUNNING,

    /** 컴파일 완료, Ship 대기 */
    READY,

    /** Ship 진행 중 */
    SINKING,

    /** 완료 */
    DONE,

    /** 실패 */
    FAILED
}
