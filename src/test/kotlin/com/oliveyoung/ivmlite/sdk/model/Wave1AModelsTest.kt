package com.oliveyoung.ivmlite.sdk.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Wave1AModelsTest {

    @Test
    fun `CompileMode - Sync 객체 생성`() {
        val mode: CompileMode = CompileMode.Sync
        assertTrue(mode is CompileMode.Sync)
    }

    @Test
    fun `CompileMode - Async 객체 생성`() {
        val mode: CompileMode = CompileMode.Async
        assertTrue(mode is CompileMode.Async)
    }

    @Test
    fun `CompileMode - SyncWithTargets 생성`() {
        val targets = listOf(TargetRef("search-doc", "v1"), TargetRef("reco-feed", "v2"))
        val mode = CompileMode.SyncWithTargets(targets)

        assertTrue(mode is CompileMode.SyncWithTargets)
        assertEquals(2, mode.targets.size)
        assertEquals("search-doc", mode.targets[0].id)
        assertEquals("v1", mode.targets[0].version)
    }

    @Test
    fun `ShipMode enum 값 확인`() {
        assertEquals(2, ShipMode.values().size)
        assertTrue(ShipMode.values().contains(ShipMode.Sync))
        assertTrue(ShipMode.values().contains(ShipMode.Async))
    }

    @Test
    fun `CutoverMode enum 값 확인`() {
        assertEquals(2, CutoverMode.values().size)
        assertTrue(CutoverMode.values().contains(CutoverMode.Ready))
        assertTrue(CutoverMode.values().contains(CutoverMode.Done))
    }

    @Test
    fun `TargetRef 기본 버전은 v1`() {
        val ref = TargetRef("search-doc")
        assertEquals("search-doc", ref.id)
        assertEquals("v1", ref.version)
    }

    @Test
    fun `TargetRef 커스텀 버전 설정`() {
        val ref = TargetRef("reco-feed", "v2")
        assertEquals("reco-feed", ref.id)
        assertEquals("v2", ref.version)
    }

    @Test
    fun `OpenSearchSinkSpec 기본값 확인`() {
        val spec = OpenSearchSinkSpec()
        assertNull(spec.index)
        assertNull(spec.alias)
        assertEquals(1000, spec.batchSize)
    }

    @Test
    fun `OpenSearchSinkSpec 커스텀 설정`() {
        val spec = OpenSearchSinkSpec(
            index = "products",
            alias = "products-alias",
            batchSize = 500
        )
        assertEquals("products", spec.index)
        assertEquals("products-alias", spec.alias)
        assertEquals(500, spec.batchSize)
    }

    @Test
    fun `OpenSearchSinkSpec은 SinkSpec 구현체`() {
        val spec: SinkSpec = OpenSearchSinkSpec(index = "test")
        assertTrue(spec is OpenSearchSinkSpec)
    }

    @Test
    fun `PersonalizeSinkSpec 기본값 확인`() {
        val spec = PersonalizeSinkSpec()
        assertNull(spec.datasetArn)
        assertNull(spec.roleArn)
    }

    @Test
    fun `PersonalizeSinkSpec 커스텀 설정`() {
        val spec = PersonalizeSinkSpec(
            datasetArn = "arn:aws:personalize:us-east-1:123456789012:dataset/test",
            roleArn = "arn:aws:iam::123456789012:role/PersonalizeRole"
        )
        assertEquals("arn:aws:personalize:us-east-1:123456789012:dataset/test", spec.datasetArn)
        assertEquals("arn:aws:iam::123456789012:role/PersonalizeRole", spec.roleArn)
    }

    @Test
    fun `PersonalizeSinkSpec은 SinkSpec 구현체`() {
        val spec: SinkSpec = PersonalizeSinkSpec(datasetArn = "arn:test")
        assertTrue(spec is PersonalizeSinkSpec)
    }

    @Test
    fun `ShipSpec 생성`() {
        val sinks = listOf<SinkSpec>(
            OpenSearchSinkSpec(index = "products"),
            PersonalizeSinkSpec(datasetArn = "arn:test")
        )
        val spec = ShipSpec(ShipMode.Async, sinks)

        assertEquals(ShipMode.Async, spec.mode)
        assertEquals(2, spec.sinks.size)
    }

    @Test
    fun `DeploySpec 기본값 확인`() {
        val spec = DeploySpec()
        assertTrue(spec.compileMode is CompileMode.Sync)
        assertNull(spec.shipSpec)
        assertEquals(CutoverMode.Ready, spec.cutoverMode)
    }

    @Test
    fun `DeploySpec 커스텀 설정`() {
        val shipSpec = ShipSpec(
            ShipMode.Async,
            listOf(OpenSearchSinkSpec(index = "products"))
        )
        val spec = DeploySpec(
            compileMode = CompileMode.Async,
            shipSpec = shipSpec,
            cutoverMode = CutoverMode.Done
        )

        assertTrue(spec.compileMode is CompileMode.Async)
        assertEquals(ShipMode.Async, spec.shipSpec?.mode)
        assertEquals(CutoverMode.Done, spec.cutoverMode)
    }

    @Test
    fun `DeployResult success 팩토리 메서드`() {
        val result = DeployResult.success("product:SKU123", "v1-20250126")

        assertTrue(result.success)
        assertEquals("product:SKU123", result.entityKey)
        assertEquals("v1-20250126", result.version)
        assertNull(result.error)
    }

    @Test
    fun `DeployResult failure 팩토리 메서드`() {
        val result = DeployResult.failure(
            "product:SKU123",
            "v1-20250126",
            "Compilation failed"
        )

        assertFalse(result.success)
        assertEquals("product:SKU123", result.entityKey)
        assertEquals("v1-20250126", result.version)
        assertEquals("Compilation failed", result.error)
    }

    @Test
    fun `DeployResult 직접 생성`() {
        val result = DeployResult(
            success = true,
            entityKey = "product:SKU123",
            version = "v1-20250126",
            error = null
        )

        assertTrue(result.success)
        assertEquals("product:SKU123", result.entityKey)
        assertEquals("v1-20250126", result.version)
        assertNull(result.error)
    }
}
