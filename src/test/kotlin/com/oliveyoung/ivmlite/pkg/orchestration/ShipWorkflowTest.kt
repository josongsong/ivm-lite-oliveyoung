package com.oliveyoung.ivmlite.pkg.orchestration
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.orchestration.application.ShipWorkflow
import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkAdapter
import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * ShipWorkflow Tests (RFC-IMPL-011 Wave 6)
 */
class ShipWorkflowTest : StringSpec({

    val tenantId = TenantId("test-tenant")
    val entityKey = EntityKey("PRODUCT#test-tenant#SKU-001")
    val version = 1L
    
    "ShipWorkflow: execute ships slice to sink" {
        val sliceRepo = InMemorySliceRepository()
        val sinkAdapter = InMemorySinkAdapter("opensearch")
        val workflow = ShipWorkflow(sliceRepo, mapOf("opensearch" to sinkAdapter))
        
        // Slice 저장
        val slice = SliceRecord(
            tenantId = tenantId,
            entityKey = entityKey,
            version = version,
            sliceType = SliceType.CORE,
            ruleSetId = "ruleset.v1",
            ruleSetVersion = com.oliveyoung.ivmlite.shared.domain.types.SemVer.parse("1.0.0"),
            data = """{"name": "Test Product", "price": 10000}""",
            hash = "hash123"
        )
        sliceRepo.putAllIdempotent(listOf(slice))
        
        // Ship 실행
        val result = workflow.execute(tenantId, entityKey, version, "opensearch")
        
        result.shouldBeInstanceOf<Result.Ok<ShipWorkflow.ShipResult>>()
        val shipResult = (result as Result.Ok).value
        shipResult.entityKey shouldBe entityKey.value
        shipResult.sinkType shouldBe "opensearch"
        
        // Sink에 저장 확인
        sinkAdapter.getShipCount() shouldBe 1
    }
    
    "ShipWorkflow: execute returns error for unknown sink" {
        val sliceRepo = InMemorySliceRepository()
        val workflow = ShipWorkflow(sliceRepo, emptyMap())
        
        val result = workflow.execute(tenantId, entityKey, version, "unknown")
        
        result.shouldBeInstanceOf<Result.Err>()
    }
    
    "ShipWorkflow: execute returns error for missing slice" {
        val sliceRepo = InMemorySliceRepository()
        val sinkAdapter = InMemorySinkAdapter("opensearch")
        val workflow = ShipWorkflow(sliceRepo, mapOf("opensearch" to sinkAdapter))
        
        // Slice 없이 Ship 시도
        val result = workflow.execute(tenantId, entityKey, version, "opensearch")
        
        result.shouldBeInstanceOf<Result.Err>()
    }
    
    "ShipWorkflow: executeToMultipleSinks ships to multiple sinks" {
        val sliceRepo = InMemorySliceRepository()
        val openSearchSink = InMemorySinkAdapter("opensearch")
        val personalizeSink = InMemorySinkAdapter("personalize")
        val workflow = ShipWorkflow(
            sliceRepo, 
            mapOf("opensearch" to openSearchSink, "personalize" to personalizeSink)
        )
        
        // Slice 저장
        val slice = SliceRecord(
            tenantId = tenantId,
            entityKey = entityKey,
            version = version,
            sliceType = SliceType.CORE,
            ruleSetId = "ruleset.v1",
            ruleSetVersion = com.oliveyoung.ivmlite.shared.domain.types.SemVer.parse("1.0.0"),
            data = """{"name": "Multi-Sink Test"}""",
            hash = "hash456"
        )
        sliceRepo.putAllIdempotent(listOf(slice))
        
        // 여러 Sink로 Ship
        val result = workflow.executeToMultipleSinks(
            tenantId, entityKey, version,
            listOf("opensearch", "personalize")
        )
        
        result.shouldBeInstanceOf<Result.Ok<ShipWorkflow.MultiShipResult>>()
        val multiResult = (result as Result.Ok).value
        multiResult.successCount shouldBe 2
        multiResult.failedCount shouldBe 0
        
        // 두 Sink 모두 저장 확인
        openSearchSink.getShipCount() shouldBe 1
        personalizeSink.getShipCount() shouldBe 1
    }
    
    "ShipWorkflow: executeBatch ships multiple entities" {
        val sliceRepo = InMemorySliceRepository()
        val sinkAdapter = InMemorySinkAdapter("opensearch")
        val workflow = ShipWorkflow(sliceRepo, mapOf("opensearch" to sinkAdapter))
        
        // 여러 Slice 저장
        val entities = (1..3).map { i ->
            val key = EntityKey("PRODUCT#test-tenant#SKU-00$i")
            SliceRecord(
                tenantId = tenantId,
                entityKey = key,
                version = 1L,
                sliceType = SliceType.CORE,
                ruleSetId = "ruleset.v1",
                ruleSetVersion = com.oliveyoung.ivmlite.shared.domain.types.SemVer.parse("1.0.0"),
                data = """{"id": $i}""",
                hash = "hash$i"
            )
        }
        sliceRepo.putAllIdempotent(entities)
        
        // 배치 Ship
        val result = workflow.executeBatch(
            tenantId,
            entities.map { ShipWorkflow.EntityVersion(it.entityKey, it.version) },
            "opensearch"
        )
        
        result.shouldBeInstanceOf<Result.Ok<ShipWorkflow.BatchShipResult>>()
        val batchResult = (result as Result.Ok).value
        batchResult.successCount shouldBe 3
        batchResult.failedCount shouldBe 0
    }
})
