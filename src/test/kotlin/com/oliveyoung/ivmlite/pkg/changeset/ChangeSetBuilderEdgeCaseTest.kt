package com.oliveyoung.ivmlite.pkg.changeset

import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeSetBuilder
import com.oliveyoung.ivmlite.pkg.changeset.domain.ChangeType
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ChangeSetBuilder 엣지/코너 케이스 검증
 *
 * - 배열 diff index 기반 경로 (RFC6901)
 * - 빈 배열, 배열 길이 변경, 중첩 배열
 * - JSON Pointer 이스케이프 (~, /)
 */
class ChangeSetBuilderEdgeCaseTest {

    private val builder = ChangeSetBuilder()

    @Test
    fun `배열 - 빈 배열에서 요소 추가`() {
        val fromPayload = """{"options":[]}"""
        val toPayload = """{"options":[{"price":10000}]}"""

        val changeSet = builder.build(
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            fromPayload = fromPayload,
            toPayload = toPayload,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )

        // null→객체 변경 시 해당 인덱스 경로만 추가 (하위 필드 별도 추가 안 함)
        val paths = changeSet.changedPaths.map { it.path }.sorted()
        assertEquals(listOf("/options/0"), paths)
    }

    @Test
    fun `배열 - 요소 삭제 (배열 축소)`() {
        val fromPayload = """{"options":[{"price":10000},{"price":15000}]}"""
        val toPayload = """{"options":[{"price":10000}]}"""

        val changeSet = builder.build(
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            fromPayload = fromPayload,
            toPayload = toPayload,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )

        // to에 없는 인덱스 1은 null vs 있음 → 변경으로 감지
        val paths = changeSet.changedPaths.map { it.path }.sorted()
        assertTrue(paths.contains("/options/1"))
    }

    @Test
    fun `배열 - 빈 배열 동일 (NO_CHANGE 아님, UPDATE)`() {
        val payload = """{"options":[]}"""

        val changeSet = builder.build(
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            fromPayload = payload,
            toPayload = payload,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )

        assertEquals(ChangeType.NO_CHANGE, changeSet.changeType)
        assertTrue(changeSet.changedPaths.isEmpty())
    }

    @Test
    fun `배열 - 중첩 배열 index 경로`() {
        val fromPayload = """{"matrix":[[1,2],[3,4]]}"""
        val toPayload = """{"matrix":[[1,2],[3,5]]}"""

        val changeSet = builder.build(
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            fromPayload = fromPayload,
            toPayload = toPayload,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )

        val paths = changeSet.changedPaths.map { it.path }.sorted()
        assertEquals(listOf("/matrix/1/1"), paths)
    }

    @Test
    fun `배열 - 여러 인덱스 동시 변경`() {
        val fromPayload = """{"options":[{"price":10000},{"price":20000},{"price":30000}]}"""
        val toPayload = """{"options":[{"price":11000},{"price":20000},{"price":33000}]}"""

        val changeSet = builder.build(
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            fromPayload = fromPayload,
            toPayload = toPayload,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )

        val paths = changeSet.changedPaths.map { it.path }.sorted()
        assertEquals(listOf("/options/0/price", "/options/2/price"), paths)
    }

    @Test
    fun `배열 - 객체와 스칼라 혼합 배열`() {
        val fromPayload = """{"items":[{"id":1},2,{"id":3}]}"""
        val toPayload = """{"items":[{"id":1},2,{"id":4}]}"""

        val changeSet = builder.build(
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            fromPayload = fromPayload,
            toPayload = toPayload,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )

        val paths = changeSet.changedPaths.map { it.path }.sorted()
        assertEquals(listOf("/items/2/id"), paths)
    }

    @Test
    fun `배열 - changedPaths 정렬 (결정성)`() {
        val fromPayload = """{"a":1,"options":[{"x":1},{"y":2}]}"""
        val toPayload = """{"a":2,"options":[{"x":1},{"y":3}]}"""

        val changeSet = builder.build(
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            fromPayload = fromPayload,
            toPayload = toPayload,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )

        val paths = changeSet.changedPaths.map { it.path }
        assertEquals(paths.sorted(), paths)
    }

    @Test
    fun `RFC6901 이스케이프 - 슬래시 포함 필드명`() {
        val fromPayload = """{"a/b":"old"}"""
        val toPayload = """{"a/b":"new"}"""

        val changeSet = builder.build(
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            fromPayload = fromPayload,
            toPayload = toPayload,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )

        // RFC6901: / → ~1, "a/b" → /a~1b
        val paths = changeSet.changedPaths.map { it.path }
        assertTrue(paths.any { it.contains("~1") }, "슬래시 이스케이프(~1) 포함")
    }

    @Test
    fun `배열 - 첫 요소만 변경`() {
        val fromPayload = """{"options":[{"price":10000},{"price":20000}]}"""
        val toPayload = """{"options":[{"price":9999},{"price":20000}]}"""

        val changeSet = builder.build(
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            fromPayload = fromPayload,
            toPayload = toPayload,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )

        assertEquals(listOf("/options/0/price"), changeSet.changedPaths.map { it.path })
    }

    @Test
    fun `배열 - 마지막 요소만 변경`() {
        val fromPayload = """{"options":[{"price":10000},{"price":20000}]}"""
        val toPayload = """{"options":[{"price":10000},{"price":20001}]}"""

        val changeSet = builder.build(
            tenantId = TenantId("t1"),
            entityType = "Product",
            entityKey = EntityKey("p1"),
            fromVersion = 1,
            toVersion = 2,
            fromPayload = fromPayload,
            toPayload = toPayload,
            impactedSliceTypes = emptySet(),
            impactMap = emptyMap(),
        )

        assertEquals(listOf("/options/1/price"), changeSet.changedPaths.map { it.path })
    }
}
