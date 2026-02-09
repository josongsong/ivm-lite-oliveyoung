package com.oliveyoung.ivmlite.pkg.sinks
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkAdapter
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPort
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Sink Adapter Tests (RFC-IMPL-011 Wave 6)
 */
class SinkAdapterTest : StringSpec({

    val tenantId = TenantId("test-tenant")
    val entityKey = EntityKey("PRODUCT#test-tenant#SKU-001")
    val payload = """{"name": "Test Product", "price": 10000}"""
    
    "InMemorySinkAdapter: ship stores data correctly" {
        val sink = InMemorySinkAdapter("test-sink")
        
        val result = sink.ship(tenantId, entityKey, 1L, payload)
        
        result shouldBe Result.Ok(SinkPort.ShipResult(
            entityKey = entityKey.value,
            version = 1L,
            sinkId = "test-sink/${tenantId.value}/${entityKey.value}",
            latencyMs = 1L
        ))
        
        // 저장된 데이터 확인
        val stored = sink.get(tenantId.value, entityKey.value)
        stored shouldNotBe null
        stored!!.payload shouldBe payload
        stored.version shouldBe 1L
    }
    
    "InMemorySinkAdapter: shipBatch stores multiple items" {
        val sink = InMemorySinkAdapter("batch-sink")
        
        val items = listOf(
            SinkPort.ShipItem(EntityKey("PRODUCT#test-tenant#SKU-001"), 1L, """{"id": 1}"""),
            SinkPort.ShipItem(EntityKey("PRODUCT#test-tenant#SKU-002"), 1L, """{"id": 2}"""),
            SinkPort.ShipItem(EntityKey("PRODUCT#test-tenant#SKU-003"), 1L, """{"id": 3}"""),
        )
        
        val result = sink.shipBatch(tenantId, items)
        
        result shouldBe Result.Ok(SinkPort.BatchShipResult(
            successCount = 3,
            failedCount = 0,
            failedKeys = emptyList(),
            totalLatencyMs = 3L
        ))
        
        // 모든 아이템 저장 확인
        sink.getAllByTenant(tenantId.value).size shouldBe 3
    }
    
    "InMemorySinkAdapter: delete removes data" {
        val sink = InMemorySinkAdapter("delete-sink")
        
        // 먼저 저장
        sink.ship(tenantId, entityKey, 1L, payload)
        sink.get(tenantId.value, entityKey.value) shouldNotBe null
        
        // 삭제
        val result = sink.delete(tenantId, entityKey)
        result shouldBe Result.Ok(Unit)
        
        // 삭제 확인
        sink.get(tenantId.value, entityKey.value) shouldBe null
    }
    
    "InMemorySinkAdapter: healthCheck returns true" {
        val sink = InMemorySinkAdapter()
        sink.healthCheck() shouldBe true
    }
    
    "InMemorySinkAdapter: getShipHistory tracks all ships" {
        val sink = InMemorySinkAdapter()
        
        sink.ship(tenantId, EntityKey("key1"), 1L, "{}")
        sink.ship(tenantId, EntityKey("key2"), 2L, "{}")
        sink.ship(tenantId, EntityKey("key3"), 3L, "{}")
        
        sink.getShipCount() shouldBe 3
        sink.getShipHistory().size shouldBe 3
    }
    
    "InMemorySinkAdapter: clear resets all data" {
        val sink = InMemorySinkAdapter()
        
        sink.ship(tenantId, entityKey, 1L, payload)
        sink.getShipCount() shouldBe 1
        
        sink.clear()
        
        sink.getShipCount() shouldBe 0
        sink.get(tenantId.value, entityKey.value) shouldBe null
    }
})
