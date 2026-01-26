package com.oliveyoung.ivmlite.sdk.dsl.entity

import com.oliveyoung.ivmlite.sdk.client.Ivm
import com.oliveyoung.ivmlite.sdk.dsl.deploy.DeployableContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * ProductBuilder 테스트
 * RFC-IMPL-011 Wave 2-D
 */
class ProductBuilderTest : StringSpec({

    "ProductBuilder - 필수 필드 누락 시 IllegalArgumentException (tenantId)" {
        shouldThrow<IllegalArgumentException> {
            ProductBuilder().apply {
                sku("SKU-001")
                name("Test Product")
                price(10000)
            }.build()
        }
    }

    "ProductBuilder - 필수 필드 누락 시 IllegalArgumentException (sku)" {
        shouldThrow<IllegalArgumentException> {
            ProductBuilder().apply {
                tenantId("tenant-1")
                name("Test Product")
                price(10000)
            }.build()
        }
    }

    "ProductBuilder - 필수 필드 누락 시 IllegalArgumentException (name)" {
        shouldThrow<IllegalArgumentException> {
            ProductBuilder().apply {
                tenantId("tenant-1")
                sku("SKU-001")
                price(10000)
            }.build()
        }
    }

    "ProductBuilder - 필수 필드 누락 시 IllegalArgumentException (price)" {
        shouldThrow<IllegalArgumentException> {
            ProductBuilder().apply {
                tenantId("tenant-1")
                sku("SKU-001")
                name("Test Product")
            }.build()
        }
    }

    "ProductBuilder - 정상 빌드 (필수 필드만)" {
        val input = ProductBuilder().apply {
            tenantId("tenant-1")
            sku("SKU-001")
            name("Test Product")
            price(10000)
        }.build()

        input.tenantId shouldBe "tenant-1"
        input.sku shouldBe "SKU-001"
        input.name shouldBe "Test Product"
        input.price shouldBe 10000
        input.currency shouldBe "KRW"
        input.category shouldBe null
        input.brand shouldBe null
        input.attributes shouldBe emptyMap()
        input.entityType shouldBe "product"
    }

    "ProductBuilder - 정상 빌드 (모든 필드)" {
        val input = ProductBuilder().apply {
            tenantId("tenant-1")
            sku("SKU-002")
            name("Premium Product")
            price(50000)
            currency("USD")
            category("CAT-001")
            brand("BRAND-001")
            attribute("color", "red")
            attribute("size", "M")
        }.build()

        input.tenantId shouldBe "tenant-1"
        input.sku shouldBe "SKU-002"
        input.name shouldBe "Premium Product"
        input.price shouldBe 50000
        input.currency shouldBe "USD"
        input.category shouldBe "CAT-001"
        input.brand shouldBe "BRAND-001"
        input.attributes shouldBe mapOf("color" to "red", "size" to "M")
    }

    "ProductInput - EntityInput 인터페이스 구현" {
        val input = ProductInput(
            tenantId = "t1",
            sku = "SKU-001",
            name = "Product",
            price = 1000
        )

        input.shouldBeInstanceOf<EntityInput>()
        input.tenantId shouldBe "t1"
        input.entityType shouldBe "product"
    }

    "IngestContext.product - 확장 함수로 DeployableContext 반환" {
        val context = Ivm.client().ingest().product {
            tenantId("tenant-1")
            sku("SKU-001")
            name("Test Product")
            price(10000)
        }

        context.shouldBeInstanceOf<DeployableContext>()
    }

    "IngestContext.product - DSL 체이닝" {
        val context = Ivm.client()
            .ingest()
            .product {
                tenantId("tenant-1")
                sku("SKU-001")
                name("Cream")
                price(19000)
                currency("KRW")
                category("SKINCARE")
                brand("OLIVEYOUNG")
                attribute("volume", "50ml")
            }

        context.shouldBeInstanceOf<DeployableContext>()
    }

    "ProductBuilder - attribute 여러 개 추가" {
        val input = ProductBuilder().apply {
            tenantId("t1")
            sku("SKU-001")
            name("Product")
            price(1000)
            attribute("key1", "value1")
            attribute("key2", 123)
            attribute("key3", true)
        }.build()

        input.attributes.size shouldBe 3
        input.attributes["key1"] shouldBe "value1"
        input.attributes["key2"] shouldBe 123
        input.attributes["key3"] shouldBe true
    }

    "ProductBuilder - currency 기본값 KRW" {
        val input = ProductBuilder().apply {
            tenantId("t1")
            sku("SKU-001")
            name("Product")
            price(1000)
        }.build()

        input.currency shouldBe "KRW"
    }

    "ProductBuilder - currency 변경 가능" {
        val input = ProductBuilder().apply {
            tenantId("t1")
            sku("SKU-001")
            name("Product")
            price(1000)
            currency("USD")
        }.build()

        input.currency shouldBe "USD"
    }
})
