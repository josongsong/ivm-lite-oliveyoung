package com.oliveyoung.ivmlite.sdk.client

import com.oliveyoung.ivmlite.sdk.model.DeployState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * StatusApi 테스트
 * RFC-IMPL-011 Wave 5-K
 */
class StatusApiTest {

    @Test
    fun `deploy status 호출 가능`() = runBlocking {
        val client = Ivm.client()
        val status = client.deploy.status("job-123")

        assertNotNull(status)
        assertEquals("job-123", status.jobId)
        assertNotNull(status.state)
        assertNotNull(status.createdAt)
        assertNotNull(status.updatedAt)
    }

    @Test
    fun `deploy await 호출 가능`() = runBlocking {
        val client = Ivm.client()
        val result = client.deploy.await(
            jobId = "job-456",
            timeout = Duration.ofMillis(100),
            pollInterval = Duration.ofMillis(20)
        )

        assertNotNull(result)
        assertEquals("job-456", result.entityKey)
    }

    @Test
    fun `await 타임아웃 테스트`() = runBlocking {
        val client = Ivm.client()
        val result = client.deploy.await(
            jobId = "job-timeout",
            timeout = Duration.ofMillis(50),
            pollInterval = Duration.ofMillis(10)
        )

        assertNotNull(result)
        assertEquals(false, result.success)
        assertEquals("timeout", result.version)
        assertTrue(result.error?.contains("Timeout") ?: false)
    }

    @Test
    fun `plan explainLastPlan 호출 가능`() {
        val client = Ivm.client()
        val plan = client.plan.explainLastPlan("deploy-123")

        assertNotNull(plan)
        assertEquals("deploy-123", plan.deployId)
        assertNotNull(plan.graph)
        assertNotNull(plan.activatedRules)
        assertTrue(plan.activatedRules.contains("product-to-search-doc"))
        assertTrue(plan.activatedRules.contains("product-to-reco-feed"))
    }

    @Test
    fun `deploy와 plan API 동시 접근 가능`() = runBlocking {
        val client = Ivm.client()

        val status = client.deploy.status("job-789")
        val plan = client.plan.explainLastPlan("deploy-789")

        assertNotNull(status)
        assertNotNull(plan)
    }
}
