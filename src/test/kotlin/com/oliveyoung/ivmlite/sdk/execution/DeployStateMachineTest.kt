package com.oliveyoung.ivmlite.sdk.execution

import com.oliveyoung.ivmlite.sdk.model.Either
import com.oliveyoung.ivmlite.sdk.model.DeployEvent
import com.oliveyoung.ivmlite.sdk.model.DeployState
import com.oliveyoung.ivmlite.sdk.model.StateError
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DeployStateMachine 완결성 테스트
 * - 정상 경로 (Happy Path)
 * - 모든 상태×이벤트 조합 검증 (6 states × 5 events = 30 cases)
 * - 터미널 상태 불변성
 * - Failed 이벤트 일관성
 */
class DeployStateMachineTest {

    // ==================== Happy Path ====================

    @Test
    fun `정상 경로 - QUEUED to DONE`() {
        var state: DeployState = DeployState.QUEUED

        // QUEUED → RUNNING
        val r1 = DeployStateMachine.transition(state, DeployEvent.StartRunning("worker-1"))
        assertTrue(r1.isRight())
        state = r1.rightOrNull()!!
        assertEquals(DeployState.RUNNING, state)

        // RUNNING → READY
        val r2 = DeployStateMachine.transition(state, DeployEvent.CompileComplete)
        assertTrue(r2.isRight())
        state = r2.rightOrNull()!!
        assertEquals(DeployState.READY, state)

        // READY → SINKING
        val r3 = DeployStateMachine.transition(state, DeployEvent.StartSinking)
        assertTrue(r3.isRight())
        state = r3.rightOrNull()!!
        assertEquals(DeployState.SINKING, state)

        // SINKING → DONE
        val r4 = DeployStateMachine.transition(state, DeployEvent.Complete)
        assertTrue(r4.isRight())
        state = r4.rightOrNull()!!
        assertEquals(DeployState.DONE, state)
    }

    @Test
    fun `정상 경로 - QUEUED to FAILED`() {
        val state = DeployState.QUEUED
        val result = DeployStateMachine.transition(state, DeployEvent.Failed("error"))

        assertTrue(result.isRight())
        assertEquals(DeployState.FAILED, result.rightOrNull()!!)
    }

    // ==================== Invalid Transitions ====================

    @Test
    fun `QUEUED 상태 - CompileComplete 이벤트는 InvalidTransition`() {
        val result = DeployStateMachine.transition(DeployState.QUEUED, DeployEvent.CompileComplete)

        assertTrue(result.isLeft())
        val error = result.leftOrNull()!!
        assertTrue(error is StateError.InvalidTransition)
        assertEquals(DeployState.QUEUED, error.current)
    }

    @Test
    fun `QUEUED 상태 - StartSinking 이벤트는 InvalidTransition`() {
        val result = DeployStateMachine.transition(DeployState.QUEUED, DeployEvent.StartSinking)

        assertTrue(result.isLeft())
        val error = result.leftOrNull()!!
        assertTrue(error is StateError.InvalidTransition)
    }

    @Test
    fun `QUEUED 상태 - Complete 이벤트는 InvalidTransition`() {
        val result = DeployStateMachine.transition(DeployState.QUEUED, DeployEvent.Complete)

        assertTrue(result.isLeft())
        val error = result.leftOrNull()!!
        assertTrue(error is StateError.InvalidTransition)
    }

    @Test
    fun `RUNNING 상태 - StartRunning 이벤트는 InvalidTransition`() {
        val result = DeployStateMachine.transition(DeployState.RUNNING, DeployEvent.StartRunning("worker-1"))

        assertTrue(result.isLeft())
    }

    @Test
    fun `RUNNING 상태 - StartSinking 이벤트는 InvalidTransition`() {
        val result = DeployStateMachine.transition(DeployState.RUNNING, DeployEvent.StartSinking)

        assertTrue(result.isLeft())
    }

    @Test
    fun `RUNNING 상태 - Complete 이벤트는 InvalidTransition`() {
        val result = DeployStateMachine.transition(DeployState.RUNNING, DeployEvent.Complete)

        assertTrue(result.isLeft())
    }

    @Test
    fun `READY 상태 - StartRunning 이벤트는 InvalidTransition`() {
        val result = DeployStateMachine.transition(DeployState.READY, DeployEvent.StartRunning("worker-1"))

        assertTrue(result.isLeft())
    }

    @Test
    fun `READY 상태 - CompileComplete 이벤트는 InvalidTransition`() {
        val result = DeployStateMachine.transition(DeployState.READY, DeployEvent.CompileComplete)

        assertTrue(result.isLeft())
    }

    @Test
    fun `READY 상태 - Complete 이벤트는 InvalidTransition`() {
        val result = DeployStateMachine.transition(DeployState.READY, DeployEvent.Complete)

        assertTrue(result.isLeft())
    }

    @Test
    fun `SINKING 상태 - StartRunning 이벤트는 InvalidTransition`() {
        val result = DeployStateMachine.transition(DeployState.SINKING, DeployEvent.StartRunning("worker-1"))

        assertTrue(result.isLeft())
    }

    @Test
    fun `SINKING 상태 - CompileComplete 이벤트는 InvalidTransition`() {
        val result = DeployStateMachine.transition(DeployState.SINKING, DeployEvent.CompileComplete)

        assertTrue(result.isLeft())
    }

    @Test
    fun `SINKING 상태 - StartSinking 이벤트는 InvalidTransition`() {
        val result = DeployStateMachine.transition(DeployState.SINKING, DeployEvent.StartSinking)

        assertTrue(result.isLeft())
    }

    // ==================== Terminal State Immutability ====================

    @Test
    fun `DONE 상태는 모든 이벤트를 거부한다`() {
        val events = listOf(
            DeployEvent.StartRunning("worker-1"),
            DeployEvent.CompileComplete,
            DeployEvent.StartSinking,
            DeployEvent.Complete,
            DeployEvent.Failed("error")
        )

        assertAll(
            events.map { event ->
                {
                    val result = DeployStateMachine.transition(DeployState.DONE, event)
                    assertTrue(result.isLeft(), "DONE 상태에서 $event 이벤트는 거부되어야 함")
                    val error = result.leftOrNull()!!
                    assertTrue(error is StateError.InvalidTransition)
                    assertEquals(DeployState.DONE, error.current)
                }
            }
        )
    }

    @Test
    fun `FAILED 상태는 모든 이벤트를 거부한다`() {
        val events = listOf(
            DeployEvent.StartRunning("worker-1"),
            DeployEvent.CompileComplete,
            DeployEvent.StartSinking,
            DeployEvent.Complete,
            DeployEvent.Failed("another error")
        )

        assertAll(
            events.map { event ->
                {
                    val result = DeployStateMachine.transition(DeployState.FAILED, event)
                    assertTrue(result.isLeft(), "FAILED 상태에서 $event 이벤트는 거부되어야 함")
                    val error = result.leftOrNull()!!
                    assertTrue(error is StateError.InvalidTransition)
                    assertEquals(DeployState.FAILED, error.current)
                }
            }
        )
    }

    // ==================== Failed Event Consistency ====================

    @Test
    fun `모든 비터미널 상태에서 Failed 이벤트는 FAILED 상태로 전이`() {
        val nonTerminalStates = listOf(
            DeployState.QUEUED,
            DeployState.RUNNING,
            DeployState.READY,
            DeployState.SINKING
        )

        assertAll(
            nonTerminalStates.map { state ->
                {
                    val result = DeployStateMachine.transition(state, DeployEvent.Failed("error"))
                    assertTrue(result.isRight(), "$state 상태에서 Failed 이벤트는 성공해야 함")
                    assertEquals(DeployState.FAILED, result.rightOrNull()!!)
                }
            }
        )
    }

    // ==================== Mathematical Completeness ====================

    @Test
    fun `수학적 완결성 - 모든 상태와 이벤트 조합 검증 (30 cases)`() {
        val allStates = DeployState.values().toList()
        val allEvents = listOf(
            DeployEvent.StartRunning("worker-1"),
            DeployEvent.CompileComplete,
            DeployEvent.StartSinking,
            DeployEvent.Complete,
            DeployEvent.Failed("error")
        )

        // 6 states × 5 events = 30 combinations
        assertEquals(6, allStates.size)
        assertEquals(5, allEvents.size)

        val validTransitions = mapOf(
            DeployState.QUEUED to setOf(
                DeployEvent.StartRunning("worker-1") to DeployState.RUNNING,
                DeployEvent.Failed("error") to DeployState.FAILED
            ),
            DeployState.RUNNING to setOf(
                DeployEvent.CompileComplete to DeployState.READY,
                DeployEvent.Failed("error") to DeployState.FAILED
            ),
            DeployState.READY to setOf(
                DeployEvent.StartSinking to DeployState.SINKING,
                DeployEvent.Failed("error") to DeployState.FAILED
            ),
            DeployState.SINKING to setOf(
                DeployEvent.Complete to DeployState.DONE,
                DeployEvent.Failed("error") to DeployState.FAILED
            )
        )

        allStates.forEach { state ->
            allEvents.forEach { event ->
                val result = DeployStateMachine.transition(state, event)

                val expectedValid = validTransitions[state]?.any { (e, _) ->
                    e.javaClass == event.javaClass
                } ?: false

                if (expectedValid) {
                    assertTrue(
                        result.isRight(),
                        "전이 $state + $event 는 유효해야 함"
                    )
                } else {
                    assertTrue(
                        result.isLeft(),
                        "전이 $state + $event 는 무효해야 함"
                    )
                }
            }
        }
    }

    // ==================== Edge Cases ====================

    @Test
    fun `여러 worker로 StartRunning 이벤트 처리`() {
        val workers = listOf("worker-1", "worker-2", "worker-3")

        workers.forEach { workerId ->
            val result = DeployStateMachine.transition(
                DeployState.QUEUED,
                DeployEvent.StartRunning(workerId)
            )
            assertTrue(result.isRight())
            assertEquals(DeployState.RUNNING, result.rightOrNull()!!)
        }
    }

    @Test
    fun `여러 종류의 실패 메시지로 Failed 이벤트 처리`() {
        val errorMessages = listOf(
            "Compilation error",
            "Timeout",
            "Network failure",
            ""
        )

        errorMessages.forEach { error ->
            val result = DeployStateMachine.transition(
                DeployState.RUNNING,
                DeployEvent.Failed(error)
            )
            assertTrue(result.isRight())
            assertEquals(DeployState.FAILED, result.rightOrNull()!!)
        }
    }

    // ==================== State Transition Graph Properties ====================

    @Test
    fun `상태 전이 그래프는 비순환성을 보장한다`() {
        // DONE과 FAILED는 터미널 상태이므로 사이클이 발생할 수 없음
        // 모든 정상 경로는 QUEUED → RUNNING → READY → SINKING → DONE 순서
        // Failed는 언제든 FAILED로 전이 가능하지만 FAILED는 터미널 상태

        val terminalStates = setOf(DeployState.DONE, DeployState.FAILED)
        val allEvents = listOf(
            DeployEvent.StartRunning("w"),
            DeployEvent.CompileComplete,
            DeployEvent.StartSinking,
            DeployEvent.Complete,
            DeployEvent.Failed("e")
        )

        terminalStates.forEach { terminal ->
            allEvents.forEach { event ->
                val result = DeployStateMachine.transition(terminal, event)
                assertTrue(result.isLeft(), "터미널 상태 $terminal 에서는 순환 불가")
            }
        }
    }

    @Test
    fun `정상 경로는 단방향이다 - 역방향 전이 불가`() {
        // RUNNING → QUEUED 불가
        val r1 = DeployStateMachine.transition(DeployState.RUNNING, DeployEvent.StartRunning("w"))
        assertTrue(r1.isLeft())

        // READY → RUNNING 불가
        val r2 = DeployStateMachine.transition(DeployState.READY, DeployEvent.CompileComplete)
        assertTrue(r2.isLeft())

        // SINKING → READY 불가
        val r3 = DeployStateMachine.transition(DeployState.SINKING, DeployEvent.StartSinking)
        assertTrue(r3.isLeft())
    }
}
