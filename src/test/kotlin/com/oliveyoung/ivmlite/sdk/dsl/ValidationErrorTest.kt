package com.oliveyoung.ivmlite.sdk.dsl

import com.oliveyoung.ivmlite.sdk.client.Ivm
import com.oliveyoung.ivmlite.sdk.dsl.entity.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * DSL Validation Error 테스트
 * RFC-IMPL-011 Wave 5-L
 *
 * DSL에서 발생할 수 있는 validation error 케이스 검증
 */
class ValidationErrorTest : StringSpec({

    // ==================== Product Validation Errors ====================

    "Product - tenantId 누락" {
        val ex = shouldThrow<IllegalArgumentException> {
            ProductBuilder().apply {
                sku("SKU-001")
                name("Test Product")
                price(10000)
            }.build()
        }
        ex.message shouldBe "tenantId is required"
    }

    "Product - sku 누락" {
        val ex = shouldThrow<IllegalArgumentException> {
            ProductBuilder().apply {
                tenantId("tenant-1")
                name("Test Product")
                price(10000)
            }.build()
        }
        ex.message shouldBe "sku is required"
    }

    "Product - name 누락" {
        val ex = shouldThrow<IllegalArgumentException> {
            ProductBuilder().apply {
                tenantId("tenant-1")
                sku("SKU-001")
                price(10000)
            }.build()
        }
        ex.message shouldBe "name is required"
    }

    "Product - price 누락" {
        val ex = shouldThrow<IllegalArgumentException> {
            ProductBuilder().apply {
                tenantId("tenant-1")
                sku("SKU-001")
                name("Test Product")
            }.build()
        }
        ex.message shouldBe "price is required"
    }

    "Product - 모든 필수 필드 누락 시 첫 번째 에러만 표시" {
        val ex = shouldThrow<IllegalArgumentException> {
            ProductBuilder().build()
        }
        ex.message shouldBe "tenantId is required"
    }

    // ==================== Brand Validation Errors ====================

    "Brand - tenantId 누락" {
        val ex = shouldThrow<IllegalArgumentException> {
            BrandBuilder().apply {
                brandId("BRAND-001")
                name("Brand")
            }.build()
        }
        ex.message shouldBe "tenantId is required"
    }

    "Brand - brandId 누락" {
        val ex = shouldThrow<IllegalArgumentException> {
            BrandBuilder().apply {
                tenantId("tenant-1")
                name("Brand")
            }.build()
        }
        ex.message shouldBe "brandId is required"
    }

    "Brand - name 누락" {
        val ex = shouldThrow<IllegalArgumentException> {
            BrandBuilder().apply {
                tenantId("tenant-1")
                brandId("BRAND-001")
            }.build()
        }
        ex.message shouldBe "name is required"
    }

    // ==================== Category Validation Errors ====================

    "Category - tenantId 누락" {
        val ex = shouldThrow<IllegalArgumentException> {
            CategoryBuilder().apply {
                categoryId("CAT-001")
                name("Category")
            }.build()
        }
        ex.message shouldBe "tenantId is required"
    }

    "Category - categoryId 누락" {
        val ex = shouldThrow<IllegalArgumentException> {
            CategoryBuilder().apply {
                tenantId("tenant-1")
                name("Category")
            }.build()
        }
        ex.message shouldBe "categoryId is required"
    }

    "Category - name 누락" {
        val ex = shouldThrow<IllegalArgumentException> {
            CategoryBuilder().apply {
                tenantId("tenant-1")
                categoryId("CAT-001")
            }.build()
        }
        ex.message shouldBe "name is required"
    }

    // ==================== DSL Chain Validation Errors ====================

    "Ivm.client().ingest().product - 필수 필드 누락 시 체이닝에서도 에러" {
        val ex = shouldThrow<IllegalArgumentException> {
            Ivm.client().ingest().product {
                // tenantId 누락
                sku("SKU-001")
                name("Test")
                price(1000)
            }
        }
        ex.message shouldBe "tenantId is required"
    }

    "Ivm.client().ingest().brand - 필수 필드 누락 시 체이닝에서도 에러" {
        val ex = shouldThrow<IllegalArgumentException> {
            Ivm.client().ingest().brand {
                // brandId 누락
                tenantId("tenant-1")
                name("Brand")
            }
        }
        ex.message shouldBe "brandId is required"
    }

    "Ivm.client().ingest().category - 필수 필드 누락 시 체이닝에서도 에러" {
        val ex = shouldThrow<IllegalArgumentException> {
            Ivm.client().ingest().category {
                // name 누락
                tenantId("tenant-1")
                categoryId("CAT-001")
            }
        }
        ex.message shouldBe "name is required"
    }

    // ==================== Edge Cases ====================

    "Product - 빈 문자열은 유효한 값" {
        // 빈 문자열도 null이 아니므로 통과
        val input = ProductBuilder().apply {
            tenantId("")  // 빈 문자열
            sku("")
            name("")
            price(0)
        }.build()

        input.tenantId shouldBe ""
        input.sku shouldBe ""
        input.name shouldBe ""
        input.price shouldBe 0
    }

    "Product - 음수 가격도 유효 (비즈니스 로직은 별도)" {
        // DSL 레벨에서는 음수도 허용 (비즈니스 규칙은 상위에서 검증)
        val input = ProductBuilder().apply {
            tenantId("tenant-1")
            sku("SKU-001")
            name("Test")
            price(-1000)
        }.build()

        input.price shouldBe -1000
    }

    "Category - 음수 depth도 유효 (비즈니스 로직은 별도)" {
        val input = CategoryBuilder().apply {
            tenantId("tenant-1")
            categoryId("CAT-001")
            name("Category")
            depth(-1)
        }.build()

        input.depth shouldBe -1
    }

    // ==================== Multiple Errors Scenario ====================

    "Product - 여러 필드 누락 시 순차적으로 에러 발생" {
        // 첫 번째: tenantId
        shouldThrow<IllegalArgumentException> {
            ProductBuilder().apply {
                // tenantId 없음
                // sku 없음
                // name 없음
                price(1000)
            }.build()
        }.message shouldBe "tenantId is required"

        // tenantId 추가 후: sku
        shouldThrow<IllegalArgumentException> {
            ProductBuilder().apply {
                tenantId("tenant-1")
                // sku 없음
                // name 없음
                price(1000)
            }.build()
        }.message shouldBe "sku is required"

        // tenantId, sku 추가 후: name
        shouldThrow<IllegalArgumentException> {
            ProductBuilder().apply {
                tenantId("tenant-1")
                sku("SKU-001")
                // name 없음
                price(1000)
            }.build()
        }.message shouldBe "name is required"

        // tenantId, sku, name 추가 후: price
        shouldThrow<IllegalArgumentException> {
            ProductBuilder().apply {
                tenantId("tenant-1")
                sku("SKU-001")
                name("Test")
                // price 없음
            }.build()
        }.message shouldBe "price is required"
    }

    // ==================== Attribute Validation ====================

    "Product - attribute는 Any 타입 허용" {
        val input = ProductBuilder().apply {
            tenantId("tenant-1")
            sku("SKU-001")
            name("Test")
            price(1000)
            attribute("string", "value")
            attribute("int", 123)
            attribute("long", 123L)
            attribute("double", 1.23)
            attribute("boolean", true)
            attribute("list", listOf(1, 2, 3))
            attribute("map", mapOf("key" to "value"))
        }.build()

        input.attributes.size shouldBe 7
        input.attributes["string"] shouldBe "value"
        input.attributes["int"] shouldBe 123
        input.attributes["long"] shouldBe 123L
        input.attributes["double"] shouldBe 1.23
        input.attributes["boolean"] shouldBe true
        input.attributes["list"] shouldBe listOf(1, 2, 3)
        input.attributes["map"] shouldBe mapOf("key" to "value")
    }

    "Brand - attribute 중복 key는 마지막 값으로 덮어쓰기" {
        val input = BrandBuilder().apply {
            tenantId("tenant-1")
            brandId("BRAND-001")
            name("Brand")
            attribute("key", "first")
            attribute("key", "second")
            attribute("key", "third")
        }.build()

        input.attributes["key"] shouldBe "third"
        input.attributes.size shouldBe 1
    }
})
