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
 * DeployStateMachine 고급 테스트
 * - 전이 체인 검증
 * - workerId 다양성
 * - 수학적 완결성 매트릭스
 */
class StateMachineAdvancedTest : DescribeSpec({
    describe("DeployStateMachine 고급 테스트") {

        context("전이 체인: 성공 시나리오") {
            it("QUEUED → RUNNING → READY → SINKING → DONE 완전 체인") {
                var state = DeployState.QUEUED

                // QUEUED → RUNNING
                state = when (val r = DeployStateMachine.transition(
                    state,
                    DeployEvent.StartRunning("worker-1")
                )) {
                    is Either.Right -> r.value
                    is Either.Left -> throw AssertionError("Should succeed")
                }
                state shouldBe DeployState.RUNNING

                // RUNNING → READY
                state = when (val r = DeployStateMachine.transition(
                    state,
                    DeployEvent.CompileComplete
                )) {
                    is Either.Right -> r.value
                    is Either.Left -> throw AssertionError("Should succeed")
                }
                state shouldBe DeployState.READY

                // READY → SINKING
                state = when (val r = DeployStateMachine.transition(
                    state,
                    DeployEvent.StartSinking
                )) {
                    is Either.Right -> r.value
                    is Either.Left -> throw AssertionError("Should succeed")
                }
                state shouldBe DeployState.SINKING

                // SINKING → DONE
                state = when (val r = DeployStateMachine.transition(
                    state,
                    DeployEvent.Complete
                )) {
                    is Either.Right -> r.value
                    is Either.Left -> throw AssertionError("Should succeed")
                }
                state shouldBe DeployState.DONE

                // DONE에서 추가 전이 불가
                val result = DeployStateMachine.transition(
                    state,
                    DeployEvent.StartRunning("worker-2")
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }
        }

        context("전이 체인: 실패 시나리오") {
            it("QUEUED에서 즉시 실패") {
                var state = DeployState.QUEUED

                state = DeployStateMachine.transition(
                    state,
                    DeployEvent.Failed("queue error")
                ).getOrNull()!!

                state shouldBe DeployState.FAILED

                // FAILED에서 추가 전이 불가
                val result = DeployStateMachine.transition(
                    state,
                    DeployEvent.StartRunning("w")
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("RUNNING에서 컴파일 실패") {
                var state = DeployState.QUEUED

                state = DeployStateMachine.transition(
                    state,
                    DeployEvent.StartRunning("w1")
                ).getOrNull()!!

                state = DeployStateMachine.transition(
                    state,
                    DeployEvent.Failed("compile error")
                ).getOrNull()!!

                state shouldBe DeployState.FAILED
            }

            it("READY에서 pre-sink 실패") {
                var state = DeployState.QUEUED

                state = DeployStateMachine.transition(
                    state,
                    DeployEvent.StartRunning("w1")
                ).getOrNull()!!

                state = DeployStateMachine.transition(
                    state,
                    DeployEvent.CompileComplete
                ).getOrNull()!!

                state = DeployStateMachine.transition(
                    state,
                    DeployEvent.Failed("validation error")
                ).getOrNull()!!

                state shouldBe DeployState.FAILED
            }

            it("SINKING에서 sink 실패") {
                var state = DeployState.QUEUED

                state = DeployStateMachine.transition(
                    state,
                    DeployEvent.StartRunning("w1")
                ).getOrNull()!!

                state = DeployStateMachine.transition(
                    state,
                    DeployEvent.CompileComplete
                ).getOrNull()!!

                state = DeployStateMachine.transition(
                    state,
                    DeployEvent.StartSinking
                ).getOrNull()!!

                state = DeployStateMachine.transition(
                    state,
                    DeployEvent.Failed("sink timeout")
                ).getOrNull()!!

                state shouldBe DeployState.FAILED
            }
        }

        context("workerId 다양성") {
            it("다양한 workerId 형식 - 모두 동일한 전이") {
                val workerIds = listOf(
                    "worker-1",
                    "worker-2",
                    "w",
                    "W",
                    "",
                    " ",
                    "worker-".repeat(50),
                    "한글워커",
                    "特殊字符",
                    "!@#$%^&*()",
                    "123456789",
                    "a".repeat(1000)
                )

                workerIds.forEach { workerId ->
                    val result = DeployStateMachine.transition(
                        DeployState.QUEUED,
                        DeployEvent.StartRunning(workerId)
                    )
                    result shouldBe Either.Right(DeployState.RUNNING)
                }
            }

            it("workerId는 전이 결과에 영향 없음 (상태 독립성)") {
                val results = (1..10).map { i ->
                    DeployStateMachine.transition(
                        DeployState.QUEUED,
                        DeployEvent.StartRunning("worker-$i")
                    )
                }

                results.forEach { result ->
                    result shouldBe Either.Right(DeployState.RUNNING)
                }
            }
        }

        context("Failed 이벤트 에러 메시지 다양성") {
            it("다양한 에러 메시지 - 전이는 동일") {
                val errorMessages = listOf(
                    "compile error",
                    "timeout",
                    "out of memory",
                    "",
                    " ",
                    "특수문자!@#$%^&*()",
                    "a".repeat(10000),
                    "줄바꿈\n포함\n메시지",
                    "탭\t문자\t포함"
                )

                errorMessages.forEach { msg ->
                    val result = DeployStateMachine.transition(
                        DeployState.RUNNING,
                        DeployEvent.Failed(msg)
                    )
                    result shouldBe Either.Right(DeployState.FAILED)
                }
            }
        }

        context("수학적 완결성: 전이 매트릭스 (6×5=30)") {
            it("QUEUED: 2개 성공 (StartRunning, Failed), 3개 실패") {
                val transitions = mapOf(
                    DeployEvent.StartRunning("w") to Either.Right(DeployState.RUNNING),
                    DeployEvent.CompileComplete to null, // Left
                    DeployEvent.StartSinking to null, // Left
                    DeployEvent.Complete to null, // Left
                    DeployEvent.Failed("e") to Either.Right(DeployState.FAILED)
                )

                transitions.forEach { (event, expected) ->
                    val result = DeployStateMachine.transition(DeployState.QUEUED, event)
                    if (expected != null) {
                        result shouldBe expected
                    } else {
                        result should beInstanceOf<Either.Left<StateError>>()
                    }
                }
            }

            it("RUNNING: 2개 성공 (CompileComplete, Failed), 3개 실패") {
                val transitions = mapOf(
                    DeployEvent.StartRunning("w") to null, // Left
                    DeployEvent.CompileComplete to Either.Right(DeployState.READY),
                    DeployEvent.StartSinking to null, // Left
                    DeployEvent.Complete to null, // Left
                    DeployEvent.Failed("e") to Either.Right(DeployState.FAILED)
                )

                transitions.forEach { (event, expected) ->
                    val result = DeployStateMachine.transition(DeployState.RUNNING, event)
                    if (expected != null) {
                        result shouldBe expected
                    } else {
                        result should beInstanceOf<Either.Left<StateError>>()
                    }
                }
            }

            it("READY: 2개 성공 (StartSinking, Failed), 3개 실패") {
                val transitions = mapOf(
                    DeployEvent.StartRunning("w") to null, // Left
                    DeployEvent.CompileComplete to null, // Left
                    DeployEvent.StartSinking to Either.Right(DeployState.SINKING),
                    DeployEvent.Complete to null, // Left
                    DeployEvent.Failed("e") to Either.Right(DeployState.FAILED)
                )

                transitions.forEach { (event, expected) ->
                    val result = DeployStateMachine.transition(DeployState.READY, event)
                    if (expected != null) {
                        result shouldBe expected
                    } else {
                        result should beInstanceOf<Either.Left<StateError>>()
                    }
                }
            }

            it("SINKING: 2개 성공 (Complete, Failed), 3개 실패") {
                val transitions = mapOf(
                    DeployEvent.StartRunning("w") to null, // Left
                    DeployEvent.CompileComplete to null, // Left
                    DeployEvent.StartSinking to null, // Left
                    DeployEvent.Complete to Either.Right(DeployState.DONE),
                    DeployEvent.Failed("e") to Either.Right(DeployState.FAILED)
                )

                transitions.forEach { (event, expected) ->
                    val result = DeployStateMachine.transition(DeployState.SINKING, event)
                    if (expected != null) {
                        result shouldBe expected
                    } else {
                        result should beInstanceOf<Either.Left<StateError>>()
                    }
                }
            }

            it("DONE: 0개 성공, 5개 실패 (Terminal State)") {
                val events = listOf(
                    DeployEvent.StartRunning("w"),
                    DeployEvent.CompileComplete,
                    DeployEvent.StartSinking,
                    DeployEvent.Complete,
                    DeployEvent.Failed("e")
                )

                events.forEach { event ->
                    val result = DeployStateMachine.transition(DeployState.DONE, event)
                    result should beInstanceOf<Either.Left<StateError>>()
                }
            }

            it("FAILED: 0개 성공, 5개 실패 (Terminal State)") {
                val events = listOf(
                    DeployEvent.StartRunning("w"),
                    DeployEvent.CompileComplete,
                    DeployEvent.StartSinking,
                    DeployEvent.Complete,
                    DeployEvent.Failed("e")
                )

                events.forEach { event ->
                    val result = DeployStateMachine.transition(DeployState.FAILED, event)
                    result should beInstanceOf<Either.Left<StateError>>()
                }
            }

            it("전체 30개 조합: 9개 성공, 21개 실패") {
                val states = DeployState.values().toList()
                val events = listOf(
                    DeployEvent.StartRunning("w"),
                    DeployEvent.CompileComplete,
                    DeployEvent.StartSinking,
                    DeployEvent.Complete,
                    DeployEvent.Failed("e")
                )

                var successCount = 0
                var failureCount = 0

                states.forEach { state ->
                    events.forEach { event ->
                        val result = DeployStateMachine.transition(state, event)
                        when (result) {
                            is Either.Right -> successCount++
                            is Either.Left -> failureCount++
                        }
                    }
                }

                // 유효한 전이: QUEUED→RUNNING, QUEUED→FAILED, RUNNING→READY, RUNNING→FAILED,
                // READY→SINKING, READY→FAILED, SINKING→DONE, SINKING→FAILED = 8개
                successCount shouldBe 8
                failureCount shouldBe 22
            }
        }

        context("StateError 정확성 검증") {
            it("InvalidTransition은 올바른 상태/이벤트 포함") {
                val testCases = listOf(
                    DeployState.QUEUED to DeployEvent.Complete,
                    DeployState.QUEUED to DeployEvent.StartSinking,
                    DeployState.RUNNING to DeployEvent.StartRunning("w"),
                    DeployState.RUNNING to DeployEvent.StartSinking,
                    DeployState.READY to DeployEvent.CompileComplete,
                    DeployState.READY to DeployEvent.Complete,
                    DeployState.SINKING to DeployEvent.StartSinking,
                    DeployState.DONE to DeployEvent.StartRunning("w"),
                    DeployState.FAILED to DeployEvent.Failed("e")
                )

                testCases.forEach { (state, event) ->
                    val result = DeployStateMachine.transition(state, event)
                    result.leftOrNull() shouldBe StateError.InvalidTransition(state, event)
                }
            }
        }

        context("불변성: 순수 함수 특성") {
            it("동일 입력 → 동일 출력 (멱등성)") {
                val state = DeployState.QUEUED
                val event = DeployEvent.StartRunning("w1")

                val results = (1..100).map {
                    DeployStateMachine.transition(state, event)
                }

                results.forEach { result ->
                    result shouldBe Either.Right(DeployState.RUNNING)
                }
            }

            it("전이는 입력을 변경하지 않음 (불변성)") {
                val originalState = DeployState.QUEUED
                val originalEvent = DeployEvent.StartRunning("w1")

                DeployStateMachine.transition(originalState, originalEvent)

                // 입력이 변경되지 않았는지 확인
                originalState shouldBe DeployState.QUEUED
                originalEvent shouldBe DeployEvent.StartRunning("w1")
            }
        }

        context("코너 케이스: 극단적 전이 시도") {
            it("QUEUED에서 Complete로 4단계 스킵 시도") {
                val result = DeployStateMachine.transition(
                    DeployState.QUEUED,
                    DeployEvent.Complete
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("SINKING에서 StartRunning으로 역행 시도") {
                val result = DeployStateMachine.transition(
                    DeployState.SINKING,
                    DeployEvent.StartRunning("w")
                )
                result should beInstanceOf<Either.Left<StateError>>()
            }

            it("DONE에서 모든 이벤트 거부 확인") {
                val allEvents = listOf(
                    DeployEvent.StartRunning("w"),
                    DeployEvent.CompileComplete,
                    DeployEvent.StartSinking,
                    DeployEvent.Complete,
                    DeployEvent.Failed("e")
                )

                allEvents.forEach { event ->
                    val result = DeployStateMachine.transition(DeployState.DONE, event)
                    result should beInstanceOf<Either.Left<StateError>>()
                }
            }

            it("FAILED에서 모든 이벤트 거부 확인") {
                val allEvents = listOf(
                    DeployEvent.StartRunning("w"),
                    DeployEvent.CompileComplete,
                    DeployEvent.StartSinking,
                    DeployEvent.Complete,
                    DeployEvent.Failed("another error")
                )

                allEvents.forEach { event ->
                    val result = DeployStateMachine.transition(DeployState.FAILED, event)
                    result should beInstanceOf<Either.Left<StateError>>()
                }
            }
        }
    }
})
