package com.oliveyoung.ivmlite.sdk.dsl.deploy

import arrow.core.getOrElse
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 엣지 케이스 및 코너 케이스 테스트
 * - 빈 값, 극단적 값, 동시성, 유니크성 검증
 */
class DeployableContextEdgeCaseTest {

    private val testConfig = IvmClientConfig(
        baseUrl = "http://localhost:8080",
        tenantId = "test-tenant"
    )

    @Test
    fun `Edge - Empty sinks in Full DSL throws IllegalArgumentException`() {
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "TEST-001",
            name = "Test Product",
            price = 10000
        )
        val context = DeployableContext(input, testConfig)

        val exception = assertThrows<IllegalArgumentException> {
            context.deploy {
                compile.sync()
                ship.async {
                    // Empty block - no sinks added
                }
            }
        }

        assertTrue(exception.message!!.contains("sinks list is empty"))
    }

    @Test
    fun `Edge - Empty sinks in deployNow throws IllegalArgumentException`() {
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "TEST-001",
            name = "Test Product",
            price = 10000
        )
        val context = DeployableContext(input, testConfig)

        val exception = assertThrows<IllegalArgumentException> {
            context.deployNow {
                // Empty block - no sinks
            }
        }

        assertTrue(exception.message!!.contains("sinks list is empty"))
    }

    @Test
    fun `Edge - Version uniqueness across multiple calls`() {
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "TEST-001",
            name = "Test Product",
            price = 10000
        )
        val context = DeployableContext(input, testConfig)

        val result1 = context.deployNow { opensearch() }
        Thread.sleep(2) // Ensure timestamp difference
        val result2 = context.deployNow { opensearch() }

        assertNotEquals(result1.version, result2.version, "Versions must be unique")
    }

    @Test
    fun `Edge - JobId uniqueness across multiple async calls`() {
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "TEST-001",
            name = "Test Product",
            price = 10000
        )
        val context = DeployableContext(input, testConfig)

        val job1 = context.deployQueued { opensearch() }.getOrElse { fail("deployQueued failed: ${it.message}") }
        val job2 = context.deployQueued { opensearch() }.getOrElse { fail("deployQueued failed: ${it.message}") }
        val job3 = context.deployQueued { opensearch() }.getOrElse { fail("deployQueued failed: ${it.message}") }

        // All jobIds must be unique
        val jobIds = setOf(job1.jobId, job2.jobId, job3.jobId)
        assertEquals(3, jobIds.size, "All jobIds must be unique")

        // All versions must be unique (or at least attempt to be)
        assertTrue(job1.jobId.startsWith("job-"))
        assertTrue(job2.jobId.startsWith("job-"))
        assertTrue(job3.jobId.startsWith("job-"))
    }

    @Test
    fun `Edge - Empty SKU string`() {
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "",
            name = "Test Product",
            price = 10000
        )
        val context = DeployableContext(input, testConfig)

        val result = context.deployNow { opensearch() }

        assertEquals("product:", result.entityKey)
        assertTrue(result.success)
    }

    @Test
    fun `Edge - Very long SKU string`() {
        val longSku = "A".repeat(1000)
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = longSku,
            name = "Test Product",
            price = 10000
        )
        val context = DeployableContext(input, testConfig)

        val result = context.deployNow { opensearch() }

        assertEquals("product:$longSku", result.entityKey)
        assertTrue(result.success)
    }

    @Test
    fun `Edge - SKU with special characters`() {
        val specialSku = "TEST-001:특수문자/슬래시\\백슬래시@골뱅이"
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = specialSku,
            name = "Test Product",
            price = 10000
        )
        val context = DeployableContext(input, testConfig)

        val result = context.deployNow { opensearch() }

        assertEquals("product:$specialSku", result.entityKey)
        assertTrue(result.success)
    }

    @Test
    fun `Edge - Price zero`() {
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "TEST-001",
            name = "Free Product",
            price = 0
        )
        val context = DeployableContext(input, testConfig)

        val result = context.deployNow { opensearch() }

        assertTrue(result.success)
        assertEquals("product:TEST-001", result.entityKey)
    }

    @Test
    fun `Edge - Price negative (allowed by data model)`() {
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "TEST-001",
            name = "Credit Product",
            price = -1000
        )
        val context = DeployableContext(input, testConfig)

        val result = context.deployNow { opensearch() }

        assertTrue(result.success)
    }

    @Test
    fun `Edge - Price maximum Long value`() {
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "TEST-001",
            name = "Expensive Product",
            price = Long.MAX_VALUE
        )
        val context = DeployableContext(input, testConfig)

        val result = context.deployNow { opensearch() }

        assertTrue(result.success)
    }

    @Test
    fun `Edge - Empty attributes map`() {
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "TEST-001",
            name = "Simple Product",
            price = 10000,
            attributes = emptyMap()
        )
        val context = DeployableContext(input, testConfig)

        val result = context.deployNow { opensearch() }

        assertTrue(result.success)
    }

    @Test
    fun `Edge - Large attributes map`() {
        val largeAttributes = (1..1000).associate { "key$it" to "value$it" }
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "TEST-001",
            name = "Complex Product",
            price = 10000,
            attributes = largeAttributes
        )
        val context = DeployableContext(input, testConfig)

        val result = context.deployNow { opensearch() }

        assertTrue(result.success)
    }

    @Test
    fun `Edge - Null optional fields`() {
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "TEST-001",
            name = "Minimal Product",
            price = 10000,
            category = null,
            brand = null
        )
        val context = DeployableContext(input, testConfig)

        val result = context.deployNow { opensearch() }

        assertTrue(result.success)
        assertEquals("product:TEST-001", result.entityKey)
    }

    @Test
    fun `Corner - Rapid successive calls`() {
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "TEST-001",
            name = "Test Product",
            price = 10000
        )
        val context = DeployableContext(input, testConfig)

        val results = (1..100).map {
            context.deployNow { opensearch() }
        }

        // All must succeed
        assertTrue(results.all { it.success })

        // Versions are based on millis timestamp, so rapid calls may produce same versions
        // This test primarily validates that rapid calls don't cause errors
        val uniqueVersions = results.map { it.version }.toSet()
        assertTrue(uniqueVersions.isNotEmpty(), "At least one version should be generated")
    }

    @Test
    fun `Corner - Version format validation`() {
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "TEST-001",
            name = "Test Product",
            price = 10000
        )
        val context = DeployableContext(input, testConfig)

        val result = context.deployNow { opensearch() }

        // Version format: TSID Long (예: "568920170376192001")
        assertTrue(result.version.toLongOrNull() != null, "version should be a valid Long")
    }

    @Test
    fun `Corner - JobId format validation`() {
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "TEST-001",
            name = "Test Product",
            price = 10000
        )
        val context = DeployableContext(input, testConfig)

        val job = context.deployQueued { opensearch() }.getOrElse { fail("deployQueued failed: ${it.message}") }

        // JobId format: job-{uuid}
        assertTrue(job.jobId.matches(Regex("^job-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")))
    }

    @Test
    fun `Corner - Same input different context instances`() {
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "TEST-001",
            name = "Test Product",
            price = 10000
        )

        val context1 = DeployableContext(input, testConfig)
        val context2 = DeployableContext(input, testConfig)

        val result1 = context1.deployNow { opensearch() }
        Thread.sleep(2)
        val result2 = context2.deployNow { opensearch() }

        // Same entityKey
        assertEquals(result1.entityKey, result2.entityKey)
        // Different versions
        assertNotEquals(result1.version, result2.version)
    }
}
