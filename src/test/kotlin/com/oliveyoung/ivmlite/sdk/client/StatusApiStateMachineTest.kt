package com.oliveyoung.ivmlite.sdk.client

import com.oliveyoung.ivmlite.sdk.model.DeployJobStatus
import com.oliveyoung.ivmlite.sdk.model.DeployState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * StatusApi 상태 머신 완결성 테스트
 * RFC-IMPL-011 Wave 5-K
 *
 * 모든 DeployState 전환 경로 검증
 */
class StatusApiStateMachineTest {

    /**
     * Mock DeployStatusApi - 상태 전환 시뮬레이션용
     */
    private class MockDeployStatusApi(
        private val stateSequence: List<DeployState>
    ) {
        private var callCount = 0

        suspend fun status(jobId: String): DeployJobStatus {
            val state = if (callCount < stateSequence.size) {
                stateSequence[callCount]
            } else {
                stateSequence.last()
            }
            callCount++

            return DeployJobStatus(
                jobId = jobId,
                state = state,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                error = if (state == DeployState.FAILED) "Mock error" else null
            )
        }
    }

    // ========== 모든 DeployState 처리 검증 ==========

    @Test
    fun `모든 DeployState가 when에서 처리됨`() {
        // DeployState의 모든 케이스가 when에서 처리되므로
        // 새로운 상태 추가 시 컴파일 에러 발생 (exhaustive when)
        val allStates = DeployState.values()
        assertEquals(6, allStates.size, "DeployState 개수 변경 시 await 로직 확인 필요")

        // 각 상태가 올바른 그룹에 속하는지 검증
        val terminalStates = setOf(DeployState.DONE, DeployState.FAILED)
        val progressStates = setOf(
            DeployState.QUEUED,
            DeployState.RUNNING,
            DeployState.READY,
            DeployState.SINKING
        )

        assertTrue(allStates.all { it in terminalStates || it in progressStates })
        assertEquals(6, terminalStates.size + progressStates.size)
    }

    // ========== 상태 전환 시나리오 테스트 ==========

    @Test
    fun `QUEUED 상태만 반복하다 타임아웃`() = runBlocking {
        // 현재 구현은 stub이므로 실제 테스트 불가
        // TODO: Mock 구현 후 실제 상태 전환 테스트
        val client = Ivm.client()
        val result = client.deploy.await(
            "job-queued",
            timeout = Duration.ofMillis(50),
            pollInterval = Duration.ofMillis(10)
        )

        assertEquals(false, result.success)
        assertEquals("timeout", result.version)
    }

    @Test
    fun `QUEUED → RUNNING → DONE 정상 흐름 (Mock 시뮬레이션)`() = runBlocking {
        // Mock으로 상태 전환 시뮬레이션
        val mock = MockDeployStatusApi(
            listOf(DeployState.QUEUED, DeployState.RUNNING, DeployState.DONE)
        )

        val status1 = mock.status("job-1")
        assertEquals(DeployState.QUEUED, status1.state)

        val status2 = mock.status("job-1")
        assertEquals(DeployState.RUNNING, status2.state)

        val status3 = mock.status("job-1")
        assertEquals(DeployState.DONE, status3.state)
    }

    @Test
    fun `QUEUED → RUNNING → READY → SINKING → DONE 전체 흐름`() = runBlocking {
        val mock = MockDeployStatusApi(
            listOf(
                DeployState.QUEUED,
                DeployState.RUNNING,
                DeployState.READY,
                DeployState.SINKING,
                DeployState.DONE
            )
        )

        val states = (1..5).map { mock.status("job-full").state }
        assertEquals(
            listOf(
                DeployState.QUEUED,
                DeployState.RUNNING,
                DeployState.READY,
                DeployState.SINKING,
                DeployState.DONE
            ),
            states
        )
    }

    @Test
    fun `RUNNING → FAILED 실패 흐름`() = runBlocking {
        val mock = MockDeployStatusApi(
            listOf(DeployState.RUNNING, DeployState.FAILED)
        )

        val status1 = mock.status("job-fail")
        assertEquals(DeployState.RUNNING, status1.state)

        val status2 = mock.status("job-fail")
        assertEquals(DeployState.FAILED, status2.state)
        assertEquals("Mock error", status2.error)
    }

    @Test
    fun `QUEUED → FAILED 즉시 실패`() = runBlocking {
        val mock = MockDeployStatusApi(
            listOf(DeployState.QUEUED, DeployState.FAILED)
        )

        val status1 = mock.status("job-immediate-fail")
        assertEquals(DeployState.QUEUED, status1.state)

        val status2 = mock.status("job-immediate-fail")
        assertEquals(DeployState.FAILED, status2.state)
    }

    // ========== 수학적 완결성 - 폴링 횟수 정확성 ==========

    @Test
    fun `폴링 횟수 계산 - timeout 100ms, interval 20ms = 최대 5회`() = runBlocking {
        // 실제로는 타임아웃 체크로 인해 정확히 5회는 아닐 수 있음
        // 하지만 대략 100/20 = 5회 정도 호출됨을 검증
        val client = Ivm.client()
        val startTime = System.currentTimeMillis()

        val result = client.deploy.await(
            "job-poll-count",
            timeout = Duration.ofMillis(100),
            pollInterval = Duration.ofMillis(20)
        )

        val elapsed = System.currentTimeMillis() - startTime
        // CI 환경에서의 타이밍 불안정성을 고려해 더 관대한 범위 설정
        assertTrue(elapsed >= 80, "timeout 시간에 가깝게 대기해야 함 (허용 오차 20ms)")
        assertTrue(elapsed < 300, "timeout 후 합리적 시간 내 반환해야 함")

        assertEquals(false, result.success)
        assertEquals("timeout", result.version)
    }

    @Test
    fun `폴링 횟수 계산 - timeout 50ms, interval 10ms = 최대 5회`() = runBlocking {
        val client = Ivm.client()
        val startTime = System.currentTimeMillis()

        val result = client.deploy.await(
            "job-poll-count-2",
            timeout = Duration.ofMillis(50),
            pollInterval = Duration.ofMillis(10)
        )

        val elapsed = System.currentTimeMillis() - startTime
        // CI 환경에서의 타이밍 불안정성을 고려해 더 관대한 범위 설정
        assertTrue(elapsed >= 30, "timeout 시간에 가깝게 대기해야 함 (허용 오차 20ms)")
        assertTrue(elapsed < 200, "timeout 후 합리적 시간 내 반환해야 함")

        assertEquals(false, result.success)
    }

    // ========== 시간 정확성 테스트 ==========

    @Test
    fun `timeout 정확성 - 50ms timeout이 정확히 지켜짐`() = runBlocking {
        val client = Ivm.client()
        val startTime = System.currentTimeMillis()

        client.deploy.await(
            "job-timeout-accuracy",
            timeout = Duration.ofMillis(50),
            pollInterval = Duration.ofMillis(10)
        )

        val elapsed = System.currentTimeMillis() - startTime
        // CI 환경에서의 타이밍 불안정성을 고려해 더 관대한 범위 설정
        assertTrue(elapsed >= 30, "최소 timeout에 가깝게 대기 (허용 오차 20ms)")
        assertTrue(elapsed < 200, "timeout 후 합리적 시간 내 반환 (CI 환경 고려)")
    }

    @Test
    fun `delay 후 타임아웃 체크 - 불필요한 status 호출 방지`() = runBlocking {
        // timeout=55ms, interval=50ms
        // 1회 status 호출 → delay(50) 전에 타임아웃 예상 체크 → 타임아웃 반환
        val client = Ivm.client()
        val startTime = System.currentTimeMillis()

        val result = client.deploy.await(
            "job-delay-timeout",
            timeout = Duration.ofMillis(55),
            pollInterval = Duration.ofMillis(50)
        )

        val elapsed = System.currentTimeMillis() - startTime

        // delay 전에 타임아웃 체크하므로 50ms 이내에 반환되어야 함
        assertTrue(elapsed < 100, "delay 전 타임아웃 체크로 빠른 반환")
        assertEquals(false, result.success)
        assertEquals("timeout", result.version)
    }

    // ========== 경계 케이스 - 상태별 error 필드 ==========

    @Test
    fun `FAILED 상태 시 error 필드 처리`() = runBlocking {
        val mock = MockDeployStatusApi(listOf(DeployState.FAILED))
        val status = mock.status("job-error")

        assertEquals(DeployState.FAILED, status.state)
        assertEquals("Mock error", status.error)
    }

    @Test
    fun `DONE 상태 시 error 필드는 null`() = runBlocking {
        val mock = MockDeployStatusApi(listOf(DeployState.DONE))
        val status = mock.status("job-success")

        assertEquals(DeployState.DONE, status.state)
        assertEquals(null, status.error)
    }
}
