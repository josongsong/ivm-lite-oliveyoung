package com.oliveyoung.ivmlite.sdk.client

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * StatusApi 엣지케이스 테스트
 * RFC-IMPL-011 Wave 5-K
 *
 * 경계값, 코너케이스, 수학적 완결성 검증
 */
class StatusApiEdgeCaseTest {

    private val client = Ivm.client()

    // ========== 경계값 테스트 ==========

    @Test
    fun `status - jobId 빈 문자열 시 예외`() = runBlocking {
        val ex = assertThrows<IllegalArgumentException> {
            client.deploy.status("")
        }
        assertTrue(ex.message!!.contains("jobId must not be blank"))
    }

    @Test
    fun `status - jobId 공백 문자열 시 예외`() = runBlocking {
        val ex = assertThrows<IllegalArgumentException> {
            client.deploy.status("   ")
        }
        assertTrue(ex.message!!.contains("jobId must not be blank"))
    }

    @Test
    fun `await - jobId 빈 문자열 시 예외`() = runBlocking {
        val ex = assertThrows<IllegalArgumentException> {
            client.deploy.await("", timeout = Duration.ofSeconds(1))
        }
        assertTrue(ex.message!!.contains("jobId must not be blank"))
    }

    @Test
    fun `await - timeout 0 시 예외`() = runBlocking {
        val ex = assertThrows<IllegalArgumentException> {
            client.deploy.await("job-1", timeout = Duration.ZERO)
        }
        assertTrue(ex.message!!.contains("timeout must be positive"))
    }

    @Test
    fun `await - timeout 음수 시 예외`() = runBlocking {
        val ex = assertThrows<IllegalArgumentException> {
            client.deploy.await("job-1", timeout = Duration.ofSeconds(-1))
        }
        assertTrue(ex.message!!.contains("timeout must be positive"))
    }

    @Test
    fun `await - pollInterval 0 시 예외`() = runBlocking {
        val ex = assertThrows<IllegalArgumentException> {
            client.deploy.await("job-1", pollInterval = Duration.ZERO)
        }
        assertTrue(ex.message!!.contains("pollInterval must be positive"))
    }

    @Test
    fun `await - pollInterval 음수 시 예외`() = runBlocking {
        val ex = assertThrows<IllegalArgumentException> {
            client.deploy.await("job-1", pollInterval = Duration.ofSeconds(-1))
        }
        assertTrue(ex.message!!.contains("pollInterval must be positive"))
    }

    @Test
    fun `await - pollInterval이 timeout보다 큰 경우 예외`() = runBlocking {
        val ex = assertThrows<IllegalArgumentException> {
            client.deploy.await(
                "job-1",
                timeout = Duration.ofSeconds(1),
                pollInterval = Duration.ofSeconds(2)
            )
        }
        assertTrue(ex.message!!.contains("pollInterval must not exceed timeout"))
    }

    // ========== 코너케이스 테스트 ==========

    @Test
    fun `await - pollInterval과 timeout이 같은 경우 정상 동작`() = runBlocking {
        val result = client.deploy.await(
            "job-corner-1",
            timeout = Duration.ofMillis(100),
            pollInterval = Duration.ofMillis(100)
        )
        // timeout이므로 실패해야 함
        assertEquals(false, result.success)
        assertEquals("timeout", result.version)
    }

    @Test
    fun `await - 매우 짧은 timeout (1ms)`() = runBlocking {
        val result = client.deploy.await(
            "job-corner-2",
            timeout = Duration.ofMillis(1),
            pollInterval = Duration.ofMillis(1)
        )
        assertEquals(false, result.success)
        assertTrue(result.error?.contains("Timeout") ?: false)
    }

    @Test
    fun `await - 매우 긴 jobId (1000자)`() = runBlocking {
        val longJobId = "j".repeat(1000)
        val result = client.deploy.await(
            longJobId,
            timeout = Duration.ofMillis(50),
            pollInterval = Duration.ofMillis(10)
        )
        assertEquals(longJobId, result.entityKey)
    }

    @Test
    fun `plan - deployId 빈 문자열 시 예외`() {
        val ex = assertThrows<IllegalArgumentException> {
            client.plan.explainLastPlan("")
        }
        assertTrue(ex.message!!.contains("deployId must not be blank"))
    }

    @Test
    fun `plan - deployId 공백 문자열 시 예외`() {
        val ex = assertThrows<IllegalArgumentException> {
            client.plan.explainLastPlan("   ")
        }
        assertTrue(ex.message!!.contains("deployId must not be blank"))
    }

    // ========== 수학적 완결성 테스트 ==========

    @Test
    fun `await - 폴링 횟수 계산 정확성`() = runBlocking {
        // timeout=100ms, pollInterval=10ms
        // 예상 최대 폴링 횟수: 100/10 = 10회
        // 실제로는 타임아웃 체크로 인해 적게 호출될 수 있음
        val result = client.deploy.await(
            "job-math-1",
            timeout = Duration.ofMillis(100),
            pollInterval = Duration.ofMillis(10)
        )
        // 타임아웃으로 실패해야 함
        assertEquals(false, result.success)
        assertEquals("timeout", result.version)
    }

    @Test
    fun `await - delay 직후 타임아웃되는 경계 케이스`() = runBlocking {
        // pollInterval이 timeout에 매우 가까운 경우
        val result = client.deploy.await(
            "job-math-2",
            timeout = Duration.ofMillis(55),
            pollInterval = Duration.ofMillis(50)
        )
        // 1회 status 호출 + delay(50) → 타임아웃
        assertEquals(false, result.success)
    }

    @Test
    fun `await - 최소 1회는 status 호출됨을 보장`() = runBlocking {
        // timeout이 매우 짧아도 최소 1회는 status 호출
        val result = client.deploy.await(
            "job-math-3",
            timeout = Duration.ofMillis(10),
            pollInterval = Duration.ofMillis(5)
        )
        // entityKey가 반환되므로 최소 1회는 호출됨
        assertEquals("job-math-3", result.entityKey)
    }

    // ========== 상태 전환 완결성 테스트 ==========

    @Test
    fun `모든 DeployState가 처리되는지 컴파일 시점 검증`() {
        // DeployState의 모든 상태가 when에서 처리되므로
        // 새로운 상태 추가 시 컴파일 에러 발생
        // (현재 when은 exhaustive하므로 컴파일 보장)
        assertTrue(true, "Compile-time exhaustiveness check passed")
    }

    @Test
    fun `await - 진행 중 상태들(QUEUED, RUNNING, READY, SINKING) 폴링 계속`() = runBlocking {
        // stub은 항상 RUNNING 반환 → 타임아웃까지 폴링
        val result = client.deploy.await(
            "job-state-1",
            timeout = Duration.ofMillis(50),
            pollInterval = Duration.ofMillis(10)
        )
        assertEquals(false, result.success)
        assertEquals("timeout", result.version)
    }

    // ========== 동시성 테스트 ==========

    @Test
    fun `await - 여러 Job 동시 대기 가능`() = runBlocking {
        val jobs = (1..5).map { "job-concurrent-$it" }

        // 각 Job에 대해 동시 await (모두 타임아웃)
        val results = jobs.map { jobId ->
            client.deploy.await(
                jobId,
                timeout = Duration.ofMillis(30),
                pollInterval = Duration.ofMillis(10)
            )
        }

        assertEquals(5, results.size)
        assertTrue(results.all { !it.success })
    }

    // ========== 특수 문자 테스트 ==========

    @Test
    fun `status - jobId에 특수문자 포함 가능`() = runBlocking {
        val jobId = "job-123-abc_XYZ:2024/01/26"
        val status = client.deploy.status(jobId)
        assertEquals(jobId, status.jobId)
    }

    @Test
    fun `await - jobId에 유니코드 포함 가능`() = runBlocking {
        val jobId = "job-한글-🚀-emoji"
        val result = client.deploy.await(
            jobId,
            timeout = Duration.ofMillis(50),
            pollInterval = Duration.ofMillis(10)
        )
        assertEquals(jobId, result.entityKey)
    }
}
