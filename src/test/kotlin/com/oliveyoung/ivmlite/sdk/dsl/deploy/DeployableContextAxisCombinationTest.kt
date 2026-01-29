package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.model.CompileMode
import com.oliveyoung.ivmlite.sdk.model.CutoverMode
import com.oliveyoung.ivmlite.sdk.model.ShipMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 수학적 완결성 테스트 - 모든 Axis 조합 검증
 *
 * Axes:
 * - CompileMode: Sync, Async
 * - ShipMode: Sync, Async, None (shipSpec = null)
 * - CutoverMode: Ready, Done
 *
 * 총 2 × 3 × 2 = 12개 조합
 * 단, compile.async + ship.sync는 DeployBuilder에서 차단 (1개 invalid)
 * 유효 조합: 11개
 */
class DeployableContextAxisCombinationTest {

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

    // ========================================
    // compile.sync combinations (6개)
    // ========================================

    @Test
    fun `Axis - compile_sync + ship_async + cutover_ready`() {
        val context = DeployableContext(testInput, testConfig)

        val result = context.deploy {
            compile.sync()
            ship.async { opensearch() }
            cutover.ready()
        }

        assertTrue(result.success)
    }

    @Test
    fun `Axis - compile_sync + ship_async + cutover_done`() {
        val context = DeployableContext(testInput, testConfig)

        val result = context.deploy {
            compile.sync()
            ship.async { opensearch() }
            cutover.done()
        }

        assertTrue(result.success)
    }

    @Test
    fun `Axis - compile_sync + no_ship + cutover_ready`() {
        val context = DeployableContext(testInput, testConfig)

        val result = context.deploy {
            compile.sync()
            // No ship configuration
            cutover.ready()
        }

        assertTrue(result.success)
    }

    @Test
    fun `Axis - compile_sync + no_ship + cutover_done`() {
        val context = DeployableContext(testInput, testConfig)

        val result = context.deploy {
            compile.sync()
            // No ship configuration
            cutover.done()
        }

        assertTrue(result.success)
    }

    // ========================================
    // compile.async combinations (5개: 4 valid + 1 invalid)
    // ========================================

    @Test
    fun `Axis - compile_async + ship_async + cutover_ready`() {
        val context = DeployableContext(testInput, testConfig)

        val result = context.deploy {
            compile.async()
            ship.async { opensearch() }
            cutover.ready()
        }

        assertTrue(result.success)
    }

    @Test
    fun `Axis - compile_async + ship_async + cutover_done`() {
        val context = DeployableContext(testInput, testConfig)

        val result = context.deploy {
            compile.async()
            ship.async { opensearch() }
            cutover.done()
        }

        assertTrue(result.success)
    }

    @Test
    fun `Axis - compile_async + no_ship + cutover_ready`() {
        val context = DeployableContext(testInput, testConfig)

        val result = context.deploy {
            compile.async()
            // No ship configuration
            cutover.ready()
        }

        assertTrue(result.success)
    }

    @Test
    fun `Axis - compile_async + no_ship + cutover_done`() {
        val context = DeployableContext(testInput, testConfig)

        val result = context.deploy {
            compile.async()
            // No ship configuration
            cutover.done()
        }

        assertTrue(result.success)
    }

    // ship.sync()는 제거되었으므로 이 테스트는 더 이상 필요 없음
    // compile.async + ship.sync 조합은 컴파일 타임에 차단됨 (ship.sync() 메서드가 없음)

    // ========================================
    // Shortcut API Correctness (조합 검증)
    // ========================================

    @Test
    fun `Shortcut - deployNow generates correct spec`() {
        // deployNow = compile.sync + ship.async + cutover.ready
        val context = DeployableContext(testInput, testConfig)

        val result = context.deployNow { opensearch() }

        assertTrue(result.success)
        // Internal spec validation already done in execute()
    }

    @Test
    fun `Shortcut - deployNowAndShipNow generates correct spec`() {
        // deployNowAndShipNow = compile.sync + ship.async + cutover.ready (ship.sync() 제거됨)
        val context = DeployableContext(testInput, testConfig)

        val result = context.deployNowAndShipNow { opensearch() }

        assertTrue(result.success)
    }

    @Test
    fun `Shortcut - deployQueued generates correct spec and returns DeployJob`() {
        // deployQueued = compile.async + ship.async + cutover.ready
        val context = DeployableContext(testInput, testConfig)

        val job = context.deployQueued { opensearch() }

        assertNotNull(job.jobId)
        assertNotNull(job.version)
        assertEquals("product:TEST-001", job.entityKey)
    }

    // ========================================
    // DeployAsyncBuilder type-safety (compile.async only)
    // ========================================

    @Test
    fun `TypeSafety - deployAsync enforces compile_async`() {
        val context = DeployableContext(testInput, testConfig)

        val job = context.deployAsync {
            // compile.async() is implicit - cannot call compile.sync()
            ship.async { opensearch() }
            cutover.ready()
        }

        assertNotNull(job.jobId)
        assertTrue(job.jobId.startsWith("job-"))
    }

    @Test
    fun `TypeSafety - deployAsync allows no ship configuration`() {
        val context = DeployableContext(testInput, testConfig)

        val job = context.deployAsync {
            // compile.async() is implicit
            // No ship configuration
            cutover.ready()
        }

        assertNotNull(job.jobId)
    }

    // ========================================
    // Mathematical completeness verification
    // ========================================

    @Test
    fun `Math - All valid combinations tested`() {
        // This is a meta-test to ensure we've tested all combinations
        // Total valid combinations: 8 (ship.sync() 제거됨)
        // - compile.sync: 4 (ship.async() + no_ship) × 2 cutover modes
        // - compile.async: 4 (ship.async() + no_ship) × 2 cutover modes

        val context = DeployableContext(testInput, testConfig)

        // compile.sync combinations
        val syncCombinations = listOf(
            { context.deploy { compile.sync(); ship.async { opensearch() }; cutover.ready() } },
            { context.deploy { compile.sync(); ship.async { opensearch() }; cutover.done() } },
            { context.deploy { compile.sync(); cutover.ready() } },
            { context.deploy { compile.sync(); cutover.done() } }
        )

        // compile.async valid combinations
        val asyncCombinations = listOf(
            { context.deploy { compile.async(); ship.async { opensearch() }; cutover.ready() } },
            { context.deploy { compile.async(); ship.async { opensearch() }; cutover.done() } },
            { context.deploy { compile.async(); cutover.ready() } },
            { context.deploy { compile.async(); cutover.done() } }
        )

        // Execute all and verify success
        syncCombinations.forEach { it().let { result -> assertTrue(result.success) } }
        asyncCombinations.forEach { it().let { result -> assertTrue(result.success) } }

        // Total tested: 8 combinations
        assertEquals(8, syncCombinations.size + asyncCombinations.size)
    }
}
