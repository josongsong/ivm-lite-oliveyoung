package com.oliveyoung.ivmlite.sdk.dsl.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * BrandBuilder 테스트
 * RFC-IMPL-011 Wave 5-L (Entity Type 확장)
 */
class BrandBuilderTest : StringSpec({

    "BrandBuilder - 필수 필드 누락 시 IllegalArgumentException (tenantId)" {
        shouldThrow<IllegalArgumentException> {
            BrandBuilder().apply {
                brandId("BRAND-001")
                name("올리브영")
            }.build()
        }.message shouldBe "tenantId is required"
    }

    "BrandBuilder - 필수 필드 누락 시 IllegalArgumentException (brandId)" {
        shouldThrow<IllegalArgumentException> {
            BrandBuilder().apply {
                tenantId("tenant-1")
                name("올리브영")
            }.build()
        }.message shouldBe "brandId is required"
    }

    "BrandBuilder - 필수 필드 누락 시 IllegalArgumentException (name)" {
        shouldThrow<IllegalArgumentException> {
            BrandBuilder().apply {
                tenantId("tenant-1")
                brandId("BRAND-001")
            }.build()
        }.message shouldBe "name is required"
    }

    "BrandBuilder - 정상 빌드 (필수 필드만)" {
        val input = BrandBuilder().apply {
            tenantId("tenant-1")
            brandId("BRAND-001")
            name("올리브영")
        }.build()

        input.tenantId shouldBe "tenant-1"
        input.brandId shouldBe "BRAND-001"
        input.name shouldBe "올리브영"
        input.logoUrl shouldBe null
        input.description shouldBe null
        input.country shouldBe null
        input.attributes shouldBe emptyMap()
        input.entityType shouldBe "brand"
    }

    "BrandBuilder - 정상 빌드 (모든 필드)" {
        val input = BrandBuilder().apply {
            tenantId("tenant-1")
            brandId("BRAND-002")
            name("이니스프리")
            logoUrl("https://cdn.example.com/innisfree.png")
            description("자연주의 화장품 브랜드")
            country("KR")
            attribute("founded", 2000)
            attribute("premium", true)
        }.build()

        input.tenantId shouldBe "tenant-1"
        input.brandId shouldBe "BRAND-002"
        input.name shouldBe "이니스프리"
        input.logoUrl shouldBe "https://cdn.example.com/innisfree.png"
        input.description shouldBe "자연주의 화장품 브랜드"
        input.country shouldBe "KR"
        input.attributes shouldBe mapOf("founded" to 2000, "premium" to true)
    }

    "BrandInput - EntityInput 인터페이스 구현" {
        val input = BrandInput(
            tenantId = "t1",
            brandId = "BRAND-001",
            name = "Brand"
        )

        input.shouldBeInstanceOf<EntityInput>()
        input.tenantId shouldBe "t1"
        input.entityType shouldBe "brand"
    }

})
