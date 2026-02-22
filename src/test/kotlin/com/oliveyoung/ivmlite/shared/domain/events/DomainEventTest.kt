package com.oliveyoung.ivmlite.shared.domain.events

import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

/**
 * DomainEvent / ViewsComposedEvent / ViewKey / SliceKey 테스트
 *
 * 커버리지:
 * - DomainEvent sealed interface 구현 확인
 * - ViewsComposedEvent 기본값 (eventId, occurredAt, aggregateType)
 * - ViewKey / SliceKey data class equality, copy
 * - 필드 접근 및 도메인 규칙 검증
 */
class DomainEventTest : DescribeSpec({

    describe("ViewsComposedEvent") {

        it("기본값 자동 생성 (eventId, occurredAt, aggregateType)") {
            val event = ViewsComposedEvent(
                aggregateId = "product:SKU-001",
                tenantId = TenantId("tenant-1"),
                entityKey = EntityKey("product:SKU-001"),
                version = 1L,
                viewKeys = emptyList(),
                sliceKeys = emptyList()
            )

            event.eventId.shouldNotBeBlank()
            event.occurredAt shouldNotBe null
            event.aggregateType shouldBe AggregateType.VIEW
        }

        it("DomainEvent 인터페이스 구현") {
            val event = ViewsComposedEvent(
                aggregateId = "product:SKU-001",
                tenantId = TenantId("tenant-1"),
                entityKey = EntityKey("product:SKU-001"),
                version = 1L,
                viewKeys = emptyList(),
                sliceKeys = emptyList()
            )

            event.shouldBeInstanceOf<DomainEvent>()
            event.aggregateType shouldBe AggregateType.VIEW
            event.aggregateId shouldBe "product:SKU-001"
        }

        it("커스텀 eventId, occurredAt 설정") {
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val event = ViewsComposedEvent(
                eventId = "custom-event-id",
                occurredAt = now,
                aggregateId = "product:SKU-001",
                tenantId = TenantId("tenant-1"),
                entityKey = EntityKey("product:SKU-001"),
                version = 1L,
                viewKeys = emptyList(),
                sliceKeys = emptyList()
            )

            event.eventId shouldBe "custom-event-id"
            event.occurredAt shouldBe now
        }

        it("viewKeys와 sliceKeys 포함") {
            val viewKeys = listOf(
                ViewKey("tenant-1", "product:SKU-001", 1L, "PRODUCT_DETAIL"),
                ViewKey("tenant-1", "product:SKU-001", 1L, "PRODUCT_SEARCH")
            )
            val sliceKeys = listOf(
                SliceKey("tenant-1", "product:SKU-001", 1L, "CORE"),
                SliceKey("tenant-1", "product:SKU-001", 1L, "PRICE")
            )

            val event = ViewsComposedEvent(
                aggregateId = "product:SKU-001",
                tenantId = TenantId("tenant-1"),
                entityKey = EntityKey("product:SKU-001"),
                version = 1L,
                viewKeys = viewKeys,
                sliceKeys = sliceKeys
            )

            event.viewKeys.size shouldBe 2
            event.sliceKeys.size shouldBe 2
            event.viewKeys[0].viewType shouldBe "PRODUCT_DETAIL"
            event.sliceKeys[1].sliceType shouldBe "PRICE"
        }

        it("data class equality") {
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val event1 = ViewsComposedEvent(
                eventId = "same-id",
                occurredAt = now,
                aggregateId = "product:SKU-001",
                tenantId = TenantId("tenant-1"),
                entityKey = EntityKey("product:SKU-001"),
                version = 1L,
                viewKeys = emptyList(),
                sliceKeys = emptyList()
            )
            val event2 = event1.copy()

            event1 shouldBe event2
        }
    }

    describe("ViewKey") {

        it("data class 필드 접근") {
            val key = ViewKey(
                tenantId = "tenant-1",
                entityKey = "product:SKU-001",
                version = 1L,
                viewType = "PRODUCT_DETAIL"
            )

            key.tenantId shouldBe "tenant-1"
            key.entityKey shouldBe "product:SKU-001"
            key.version shouldBe 1L
            key.viewType shouldBe "PRODUCT_DETAIL"
        }

        it("equality") {
            val key1 = ViewKey("t", "e", 1L, "v")
            val key2 = ViewKey("t", "e", 1L, "v")
            key1 shouldBe key2
        }

        it("inequality - viewType 다름") {
            val key1 = ViewKey("t", "e", 1L, "DETAIL")
            val key2 = ViewKey("t", "e", 1L, "SEARCH")
            key1 shouldNotBe key2
        }
    }

    describe("SliceKey") {

        it("data class 필드 접근") {
            val key = SliceKey(
                tenantId = "tenant-1",
                entityKey = "product:SKU-001",
                version = 1L,
                sliceType = "CORE"
            )

            key.tenantId shouldBe "tenant-1"
            key.sliceType shouldBe "CORE"
        }

        it("equality") {
            val key1 = SliceKey("t", "e", 1L, "CORE")
            val key2 = SliceKey("t", "e", 1L, "CORE")
            key1 shouldBe key2
        }
    }
})
