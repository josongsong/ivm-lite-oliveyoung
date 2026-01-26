package com.oliveyoung.ivmlite.sdk.execution

import com.oliveyoung.ivmlite.sdk.model.Either
import com.oliveyoung.ivmlite.sdk.model.left
import com.oliveyoung.ivmlite.sdk.model.right
import com.oliveyoung.ivmlite.sdk.model.DeployEvent
import com.oliveyoung.ivmlite.sdk.model.DeployState
import com.oliveyoung.ivmlite.sdk.model.StateError

/**
 * Deploy 상태 전이 로직
 * RFC-IMPL-011 Wave 2-G
 */
object DeployStateMachine {
    /**
     * 현재 상태와 이벤트를 받아 다음 상태로 전이
     * @param current 현재 상태
     * @param event 전이 이벤트
     * @return 성공 시 다음 상태, 실패 시 StateError
     */
    fun transition(current: DeployState, event: DeployEvent): Either<StateError, DeployState> =
        when (current) {
            DeployState.QUEUED -> when (event) {
                is DeployEvent.StartRunning -> DeployState.RUNNING.right()
                is DeployEvent.Failed -> DeployState.FAILED.right()
                else -> StateError.InvalidTransition(current, event).left()
            }
            DeployState.RUNNING -> when (event) {
                is DeployEvent.CompileComplete -> DeployState.READY.right()
                is DeployEvent.Failed -> DeployState.FAILED.right()
                else -> StateError.InvalidTransition(current, event).left()
            }
            DeployState.READY -> when (event) {
                is DeployEvent.StartSinking -> DeployState.SINKING.right()
                is DeployEvent.Failed -> DeployState.FAILED.right()
                else -> StateError.InvalidTransition(current, event).left()
            }
            DeployState.SINKING -> when (event) {
                is DeployEvent.Complete -> DeployState.DONE.right()
                is DeployEvent.Failed -> DeployState.FAILED.right()
                else -> StateError.InvalidTransition(current, event).left()
            }
            DeployState.DONE, DeployState.FAILED ->
                StateError.InvalidTransition(current, event).left()
        }
}
