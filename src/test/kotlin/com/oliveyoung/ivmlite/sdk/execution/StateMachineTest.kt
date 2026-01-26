package com.oliveyoung.ivmlite.sdk.execution

import com.oliveyoung.ivmlite.sdk.model.DeployEvent
import com.oliveyoung.ivmlite.sdk.model.DeployState
import com.oliveyoung.ivmlite.sdk.model.Either
import com.oliveyoung.ivmlite.sdk.model.StateError
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * DeployStateMachine 비판적 검증 테스트
 * RFC-IMPL-011 Wave 2-G
 *
 * 검증 항목:
 * 1. 기본 정상 흐름
 * 2. 엣지 케이스 (terminal states, 직접 실패)
 * 3. 코너 케이스 (중복 이벤트, 잘못된 순서)
 * 4. 수학적 완결성 (30개 조합 전체 검증)
 */
class StateMachineTest : StringSpec({

    // ==================== 기본 정상 흐름 ====================

    "정상 흐름: QUEUED → RUNNING → READY → SINKING → DONE" {
        var state = DeployState.QUEUED

        // QUEUED → RUNNING
        DeployStateMachine.transition(state, DeployEvent.StartRunning("worker-1")).also {
            it.shouldBeInstanceOf<Either.Right<DeployState>>()
            state = it.getOrNull()!!
        }
        state shouldBe DeployState.RUNNING

        // RUNNING → READY
        DeployStateMachine.transition(state, DeployEvent.CompileComplete).also {
            it.shouldBeInstanceOf<Either.Right<DeployState>>()
            state = it.getOrNull()!!
        }
        state shouldBe DeployState.READY

        // READY → SINKING
        DeployStateMachine.transition(state, DeployEvent.StartSinking).also {
            it.shouldBeInstanceOf<Either.Right<DeployState>>()
            state = it.getOrNull()!!
        }
        state shouldBe DeployState.SINKING

        // SINKING → DONE
        DeployStateMachine.transition(state, DeployEvent.Complete).also {
            it.shouldBeInstanceOf<Either.Right<DeployState>>()
            state = it.getOrNull()!!
        }
        state shouldBe DeployState.DONE
    }

    // ==================== 엣지 케이스 ====================

    "엣지: QUEUED → Failed (직접 실패)" {
        val result = DeployStateMachine.transition(
            DeployState.QUEUED,
            DeployEvent.Failed("startup failed")
        )
        result.shouldBeInstanceOf<Either.Right<DeployState>>()
        result.getOrNull() shouldBe DeployState.FAILED
    }

    "엣지: RUNNING → Failed (컴파일 실패)" {
        val result = DeployStateMachine.transition(
            DeployState.RUNNING,
            DeployEvent.Failed("compilation error")
        )
        result.shouldBeInstanceOf<Either.Right<DeployState>>()
        result.getOrNull() shouldBe DeployState.FAILED
    }

    "엣지: READY → Failed (준비 중 실패)" {
        val result = DeployStateMachine.transition(
            DeployState.READY,
            DeployEvent.Failed("pre-ship check failed")
        )
        result.shouldBeInstanceOf<Either.Right<DeployState>>()
        result.getOrNull() shouldBe DeployState.FAILED
    }

    "엣지: SINKING → Failed (ship 실패)" {
        val result = DeployStateMachine.transition(
            DeployState.SINKING,
            DeployEvent.Failed("sink error")
        )
        result.shouldBeInstanceOf<Either.Right<DeployState>>()
        result.getOrNull() shouldBe DeployState.FAILED
    }

    "엣지: READY → Complete는 InvalidTransition (ship 스킵 불가)" {
        val result = DeployStateMachine.transition(
            DeployState.READY,
            DeployEvent.Complete
        )
        result.shouldBeInstanceOf<Either.Left<StateError>>()
        result.leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()
        (result.leftOrNull() as StateError.InvalidTransition).current shouldBe DeployState.READY
    }

    "엣지: DONE은 terminal state (모든 이벤트 거부)" {
        listOf(
            DeployEvent.StartRunning("worker-1"),
            DeployEvent.CompileComplete,
            DeployEvent.StartSinking,
            DeployEvent.Complete,
            DeployEvent.Failed("error")
        ).forEach { event ->
            val result = DeployStateMachine.transition(DeployState.DONE, event)
            result.shouldBeInstanceOf<Either.Left<StateError>>()
            result.leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()
        }
    }

    "엣지: FAILED는 terminal state (모든 이벤트 거부)" {
        listOf(
            DeployEvent.StartRunning("worker-1"),
            DeployEvent.CompileComplete,
            DeployEvent.StartSinking,
            DeployEvent.Complete,
            DeployEvent.Failed("another error")
        ).forEach { event ->
            val result = DeployStateMachine.transition(DeployState.FAILED, event)
            result.shouldBeInstanceOf<Either.Left<StateError>>()
            result.leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()
        }
    }

    // ==================== 코너 케이스 ====================

    "코너: QUEUED → CompileComplete는 InvalidTransition (순서 위반)" {
        val result = DeployStateMachine.transition(
            DeployState.QUEUED,
            DeployEvent.CompileComplete
        )
        result.shouldBeInstanceOf<Either.Left<StateError>>()
        result.leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()
    }

    "코너: RUNNING → StartRunning는 InvalidTransition (중복 시작)" {
        val result = DeployStateMachine.transition(
            DeployState.RUNNING,
            DeployEvent.StartRunning("worker-2")
        )
        result.shouldBeInstanceOf<Either.Left<StateError>>()
        result.leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()
    }

    "코너: READY → CompileComplete는 InvalidTransition (이미 완료됨)" {
        val result = DeployStateMachine.transition(
            DeployState.READY,
            DeployEvent.CompileComplete
        )
        result.shouldBeInstanceOf<Either.Left<StateError>>()
        result.leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()
    }

    "코너: SINKING → StartSinking는 InvalidTransition (중복 시작)" {
        val result = DeployStateMachine.transition(
            DeployState.SINKING,
            DeployEvent.StartSinking
        )
        result.shouldBeInstanceOf<Either.Left<StateError>>()
        result.leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()
    }

    "코너: QUEUED → StartSinking는 InvalidTransition (상태 스킵)" {
        val result = DeployStateMachine.transition(
            DeployState.QUEUED,
            DeployEvent.StartSinking
        )
        result.shouldBeInstanceOf<Either.Left<StateError>>()
        result.leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()
    }

    "코너: RUNNING → StartSinking는 InvalidTransition (READY 스킵)" {
        val result = DeployStateMachine.transition(
            DeployState.RUNNING,
            DeployEvent.StartSinking
        )
        result.shouldBeInstanceOf<Either.Left<StateError>>()
        result.leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()
    }

    // ==================== 수학적 완결성: 30개 조합 전체 검증 ====================

    "수학적 완결성: QUEUED × 5 events" {
        val state = DeployState.QUEUED

        // StartRunning → RUNNING (✓)
        DeployStateMachine.transition(state, DeployEvent.StartRunning("w1"))
            .getOrNull() shouldBe DeployState.RUNNING

        // CompileComplete → Error (❌)
        DeployStateMachine.transition(state, DeployEvent.CompileComplete)
            .leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()

        // StartSinking → Error (❌)
        DeployStateMachine.transition(state, DeployEvent.StartSinking)
            .leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()

        // Complete → Error (❌)
        DeployStateMachine.transition(state, DeployEvent.Complete)
            .leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()

        // Failed → FAILED (✓)
        DeployStateMachine.transition(state, DeployEvent.Failed("err"))
            .getOrNull() shouldBe DeployState.FAILED
    }

    "수학적 완결성: RUNNING × 5 events" {
        val state = DeployState.RUNNING

        // StartRunning → Error (❌)
        DeployStateMachine.transition(state, DeployEvent.StartRunning("w1"))
            .leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()

        // CompileComplete → READY (✓)
        DeployStateMachine.transition(state, DeployEvent.CompileComplete)
            .getOrNull() shouldBe DeployState.READY

        // StartSinking → Error (❌)
        DeployStateMachine.transition(state, DeployEvent.StartSinking)
            .leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()

        // Complete → Error (❌)
        DeployStateMachine.transition(state, DeployEvent.Complete)
            .leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()

        // Failed → FAILED (✓)
        DeployStateMachine.transition(state, DeployEvent.Failed("err"))
            .getOrNull() shouldBe DeployState.FAILED
    }

    "수학적 완결성: READY × 5 events" {
        val state = DeployState.READY

        // StartRunning → Error (❌)
        DeployStateMachine.transition(state, DeployEvent.StartRunning("w1"))
            .leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()

        // CompileComplete → Error (❌)
        DeployStateMachine.transition(state, DeployEvent.CompileComplete)
            .leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()

        // StartSinking → SINKING (✓)
        DeployStateMachine.transition(state, DeployEvent.StartSinking)
            .getOrNull() shouldBe DeployState.SINKING

        // Complete → Error (❌) - ship 스킵 불가
        DeployStateMachine.transition(state, DeployEvent.Complete)
            .leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()

        // Failed → FAILED (✓)
        DeployStateMachine.transition(state, DeployEvent.Failed("err"))
            .getOrNull() shouldBe DeployState.FAILED
    }

    "수학적 완결성: SINKING × 5 events" {
        val state = DeployState.SINKING

        // StartRunning → Error (❌)
        DeployStateMachine.transition(state, DeployEvent.StartRunning("w1"))
            .leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()

        // CompileComplete → Error (❌)
        DeployStateMachine.transition(state, DeployEvent.CompileComplete)
            .leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()

        // StartSinking → Error (❌)
        DeployStateMachine.transition(state, DeployEvent.StartSinking)
            .leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()

        // Complete → DONE (✓)
        DeployStateMachine.transition(state, DeployEvent.Complete)
            .getOrNull() shouldBe DeployState.DONE

        // Failed → FAILED (✓)
        DeployStateMachine.transition(state, DeployEvent.Failed("err"))
            .getOrNull() shouldBe DeployState.FAILED
    }

    "수학적 완결성: DONE × 5 events (모두 거부)" {
        val state = DeployState.DONE

        listOf(
            DeployEvent.StartRunning("w1"),
            DeployEvent.CompileComplete,
            DeployEvent.StartSinking,
            DeployEvent.Complete,
            DeployEvent.Failed("err")
        ).forEach { event ->
            DeployStateMachine.transition(state, event)
                .leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()
        }
    }

    "수학적 완결성: FAILED × 5 events (모두 거부)" {
        val state = DeployState.FAILED

        listOf(
            DeployEvent.StartRunning("w1"),
            DeployEvent.CompileComplete,
            DeployEvent.StartSinking,
            DeployEvent.Complete,
            DeployEvent.Failed("err")
        ).forEach { event ->
            DeployStateMachine.transition(state, event)
                .leftOrNull().shouldBeInstanceOf<StateError.InvalidTransition>()
        }
    }

    // ==================== Reachability 검증 ====================

    "Reachability: QUEUED에서 RUNNING 도달 가능" {
        val result = DeployStateMachine.transition(
            DeployState.QUEUED,
            DeployEvent.StartRunning("w1")
        )
        result.getOrNull() shouldBe DeployState.RUNNING
    }

    "Reachability: QUEUED에서 READY 도달 가능 (2단계)" {
        var state = DeployState.QUEUED
        state = DeployStateMachine.transition(state, DeployEvent.StartRunning("w1")).getOrNull()!!
        state = DeployStateMachine.transition(state, DeployEvent.CompileComplete).getOrNull()!!
        state shouldBe DeployState.READY
    }

    "Reachability: QUEUED에서 SINKING 도달 가능 (3단계)" {
        var state = DeployState.QUEUED
        state = DeployStateMachine.transition(state, DeployEvent.StartRunning("w1")).getOrNull()!!
        state = DeployStateMachine.transition(state, DeployEvent.CompileComplete).getOrNull()!!
        state = DeployStateMachine.transition(state, DeployEvent.StartSinking).getOrNull()!!
        state shouldBe DeployState.SINKING
    }

    "Reachability: QUEUED에서 DONE 도달 가능 (4단계)" {
        var state = DeployState.QUEUED
        state = DeployStateMachine.transition(state, DeployEvent.StartRunning("w1")).getOrNull()!!
        state = DeployStateMachine.transition(state, DeployEvent.CompileComplete).getOrNull()!!
        state = DeployStateMachine.transition(state, DeployEvent.StartSinking).getOrNull()!!
        state = DeployStateMachine.transition(state, DeployEvent.Complete).getOrNull()!!
        state shouldBe DeployState.DONE
    }

    "Reachability: QUEUED에서 FAILED 직접 도달 가능" {
        val state = DeployStateMachine.transition(
            DeployState.QUEUED,
            DeployEvent.Failed("err")
        ).getOrNull()
        state shouldBe DeployState.FAILED
    }

    "Reachability: 모든 non-terminal state에서 FAILED 도달 가능" {
        listOf(
            DeployState.QUEUED,
            DeployState.RUNNING,
            DeployState.READY,
            DeployState.SINKING
        ).forEach { state ->
            val result = DeployStateMachine.transition(state, DeployEvent.Failed("err"))
            result.getOrNull() shouldBe DeployState.FAILED
        }
    }

    // ==================== Determinism 검증 ====================

    "Determinism: 동일 입력 → 동일 출력 (반복 100회)" {
        repeat(100) {
            val result = DeployStateMachine.transition(
                DeployState.QUEUED,
                DeployEvent.StartRunning("worker-1")
            )
            result.getOrNull() shouldBe DeployState.RUNNING
        }
    }

    "Determinism: 동일 시퀀스 → 동일 최종 상태 (반복 50회)" {
        repeat(50) {
            var state = DeployState.QUEUED
            state = DeployStateMachine.transition(state, DeployEvent.StartRunning("w1")).getOrNull()!!
            state = DeployStateMachine.transition(state, DeployEvent.CompileComplete).getOrNull()!!
            state = DeployStateMachine.transition(state, DeployEvent.StartSinking).getOrNull()!!
            state = DeployStateMachine.transition(state, DeployEvent.Complete).getOrNull()!!
            state shouldBe DeployState.DONE
        }
    }

    // ==================== InvalidTransition 상세 검증 ====================

    "InvalidTransition: current와 event 정보 포함" {
        val result = DeployStateMachine.transition(
            DeployState.QUEUED,
            DeployEvent.CompileComplete
        )
        val error = result.leftOrNull() as StateError.InvalidTransition
        error.current shouldBe DeployState.QUEUED
        error.event shouldBe DeployEvent.CompileComplete
    }

    "InvalidTransition: Failed 이벤트 정보 보존" {
        val failedEvent = DeployEvent.Failed("critical error")
        val result = DeployStateMachine.transition(DeployState.DONE, failedEvent)
        val error = result.leftOrNull() as StateError.InvalidTransition
        error.event shouldBe failedEvent
        (error.event as DeployEvent.Failed).error shouldBe "critical error"
    }
})
