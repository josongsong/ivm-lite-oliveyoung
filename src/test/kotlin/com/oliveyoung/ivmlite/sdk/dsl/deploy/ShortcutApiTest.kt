package com.oliveyoung.ivmlite.sdk.dsl.deploy

import arrow.core.getOrElse
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.model.DeployState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * RFC-008 Section 11 Shortcut APIs 테스트
 * - deployNow(): compile.sync + ship.async + cutover.ready
 * - deployNowAndShipNow(): compile.sync + ship.sync + cutover.ready
 * - deployQueued(): compile.async + ship.async + cutover.ready
 */
class ShortcutApiTest {

    private val testInput = ProductInput(
        tenantId = "test-tenant",
        sku = "TEST-001",
        name = "Test Product",
        price = 10000
    )

    private val testConfig = IvmClientConfig(
        baseUrl = "http://localhost:8080",
        tenantId = "test-tenant"
    )

    @Test
    fun `deployNow - compile_sync + ship_async + cutover_ready`() {
        val context = DeployableContext(testInput, testConfig)

        val result = context.deployNow {
            opensearch {
                index("products")
            }
        }

        assertTrue(result.success)
        assertEquals("product:TEST-001", result.entityKey)
        assertNotNull(result.version)
        // TSID 기반 version은 Long 문자열
        assertTrue(result.version.toLongOrNull() != null, "version should be a valid Long")
    }

    @Test
    fun `deployNow - Multiple sinks`() {
        val context = DeployableContext(testInput, testConfig)

        val result = context.deployNow {
            opensearch {
                index("products")
            }
            personalize {
                dataset("product-interactions")
            }
        }

        assertTrue(result.success)
        assertEquals("product:TEST-001", result.entityKey)
        assertNotNull(result.version)
    }

    @Test
    fun `deployNowAndShipNow - compile_sync + ship_sync + cutover_ready`() {
        val context = DeployableContext(testInput, testConfig)

        val result = context.deployNowAndShipNow {
            opensearch {
                index("products")
            }
        }

        assertTrue(result.success)
        assertEquals("product:TEST-001", result.entityKey)
        assertNotNull(result.version)
        // TSID 기반 version은 Long 문자열
        assertTrue(result.version.toLongOrNull() != null, "version should be a valid Long")
    }

    @Test
    fun `deployNowAndShipNow - Multiple sinks`() {
        val context = DeployableContext(testInput, testConfig)

        val result = context.deployNowAndShipNow {
            opensearch {
                index("products")
            }
            personalize {
                dataset("product-interactions")
            }
        }

        assertTrue(result.success)
        assertEquals("product:TEST-001", result.entityKey)
        assertNotNull(result.version)
    }

    @Test
    fun `deployQueued - compile_async + ship_async + cutover_ready + DeployJob 반환`() {
        val context = DeployableContext(testInput, testConfig)

        val job = context.deployQueued {
            opensearch {
                index("products")
            }
        }.getOrElse { fail("deployQueued failed: ${it.message}") }

        assertEquals("product:TEST-001", job.entityKey)
        assertNotNull(job.version)
        // TSID 기반 version은 Long 문자열
        assertTrue(job.version.toLongOrNull() != null, "version should be a valid Long")
        assertNotNull(job.jobId)
        assertTrue(job.jobId.startsWith("job-"))
        assertEquals(DeployState.QUEUED, job.state)
    }

    @Test
    fun `deployQueued - Multiple sinks`() {
        val context = DeployableContext(testInput, testConfig)

        val job = context.deployQueued {
            opensearch {
                index("products")
            }
            personalize {
                dataset("product-interactions")
            }
        }.getOrElse { fail("deployQueued failed: ${it.message}") }

        assertEquals("product:TEST-001", job.entityKey)
        assertNotNull(job.version)
        assertNotNull(job.jobId)
        assertEquals(DeployState.QUEUED, job.state)
    }

    @Test
    fun `Shortcut API comparison - deployNow vs deployNowAndShipNow vs deployQueued`() {
        val context = DeployableContext(testInput, testConfig)

        // deployNow: compile.sync + ship.async (가장 일반적)
        val nowResult = context.deployNow {
            opensearch { index("products") }
        }
        assertTrue(nowResult.success)

        // deployNowAndShipNow: compile.sync + ship.sync (동기 전송)
        val nowAndShipResult = context.deployNowAndShipNow {
            opensearch { index("products") }
        }
        assertTrue(nowAndShipResult.success)

        // deployQueued: compile.async + ship.async (비동기 큐 방식)
        val queuedJob = context.deployQueued {
            opensearch { index("products") }
        }.getOrElse { fail("deployQueued failed: ${it.message}") }
        assertEquals(DeployState.QUEUED, queuedJob.state)
    }
}
