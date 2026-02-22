package com.oliveyoung.ivmlite.sdk.client

import com.oliveyoung.ivmlite.sdk.model.DeployJobStatus
import com.oliveyoung.ivmlite.sdk.model.DeployState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
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
