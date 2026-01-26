package com.oliveyoung.ivmlite.sdk.execution

import com.oliveyoung.ivmlite.sdk.model.DeployEvent
import com.oliveyoung.ivmlite.sdk.model.DeployState
import com.oliveyoung.ivmlite.sdk.model.Either
import com.oliveyoung.ivmlite.sdk.model.StateError
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf

/**
 * DeployStateMachine 수학적 완결성 테스트
 * RFC-IMPL-011 Wave 2-G
 *
 * 목표: 모든 상태 × 이벤트 조합(6 × 5 = 30개) 검증
 */
class StateMachineCompletenessTest : DescribeSpec({
    describe("상태 전이 행렬 완결성 검증") {
        context("QUEUED 상태에서") {
            it("StartRunning → RUNNING (허용)") {
                val result = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.StartRunning("worker-1")
                )
                result shouldBe Either.Right(DeployState.RUNNING)
            }

            it("CompileComplete → InvalidTransition (거부)") {
                val result = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.CompileComplete
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("StartSinking → InvalidTransition (거부)") {
                val result = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.StartSinking
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("Complete → InvalidTransition (거부)") {
                val result = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.Complete
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("Failed → FAILED (허용)") {
                val result = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.Failed("error")
                )
                result shouldBe Either.Right(DeployState.FAILED)
            }
        }

        context("RUNNING 상태에서") {
            it("StartRunning → InvalidTransition (중복 시작 거부)") {
                val result = DeployStateMachine.transition(
                    DeployState.RUNNING,
                    DeployEvent.StartRunning("worker-2")
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("CompileComplete → READY (허용)") {
                val result = DeployStateMachine.transition(
                    DeployState.RUNNING,
                    DeployEvent.CompileComplete
                )
                result shouldBe Either.Right(DeployState.READY)
            }

            it("StartSinking → InvalidTransition (단계 건너뛰기 거부)") {
                val result = DeployStateMachine.transition(
                    DeployState.RUNNING,
                    DeployEvent.StartSinking
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("Complete → InvalidTransition (단계 건너뛰기 거부)") {
                val result = DeployStateMachine.transition(
                    DeployState.RUNNING,
                    DeployEvent.Complete
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("Failed → FAILED (허용)") {
                val result = DeployStateMachine.transition(
                    DeployState.RUNNING,
                    DeployEvent.Failed("compile error")
                )
                result shouldBe Either.Right(DeployState.FAILED)
            }
        }

        context("READY 상태에서") {
            it("StartRunning → InvalidTransition (역방향 거부)") {
                val result = DeployStateMachine.transition(
                    DeployState.READY,
                    DeployEvent.StartRunning("worker-1")
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("CompileComplete → InvalidTransition (중복 완료 거부)") {
                val result = DeployStateMachine.transition(
                    DeployState.READY,
                    DeployEvent.CompileComplete
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("StartSinking → SINKING (허용)") {
                val result = DeployStateMachine.transition(
                    DeployState.READY,
                    DeployEvent.StartSinking
                )
                result shouldBe Either.Right(DeployState.SINKING)
            }

            it("Complete → InvalidTransition (단계 건너뛰기 거부)") {
                val result = DeployStateMachine.transition(
                    DeployState.READY,
                    DeployEvent.Complete
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("Failed → FAILED (허용)") {
                val result = DeployStateMachine.transition(
                    DeployState.READY,
                    DeployEvent.Failed("ready error")
                )
                result shouldBe Either.Right(DeployState.FAILED)
            }
        }

        context("SINKING 상태에서") {
            it("StartRunning → InvalidTransition (역방향 거부)") {
                val result = DeployStateMachine.transition(
                    DeployState.SINKING,
                    DeployEvent.StartRunning("worker-1")
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("CompileComplete → InvalidTransition (역방향 거부)") {
                val result = DeployStateMachine.transition(
                    DeployState.SINKING,
                    DeployEvent.CompileComplete
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("StartSinking → InvalidTransition (중복 시작 거부)") {
                val result = DeployStateMachine.transition(
                    DeployState.SINKING,
                    DeployEvent.StartSinking
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("Complete → DONE (허용)") {
                val result = DeployStateMachine.transition(
                    DeployState.SINKING,
                    DeployEvent.Complete
                )
                result shouldBe Either.Right(DeployState.DONE)
            }

            it("Failed → FAILED (허용)") {
                val result = DeployStateMachine.transition(
                    DeployState.SINKING,
                    DeployEvent.Failed("sink error")
                )
                result shouldBe Either.Right(DeployState.FAILED)
            }
        }

        context("DONE 상태에서 (터미널 상태)") {
            it("StartRunning → InvalidTransition") {
                val result = DeployStateMachine.transition(
                    DeployState.DONE,
                    DeployEvent.StartRunning("worker-1")
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("CompileComplete → InvalidTransition") {
                val result = DeployStateMachine.transition(
                    DeployState.DONE,
                    DeployEvent.CompileComplete
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("StartSinking → InvalidTransition") {
                val result = DeployStateMachine.transition(
                    DeployState.DONE,
                    DeployEvent.StartSinking
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("Complete → InvalidTransition") {
                val result = DeployStateMachine.transition(
                    DeployState.DONE,
                    DeployEvent.Complete
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("Failed → InvalidTransition (터미널 상태에서 탈출 불가)") {
                val result = DeployStateMachine.transition(
                    DeployState.DONE,
                    DeployEvent.Failed("error")
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }
        }

        context("FAILED 상태에서 (터미널 상태)") {
            it("StartRunning → InvalidTransition") {
                val result = DeployStateMachine.transition(
                    DeployState.FAILED,
                    DeployEvent.StartRunning("worker-1")
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("CompileComplete → InvalidTransition") {
                val result = DeployStateMachine.transition(
                    DeployState.FAILED,
                    DeployEvent.CompileComplete
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("StartSinking → InvalidTransition") {
                val result = DeployStateMachine.transition(
                    DeployState.FAILED,
                    DeployEvent.StartSinking
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("Complete → InvalidTransition") {
                val result = DeployStateMachine.transition(
                    DeployState.FAILED,
                    DeployEvent.Complete
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("Failed → InvalidTransition (이미 실패 상태)") {
                val result = DeployStateMachine.transition(
                    DeployState.FAILED,
                    DeployEvent.Failed("another error")
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }
        }
    }

    describe("Property-based 검증") {
        context("터미널 상태 불변성") {
            val terminalStates = listOf(DeployState.DONE, DeployState.FAILED)
            val allEvents = listOf(
                DeployEvent.StartRunning("worker-1"),
                DeployEvent.CompileComplete,
                DeployEvent.StartSinking,
                DeployEvent.Complete,
                DeployEvent.Failed("error")
            )

            terminalStates.forEach { terminalState ->
                allEvents.forEach { event ->
                    it("$terminalState 에서 $event 이벤트는 항상 InvalidTransition") {
                        val result = DeployStateMachine.transition(terminalState, event)
                        result should beInstanceOf<Either.Left<StateError>>()
                        result.leftOrNull() shouldBe StateError.InvalidTransition(terminalState, event)
                    }
                }
            }
        }

        context("Failed 이벤트 전방향성") {
            val nonTerminalStates = listOf(
                DeployState.QUEUED,
                DeployState.RUNNING,
                DeployState.READY,
                DeployState.SINKING
            )

            nonTerminalStates.forEach { state ->
                it("$state 에서 Failed 이벤트는 항상 FAILED로 전이") {
                    val result = DeployStateMachine.transition(
                        state,
                        DeployEvent.Failed("error from $state")
                    )
                    result shouldBe Either.Right(DeployState.FAILED)
                }
            }
        }

        context("정상 경로 단방향성 (역방향 불가)") {
            it("RUNNING → QUEUED 불가능 (StartRunning은 QUEUED에서만)") {
                val result = DeployStateMachine.transition(
                    DeployState.RUNNING,
                    DeployEvent.StartRunning("worker-1")
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("READY → RUNNING 불가능 (CompileComplete는 RUNNING에서만)") {
                val result = DeployStateMachine.transition(
                    DeployState.READY,
                    DeployEvent.CompileComplete
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("SINKING → READY 불가능 (StartSinking은 READY에서만)") {
                val result = DeployStateMachine.transition(
                    DeployState.SINKING,
                    DeployEvent.StartSinking
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("DONE → SINKING 불가능 (Complete는 SINKING에서만)") {
                val result = DeployStateMachine.transition(
                    DeployState.DONE,
                    DeployEvent.Complete
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }
        }

        context("단계 건너뛰기 불가") {
            it("QUEUED → READY 직접 전이 불가") {
                val skipResult = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.CompileComplete
                )
                skipResult should beInstanceOf<Either.Left<StateError>>()
            }

            it("QUEUED → SINKING 직접 전이 불가") {
                val skipResult = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.StartSinking
                )
                skipResult should beInstanceOf<Either.Left<StateError>>()
            }

            it("QUEUED → DONE 직접 전이 불가") {
                val skipResult = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.Complete
                )
                skipResult should beInstanceOf<Either.Left<StateError>>()
            }

            it("RUNNING → SINKING 직접 전이 불가") {
                val skipResult = DeployStateMachine.transition(
                    DeployState.RUNNING,
                    DeployEvent.StartSinking
                )
                skipResult should beInstanceOf<Either.Left<StateError>>()
            }

            it("RUNNING → DONE 직접 전이 불가") {
                val skipResult = DeployStateMachine.transition(
                    DeployState.RUNNING,
                    DeployEvent.Complete
                )
                skipResult should beInstanceOf<Either.Left<StateError>>()
            }

            it("READY → DONE 직접 전이 불가") {
                val skipResult = DeployStateMachine.transition(
                    DeployState.READY,
                    DeployEvent.Complete
                )
                skipResult should beInstanceOf<Either.Left<StateError>>()
            }
        }
    }

    describe("엣지 케이스") {
        context("특수 입력값") {
            it("workerId가 빈 문자열") {
                val result = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.StartRunning("")
                )
                result shouldBe Either.Right(DeployState.RUNNING)
            }

            it("workerId가 특수문자 포함") {
                val result = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.StartRunning("worker-!@#$%^&*()")
                )
                result shouldBe Either.Right(DeployState.RUNNING)
            }

            it("error 메시지가 빈 문자열") {
                val result = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.Failed("")
                )
                result shouldBe Either.Right(DeployState.FAILED)
            }

            it("error 메시지가 매우 긴 문자열") {
                val longError = "error".repeat(1000)
                val result = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.Failed(longError)
                )
                result shouldBe Either.Right(DeployState.FAILED)
            }
        }

        context("연속 전이") {
            it("QUEUED → RUNNING → READY 연속 전이 검증") {
                val step1 = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.StartRunning("w1")
                )
                step1.getOrNull() shouldBe DeployState.RUNNING

                val step2 = DeployStateMachine.transition(
                    step1.getOrNull()!!,
                    DeployEvent.CompileComplete
                )
                step2.getOrNull() shouldBe DeployState.READY
            }

            it("전체 플로우 3회 반복 (멱등성)") {
                repeat(3) {
                    var state = DeployState.QUEUED
                    state = DeployStateMachine.transition(state, DeployEvent.StartRunning("w$it"))
                        .getOrNull()!!
                    state = DeployStateMachine.transition(state, DeployEvent.CompileComplete)
                        .getOrNull()!!
                    state = DeployStateMachine.transition(state, DeployEvent.StartSinking)
                        .getOrNull()!!
                    state = DeployStateMachine.transition(state, DeployEvent.Complete)
                        .getOrNull()!!
                    state shouldBe DeployState.DONE
                }
            }
        }
    }

    describe("코너 케이스") {
        context("중복 이벤트 적용") {
            it("QUEUED에서 StartRunning 중복 적용 불가 (RUNNING에서 거부)") {
                val step1 = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.StartRunning("w1")
                )
                step1.getOrNull() shouldBe DeployState.RUNNING

                val step2 = DeployStateMachine.transition(
                    DeployState.RUNNING,
                    DeployEvent.StartRunning("w2")
                )
                step2 should beInstanceOf<Either.Left<StateError>>()
            }

            it("CompileComplete 중복 적용 불가") {
                val step1 = DeployStateMachine.transition(
                    DeployState.RUNNING,
                    DeployEvent.CompileComplete
                )
                step1.getOrNull() shouldBe DeployState.READY

                val step2 = DeployStateMachine.transition(
                    DeployState.READY,
                    DeployEvent.CompileComplete
                )
                step2 should beInstanceOf<Either.Left<StateError>>()
            }
        }

        context("StateError 내용 검증") {
            it("InvalidTransition은 현재 상태와 이벤트를 포함") {
                val result = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.CompileComplete
                )
                val error = result.leftOrNull()
                error shouldBe StateError.InvalidTransition(
                    DeployState.QUEUED,
                    DeployEvent.CompileComplete
                )
            }
        }
    }

    describe("수학적 완결성 매트릭스 검증") {
        it("전체 6×5=30개 조합이 모두 정의됨") {
            val states = DeployState.values()
            val events = listOf(
                DeployEvent.StartRunning("w1"),
                DeployEvent.CompileComplete,
                DeployEvent.StartSinking,
                DeployEvent.Complete,
                DeployEvent.Failed("e")
            )

            states.forEach { state ->
                events.forEach { event ->
                    // 모든 조합이 예외 없이 Either를 반환해야 함
                    val result = DeployStateMachine.transition(state, event)
                    result should beInstanceOf<Either<*, *>>()
                }
            }
        }
    }
})
