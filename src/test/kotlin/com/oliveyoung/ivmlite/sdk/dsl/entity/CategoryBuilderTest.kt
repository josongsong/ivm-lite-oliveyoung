package com.oliveyoung.ivmlite.sdk.dsl.entity

import com.oliveyoung.ivmlite.sdk.client.Ivm
import com.oliveyoung.ivmlite.sdk.dsl.deploy.DeployableContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * CategoryBuilder 테스트
 * RFC-IMPL-011 Wave 5-L (Entity Type 확장)
 */
class CategoryBuilderTest : StringSpec({

    "CategoryBuilder - 필수 필드 누락 시 IllegalArgumentException (tenantId)" {
        shouldThrow<IllegalArgumentException> {
            CategoryBuilder().apply {
                categoryId("CAT-001")
                name("스킨케어")
            }.build()
        }.message shouldBe "tenantId is required"
    }

    "CategoryBuilder - 필수 필드 누락 시 IllegalArgumentException (categoryId)" {
        shouldThrow<IllegalArgumentException> {
            CategoryBuilder().apply {
                tenantId("tenant-1")
                name("스킨케어")
            }.build()
        }.message shouldBe "categoryId is required"
    }

    "CategoryBuilder - 필수 필드 누락 시 IllegalArgumentException (name)" {
        shouldThrow<IllegalArgumentException> {
            CategoryBuilder().apply {
                tenantId("tenant-1")
                categoryId("CAT-001")
            }.build()
        }.message shouldBe "name is required"
    }

    "CategoryBuilder - 정상 빌드 (필수 필드만)" {
        val input = CategoryBuilder().apply {
            tenantId("tenant-1")
            categoryId("CAT-001")
            name("스킨케어")
        }.build()

        input.tenantId shouldBe "tenant-1"
        input.categoryId shouldBe "CAT-001"
        input.name shouldBe "스킨케어"
        input.parentId shouldBe null
        input.depth shouldBe 0
        input.displayOrder shouldBe 0
        input.attributes shouldBe emptyMap()
        input.entityType shouldBe "category"
    }

    "CategoryBuilder - 정상 빌드 (모든 필드 - 계층 구조)" {
        val input = CategoryBuilder().apply {
            tenantId("tenant-1")
            categoryId("CAT-001-001")
            name("토너/스킨")
            parentId("CAT-001")
            depth(1)
            displayOrder(1)
            attribute("icon", "toner.png")
            attribute("featured", true)
        }.build()

        input.tenantId shouldBe "tenant-1"
        input.categoryId shouldBe "CAT-001-001"
        input.name shouldBe "토너/스킨"
        input.parentId shouldBe "CAT-001"
        input.depth shouldBe 1
        input.displayOrder shouldBe 1
        input.attributes shouldBe mapOf("icon" to "toner.png", "featured" to true)
    }

    "CategoryInput - EntityInput 인터페이스 구현" {
        val input = CategoryInput(
            tenantId = "t1",
            categoryId = "CAT-001",
            name = "Category"
        )

        input.shouldBeInstanceOf<EntityInput>()
        input.tenantId shouldBe "t1"
        input.entityType shouldBe "category"
    }

    "IngestContext.category - 확장 함수로 DeployableContext 반환" {
        val context = Ivm.client().ingest().category {
            tenantId("tenant-1")
            categoryId("CAT-001")
            name("스킨케어")
        }

        context.shouldBeInstanceOf<DeployableContext>()
    }

    "IngestContext.category - DSL 체이닝 (3단계 계층)" {
        // 루트 카테고리
        val root = Ivm.client().ingest().category {
            tenantId("tenant-1")
            categoryId("CAT-ROOT")
            name("뷰티")
            depth(0)
            displayOrder(1)
        }
        root.shouldBeInstanceOf<DeployableContext>()

        // 중간 카테고리
        val mid = Ivm.client().ingest().category {
            tenantId("tenant-1")
            categoryId("CAT-001")
            name("스킨케어")
            parentId("CAT-ROOT")
            depth(1)
            displayOrder(1)
        }
        mid.shouldBeInstanceOf<DeployableContext>()

        // 말단 카테고리
        val leaf = Ivm.client().ingest().category {
            tenantId("tenant-1")
            categoryId("CAT-001-001")
            name("토너")
            parentId("CAT-001")
            depth(2)
            displayOrder(1)
            attribute("leaf", true)
        }
        leaf.shouldBeInstanceOf<DeployableContext>()
    }
})
