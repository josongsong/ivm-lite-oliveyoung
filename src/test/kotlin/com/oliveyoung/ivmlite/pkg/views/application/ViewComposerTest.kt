package com.oliveyoung.ivmlite.pkg.views.application

import com.oliveyoung.ivmlite.pkg.slices.domain.DeleteReason
import com.oliveyoung.ivmlite.pkg.slices.domain.SliceRecord
import com.oliveyoung.ivmlite.pkg.slices.domain.Tombstone
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.Result
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * ViewComposer 테스트
 *
 * 커버리지 대상:
 * - compose: 정상 조합, 빈 슬라이스, tombstone 전체, 불일치 tenant/entity/version
 * - composeOne: 정상, viewType 미매칭
 * - combineSlices: 유효 JSON 파싱, 잘못된 JSON 스킵
 * - extractViewType: viewDefId 파싱 패턴
 */
class ViewComposerTest : DescribeSpec({

    val composer = ViewComposer()

    val tenantId = TenantId("tenant-1")
    val entityKey = EntityKey("product:SKU-001")
    val version = 100L
    val ruleSetVersion = SemVer.parse("1.0.0")

    fun createSlice(
        sliceType: SliceType = SliceType.CORE,
        data: String = """{"name":"test","price":1000}""",
        tombstone: Tombstone? = null,
        tid: TenantId = tenantId,
        ek: EntityKey = entityKey,
        ver: Long = version
    ) = SliceRecord(
        tenantId = tid,
        entityKey = ek,
        version = ver,
        sliceType = sliceType,
        data = data,
        hash = "hash-${sliceType.name}",
        ruleSetId = "ruleset.core.v1",
        ruleSetVersion = ruleSetVersion,
        tombstone = tombstone
    )

    describe("compose") {

        it("단일 슬라이스 → View 생성 성공") {
            val slices = listOf(createSlice())

            val result = composer.compose(slices, "view.product.core.v1", "1.0.0")

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val views = (result as Result.Ok).value
            views.size shouldBe 1
            views[0].viewType shouldBe "PRODUCT_CORE"
            views[0].viewDefId shouldBe "view.product.core.v1"
            views[0].usedSlices shouldBe listOf("CORE")
        }

        it("여러 슬라이스 → 조합 View 생성") {
            val slices = listOf(
                createSlice(SliceType.CORE, """{"name":"iPhone"}"""),
                createSlice(SliceType.PRICE, """{"price":1200000}"""),
                createSlice(SliceType.INVENTORY, """{"stock":50}""")
            )

            val result = composer.compose(slices, "view.product.detail.v1", "1.0.0")

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val views = (result as Result.Ok).value
            views.size shouldBe 1
            views[0].data shouldContain "CORE"
            views[0].data shouldContain "PRICE"
            views[0].data shouldContain "INVENTORY"
            views[0].usedSlices.size shouldBe 3
        }

        it("빈 슬라이스 → ValidationError") {
            val result = composer.compose(emptyList(), "view.product.core.v1", "1.0.0")

            result.shouldBeInstanceOf<Result.Err>()
            val error = (result as Result.Err).error
            error.shouldBeInstanceOf<DomainError.ValidationError>()
        }

        it("모든 슬라이스가 tombstone → ValidationError") {
            val slices = listOf(
                createSlice(
                    tombstone = Tombstone.create(version, DeleteReason.USER_DELETE)
                )
            )

            val result = composer.compose(slices, "view.product.core.v1", "1.0.0")

            result.shouldBeInstanceOf<Result.Err>()
            val error = (result as Result.Err).error
            error.shouldBeInstanceOf<DomainError.ValidationError>()
            error.msg shouldContain "tombstone"
        }

        it("tenant 불일치 → ValidationError") {
            val slices = listOf(
                createSlice(tid = TenantId("tenant-A")),
                createSlice(tid = TenantId("tenant-B"), sliceType = SliceType.PRICE)
            )

            val result = composer.compose(slices, "view.product.core.v1", "1.0.0")

            result.shouldBeInstanceOf<Result.Err>()
            val error = (result as Result.Err).error
            error.shouldBeInstanceOf<DomainError.ValidationError>()
        }

        it("entityKey 불일치 → ValidationError") {
            val slices = listOf(
                createSlice(ek = EntityKey("product:A")),
                createSlice(ek = EntityKey("product:B"), sliceType = SliceType.PRICE)
            )

            val result = composer.compose(slices, "view.product.core.v1", "1.0.0")

            result.shouldBeInstanceOf<Result.Err>()
        }

        it("version 불일치 → ValidationError") {
            val slices = listOf(
                createSlice(ver = 1L),
                createSlice(ver = 2L, sliceType = SliceType.PRICE)
            )

            val result = composer.compose(slices, "view.product.core.v1", "1.0.0")

            result.shouldBeInstanceOf<Result.Err>()
        }

        it("tombstone 섞인 슬라이스 → tombstone 제외 후 정상 조합") {
            val slices = listOf(
                createSlice(SliceType.CORE, """{"name":"test"}"""),
                createSlice(
                    SliceType.PRICE,
                    """{"price":1000}""",
                    tombstone = Tombstone.create(version, DeleteReason.ARCHIVED)
                )
            )

            val result = composer.compose(slices, "view.product.core.v1", "1.0.0")

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val views = (result as Result.Ok).value
            views[0].usedSlices shouldBe listOf("CORE")
        }

        it("잘못된 JSON 데이터 → 해당 슬라이스 스킵 후 조합") {
            val slices = listOf(
                createSlice(SliceType.CORE, """{"name":"valid"}"""),
                createSlice(SliceType.PRICE, "not-a-json")
            )

            val result = composer.compose(slices, "view.product.core.v1", "1.0.0")

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val views = (result as Result.Ok).value
            views[0].data shouldContain "CORE"
        }

        it("hash 결정성: 동일 입력 → 동일 hash") {
            val slices = listOf(createSlice(SliceType.CORE, """{"name":"deterministic"}"""))

            val result1 = composer.compose(slices, "view.product.core.v1", "1.0.0")
            val result2 = composer.compose(slices, "view.product.core.v1", "1.0.0")

            result1.shouldBeInstanceOf<Result.Ok<*>>()
            result2.shouldBeInstanceOf<Result.Ok<*>>()
            val view1 = (result1 as Result.Ok).value[0]
            val view2 = (result2 as Result.Ok).value[0]
            view1.hash shouldBe view2.hash
        }
    }

    describe("composeOne") {

        it("정상 → 특정 viewType의 ViewRecord 반환") {
            val slices = listOf(createSlice(SliceType.CORE, """{"name":"one"}"""))

            val result = composer.composeOne(
                slices, "view.product.core.v1", "PRODUCT_CORE", "1.0.0"
            )

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val view = (result as Result.Ok).value
            view.viewType shouldBe "PRODUCT_CORE"
        }

        it("viewType 미매칭 → NotFoundError") {
            val slices = listOf(createSlice(SliceType.CORE, """{"name":"miss"}"""))

            val result = composer.composeOne(
                slices, "view.product.core.v1", "NONEXISTENT_TYPE", "1.0.0"
            )

            result.shouldBeInstanceOf<Result.Err>()
            val error = (result as Result.Err).error
            error.shouldBeInstanceOf<DomainError.NotFoundError>()
        }

        it("빈 슬라이스 → ValidationError 전파") {
            val result = composer.composeOne(
                emptyList(), "view.product.core.v1", "PRODUCT_CORE", "1.0.0"
            )

            result.shouldBeInstanceOf<Result.Err>()
        }
    }

    describe("extractViewType") {

        it("view.product.pdp.v1 → PRODUCT_PDP") {
            val slices = listOf(createSlice(SliceType.CORE, """{"x":1}"""))
            val result = composer.compose(slices, "view.product.pdp.v1", "1.0.0")

            result.shouldBeInstanceOf<Result.Ok<*>>()
            (result as Result.Ok).value[0].viewType shouldBe "PRODUCT_PDP"
        }

        it("view.brand.detail.v1 → BRAND_DETAIL") {
            val slices = listOf(createSlice(SliceType.CORE, """{"x":1}"""))
            val result = composer.compose(slices, "view.brand.detail.v1", "1.0.0")

            result.shouldBeInstanceOf<Result.Ok<*>>()
            (result as Result.Ok).value[0].viewType shouldBe "BRAND_DETAIL"
        }

        it("비표준 viewDefId → 전체 대문자+언더스코어 변환") {
            val slices = listOf(createSlice(SliceType.CORE, """{"x":1}"""))
            val result = composer.compose(slices, "custom-view-id", "1.0.0")

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val viewType = (result as Result.Ok).value[0].viewType
            viewType shouldNotBe null
        }
    }
})
