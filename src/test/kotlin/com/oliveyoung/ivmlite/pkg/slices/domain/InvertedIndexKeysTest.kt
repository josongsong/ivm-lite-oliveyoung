package com.oliveyoung.ivmlite.pkg.slices.domain

import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

/**
 * InvertedIndexKeys 단위 테스트
 *
 * 커버리지:
 * - refPk: PK 생성, 패딩, 기본값
 * - targetSk: SK 생성
 * - splitEntityKey: 정상 파싱, 잘못된 형식
 * - 상수값 확인
 */
class InvertedIndexKeysTest : DescribeSpec({

    val tenantId = TenantId("tenant-1")

    describe("refPk") {

        it("기본 PK 생성") {
            val pk = InvertedIndexKeys.refPk(
                tenantId = tenantId,
                refEntityKey = EntityKey("BRAND#tenant-1#br001"),
                refVersion = 42L
            )

            pk shouldStartWith "REF#BRAND#tenant-1#br001#v"
            pk shouldContain "000000000042" // 12자리 패딩
        }

        it("커스텀 패딩 폭") {
            val pk = InvertedIndexKeys.refPk(
                tenantId = tenantId,
                refEntityKey = EntityKey("BRAND#tenant-1#br001"),
                refVersion = 5L,
                padWidth = 6
            )

            pk shouldContain "000005"
        }

        it("큰 버전 값") {
            val pk = InvertedIndexKeys.refPk(
                tenantId = tenantId,
                refEntityKey = EntityKey("PRODUCT#tenant-1#prod001"),
                refVersion = 999_999_999_999L
            )

            pk shouldContain "999999999999"
        }
    }

    describe("targetSk") {

        it("기본 SK 생성") {
            val sk = InvertedIndexKeys.targetSk(
                targetEntityKey = EntityKey("PRODUCT#tenant-1#prod001")
            )

            sk shouldBe "PRODUCT#prod001"
        }
    }

    describe("splitEntityKey") {

        it("정상 분리: BRAND#tenant-1#br001") {
            val (type, tenant, id) = InvertedIndexKeys.splitEntityKey(EntityKey("BRAND#tenant-1#br001"))

            type shouldBe "BRAND"
            tenant shouldBe "tenant-1"
            id shouldBe "br001"
        }

        it("잘못된 형식 (부분 부족) → IAE") {
            shouldThrow<IllegalArgumentException> {
                InvertedIndexKeys.splitEntityKey(EntityKey("INVALID"))
            }
        }

        it("2 부분만 → IAE") {
            shouldThrow<IllegalArgumentException> {
                InvertedIndexKeys.splitEntityKey(EntityKey("TYPE#value"))
            }
        }
    }

    describe("상수값") {

        it("DEFAULT_PAD_WIDTH = 12") {
            InvertedIndexKeys.DEFAULT_PAD_WIDTH shouldBe 12
        }

        it("DEFAULT_SEPARATOR = #") {
            InvertedIndexKeys.DEFAULT_SEPARATOR shouldBe "#"
        }
    }
})
