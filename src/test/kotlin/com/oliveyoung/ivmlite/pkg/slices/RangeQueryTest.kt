package com.oliveyoung.ivmlite.pkg.slices

import com.oliveyoung.ivmlite.pkg.slices.adapters.InMemorySliceRepository
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.ports.SliceRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Range Query Tests (RFC-IMPL-011 Wave 6)
 */
class RangeQueryTest : StringSpec({

    val tenantId = TenantId("test-tenant")
    
    fun createSlice(entityKey: String, version: Long, sliceType: SliceType = SliceType.CORE) = SliceRecord(
        tenantId = tenantId,
        entityKey = EntityKey(entityKey),
        version = version,
        sliceType = sliceType,
        ruleSetId = "ruleset.v1",
        ruleSetVersion = com.oliveyoung.ivmlite.shared.domain.types.SemVer.parse("1.0.0"),
        data = """{"key": "$entityKey", "version": $version}""",
        hash = "hash-$entityKey-$version"
    )
    
    "findByKeyPrefix: returns slices matching prefix" {
        val repo = InMemorySliceRepository()
        
        // 다양한 키로 Slice 저장
        repo.putAllIdempotent(listOf(
            createSlice("PRODUCT#test-tenant#SKU-001", 1),
            createSlice("PRODUCT#test-tenant#SKU-002", 1),
            createSlice("PRODUCT#test-tenant#SKU-003", 1),
            createSlice("BRAND#test-tenant#BRAND-001", 1),
        ))
        
        // PRODUCT 프리픽스로 조회
        val result = repo.findByKeyPrefix(tenantId, "PRODUCT#", null, 100, null)
        
        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<SliceRepositoryPort.RangeQueryResult>>()
        val queryResult = (result as SliceRepositoryPort.Result.Ok).value
        queryResult.items.size shouldBe 3
        queryResult.hasMore shouldBe false
    }
    
    "findByKeyPrefix: respects limit" {
        val repo = InMemorySliceRepository()
        
        repo.putAllIdempotent((1..10).map { i ->
            createSlice("PRODUCT#test-tenant#SKU-${i.toString().padStart(3, '0')}", 1)
        })
        
        val result = repo.findByKeyPrefix(tenantId, "PRODUCT#", null, 3, null)
        
        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<SliceRepositoryPort.RangeQueryResult>>()
        val queryResult = (result as SliceRepositoryPort.Result.Ok).value
        queryResult.items.size shouldBe 3
        queryResult.hasMore shouldBe true
        queryResult.nextCursor shouldBe "PRODUCT#test-tenant#SKU-003|1"
    }
    
    "findByKeyPrefix: pagination with cursor" {
        val repo = InMemorySliceRepository()
        
        repo.putAllIdempotent((1..5).map { i ->
            createSlice("PRODUCT#test-tenant#SKU-${i.toString().padStart(3, '0')}", 1)
        })
        
        // 첫 페이지
        val page1 = repo.findByKeyPrefix(tenantId, "PRODUCT#", null, 2, null)
        page1.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<SliceRepositoryPort.RangeQueryResult>>()
        val result1 = (page1 as SliceRepositoryPort.Result.Ok).value
        result1.items.size shouldBe 2
        result1.hasMore shouldBe true
        
        // 두 번째 페이지
        val page2 = repo.findByKeyPrefix(tenantId, "PRODUCT#", null, 2, result1.nextCursor)
        page2.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<SliceRepositoryPort.RangeQueryResult>>()
        val result2 = (page2 as SliceRepositoryPort.Result.Ok).value
        result2.items.size shouldBe 2
        result2.hasMore shouldBe true
        
        // 세 번째 페이지 (마지막)
        val page3 = repo.findByKeyPrefix(tenantId, "PRODUCT#", null, 2, result2.nextCursor)
        page3.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<SliceRepositoryPort.RangeQueryResult>>()
        val result3 = (page3 as SliceRepositoryPort.Result.Ok).value
        result3.items.size shouldBe 1
        result3.hasMore shouldBe false
    }
    
    "findByKeyPrefix: filters by sliceType" {
        val repo = InMemorySliceRepository()
        
        repo.putAllIdempotent(listOf(
            createSlice("PRODUCT#test-tenant#SKU-001", 1, SliceType.CORE),
            createSlice("PRODUCT#test-tenant#SKU-001", 1, SliceType.PRICE),
            createSlice("PRODUCT#test-tenant#SKU-002", 1, SliceType.CORE),
        ))
        
        val result = repo.findByKeyPrefix(tenantId, "PRODUCT#", SliceType.CORE, 100, null)
        
        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<SliceRepositoryPort.RangeQueryResult>>()
        val queryResult = (result as SliceRepositoryPort.Result.Ok).value
        queryResult.items.size shouldBe 2
        queryResult.items.all { it.sliceType == SliceType.CORE } shouldBe true
    }
    
    "count: returns correct count" {
        val repo = InMemorySliceRepository()
        
        repo.putAllIdempotent(listOf(
            createSlice("PRODUCT#test-tenant#SKU-001", 1),
            createSlice("PRODUCT#test-tenant#SKU-002", 1),
            createSlice("BRAND#test-tenant#BRAND-001", 1),
        ))
        
        // 전체 카운트
        val totalResult = repo.count(tenantId, null, null)
        totalResult.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<Long>>()
        (totalResult as SliceRepositoryPort.Result.Ok).value shouldBe 3
        
        // 프리픽스 필터 카운트
        val productResult = repo.count(tenantId, "PRODUCT#", null)
        productResult.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<Long>>()
        (productResult as SliceRepositoryPort.Result.Ok).value shouldBe 2
    }
    
    "getLatestVersion: returns latest version slices" {
        val repo = InMemorySliceRepository()
        
        val entityKey = EntityKey("PRODUCT#test-tenant#SKU-001")
        
        // 여러 버전 저장
        val semVer = com.oliveyoung.ivmlite.shared.domain.types.SemVer.parse("1.0.0")
        repo.putAllIdempotent(listOf(
            SliceRecord(tenantId, entityKey, 1, SliceType.CORE, """{"v":1}""", "h1", "rs", semVer),
            SliceRecord(tenantId, entityKey, 2, SliceType.CORE, """{"v":2}""", "h2", "rs", semVer),
            SliceRecord(tenantId, entityKey, 3, SliceType.CORE, """{"v":3}""", "h3", "rs", semVer),
        ))
        
        val result = repo.getLatestVersion(tenantId, entityKey, null)
        
        result.shouldBeInstanceOf<SliceRepositoryPort.Result.Ok<List<SliceRecord>>>()
        val slices = (result as SliceRepositoryPort.Result.Ok).value
        slices.size shouldBe 1
        slices[0].version shouldBe 3
    }
})
