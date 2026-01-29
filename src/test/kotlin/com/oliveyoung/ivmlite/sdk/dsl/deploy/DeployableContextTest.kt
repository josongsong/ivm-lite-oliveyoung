package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.model.CompileMode
import com.oliveyoung.ivmlite.sdk.model.CutoverMode
import com.oliveyoung.ivmlite.sdk.model.DeployState
import com.oliveyoung.ivmlite.sdk.model.ShipMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeployableContextTest {

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
    fun `deploy - Full DSL with sync compile and async ship`() {
        val context = DeployableContext(testInput, testConfig)

        val result = context.deploy {
            compile.sync()
            ship.async {
                opensearch {
                    index("products")
                }
            }
            cutover.ready()
        }

        assertTrue(result.success)
        assertEquals("product:TEST-001", result.entityKey)
        assertNotNull(result.version)
        // TSID 기반 version은 Long 문자열 (예: "568920170376192001")
        assertTrue(result.version.toLongOrNull() != null, "version should be a valid Long: ${result.version}")
    }

    @Test
    fun `deploy - Full DSL with async compile and async ship`() {
        val context = DeployableContext(testInput, testConfig)

        val result = context.deploy {
            compile.async()
            ship.async {
                opensearch {
                    index("products")
                }
            }
            cutover.ready()
        }

        assertTrue(result.success)
        assertEquals("product:TEST-001", result.entityKey)
        assertNotNull(result.version)
    }

    @Test
    fun `deployAsync - Type-safe async DSL`() {
        val context = DeployableContext(testInput, testConfig)

        val job = context.deployAsync {
            compile.async()
            ship.async {
                opensearch {
                    index("products")
                }
            }
            cutover.ready()
        }

        assertEquals("product:TEST-001", job.entityKey)
        assertNotNull(job.version)
        assertNotNull(job.jobId)
        assertTrue(job.jobId.startsWith("job-"))
        assertEquals(DeployState.QUEUED, job.state)
    }

    @Test
    fun `deployAsync - Multiple sinks`() {
        val context = DeployableContext(testInput, testConfig)

        val job = context.deployAsync {
            compile.async()
            ship.async {
                opensearch {
                    index("products")
                }
                personalize {
                    dataset("product-interactions")
                }
            }
            cutover.ready()
        }

        assertEquals("product:TEST-001", job.entityKey)
        assertEquals(DeployState.QUEUED, job.state)
    }
}
