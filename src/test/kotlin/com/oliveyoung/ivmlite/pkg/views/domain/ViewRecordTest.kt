package com.oliveyoung.ivmlite.pkg.views.domain

import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank

/**
 * ViewRecord 도메인 모델 테스트
 *
 * 커버리지:
 * - create: 결정적 hash 계산, 필드 설정
 * - calculateHash: 동일 입력 → 동일 hash, 다른 입력 → 다른 hash
 * - init validation: 빈 tenantId, entityKey, viewType, data, hash, viewDefId, viewDefVersion
 * - ViewKey data class equality
 */
class ViewRecordTest : DescribeSpec({

    val tenantId = TenantId("test-tenant")
    val entityKey = EntityKey("product:SKU-001")

    describe("create") {

        it("결정적 hash 생성") {
            val view = ViewRecord.create(
                tenantId = tenantId,
                entityKey = entityKey,
                version = 1L,
                viewType = "PRODUCT_DETAIL",
                data = """{"name":"test"}""",
                viewDefId = "view.product.detail.v1",
                viewDefVersion = "1.0.0",
                usedSlices = listOf("CORE")
            )

            view.hash.shouldNotBeBlank()
            view.tenantId shouldBe tenantId
            view.viewType shouldBe "PRODUCT_DETAIL"
            view.usedSlices shouldBe listOf("CORE")
        }

        it("동일 입력 → 동일 hash") {
            val v1 = ViewRecord.create(
                tenantId, entityKey, 1L, "T", "data", "vd", "1.0.0", listOf("CORE")
            )
            val v2 = ViewRecord.create(
                tenantId, entityKey, 1L, "T", "data", "vd", "1.0.0", listOf("CORE")
            )

            v1.hash shouldBe v2.hash
        }

        it("다른 data → 다른 hash") {
            val v1 = ViewRecord.create(
                tenantId, entityKey, 1L, "T", "data1", "vd", "1.0.0", listOf("CORE")
            )
            val v2 = ViewRecord.create(
                tenantId, entityKey, 1L, "T", "data2", "vd", "1.0.0", listOf("CORE")
            )

            v1.hash shouldNotBe v2.hash
        }
    }

    describe("calculateHash") {

        it("결정성: 동일 입력 → 동일 결과") {
            val h1 = ViewRecord.calculateHash("TYPE", "data", "1.0.0")
            val h2 = ViewRecord.calculateHash("TYPE", "data", "1.0.0")
            h1 shouldBe h2
        }

        it("viewType 다르면 다른 hash") {
            val h1 = ViewRecord.calculateHash("TYPE_A", "data", "1.0.0")
            val h2 = ViewRecord.calculateHash("TYPE_B", "data", "1.0.0")
            h1 shouldNotBe h2
        }

        it("version 다르면 다른 hash") {
            val h1 = ViewRecord.calculateHash("TYPE", "data", "1.0.0")
            val h2 = ViewRecord.calculateHash("TYPE", "data", "2.0.0")
            h1 shouldNotBe h2
        }
    }

    describe("init validation") {

        it("빈 tenantId → IAE") {
            shouldThrow<IllegalArgumentException> {
                ViewRecord.create(
                    TenantId(""), entityKey, 1L, "T", "data", "vd", "1.0.0", listOf("CORE")
                )
            }
        }

        it("빈 entityKey → IAE") {
            shouldThrow<IllegalArgumentException> {
                ViewRecord.create(
                    tenantId, EntityKey(""), 1L, "T", "data", "vd", "1.0.0", listOf("CORE")
                )
            }
        }

        it("version <= 0 → IAE") {
            shouldThrow<IllegalArgumentException> {
                ViewRecord.create(
                    tenantId, entityKey, 0L, "T", "data", "vd", "1.0.0", listOf("CORE")
                )
            }
        }

        it("빈 viewType → IAE") {
            shouldThrow<IllegalArgumentException> {
                ViewRecord.create(
                    tenantId, entityKey, 1L, "", "data", "vd", "1.0.0", listOf("CORE")
                )
            }
        }

        it("빈 data → IAE") {
            shouldThrow<IllegalArgumentException> {
                ViewRecord.create(
                    tenantId, entityKey, 1L, "T", "", "vd", "1.0.0", listOf("CORE")
                )
            }
        }

        it("빈 viewDefId → IAE") {
            shouldThrow<IllegalArgumentException> {
                ViewRecord.create(
                    tenantId, entityKey, 1L, "T", "data", "", "1.0.0", listOf("CORE")
                )
            }
        }

        it("빈 viewDefVersion → IAE") {
            shouldThrow<IllegalArgumentException> {
                ViewRecord.create(
                    tenantId, entityKey, 1L, "T", "data", "vd", "", listOf("CORE")
                )
            }
        }
    }

    describe("ViewKey") {

        it("equality") {
            val k1 = ViewKey(tenantId, entityKey, 1L, "DETAIL")
            val k2 = ViewKey(tenantId, entityKey, 1L, "DETAIL")
            k1 shouldBe k2
        }

        it("다른 viewType → 다름") {
            val k1 = ViewKey(tenantId, entityKey, 1L, "DETAIL")
            val k2 = ViewKey(tenantId, entityKey, 1L, "SEARCH")
            k1 shouldNotBe k2
        }

        it("다른 version → 다름") {
            val k1 = ViewKey(tenantId, entityKey, 1L, "DETAIL")
            val k2 = ViewKey(tenantId, entityKey, 2L, "DETAIL")
            k1 shouldNotBe k2
        }
    }
})
