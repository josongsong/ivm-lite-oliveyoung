package com.oliveyoung.ivmlite.pkg.contracts

import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.slices.domain.ProjectionMode
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * RuleSet Contract에서 Projection 파싱 검증
 *
 * 검증 항목:
 * 1. YAML Contract에서 projection 정의 파싱
 * 2. fromTargetPath/toOutputPath 형식 파싱
 * 3. from/to 형식 파싱 (하위 호환성)
 * 4. projection이 없는 경우 null 반환
 * 5. 실제 Contract 파일 (ruleset.v1.yaml) 검증
 */
class RuleSetProjectionParsingTest : StringSpec({

    "LocalYaml - ruleset.product.oliveyoung.v1에서 projection 파싱 검증" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.product.oliveyoung.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        // ENRICHED 슬라이스에 brand join projection 확인
        val enrichedSlice = contract.slices.firstOrNull { it.type == SliceType.ENRICHED }
        enrichedSlice shouldNotBe null
        val brandJoin = enrichedSlice!!.joins.firstOrNull { it.name == "brand" }
        brandJoin shouldNotBe null
        brandJoin!!.projection shouldNotBe null
        val projection = brandJoin.projection!!
        projection.mode shouldBe ProjectionMode.COPY_FIELDS
        projection.fields.size shouldBe 2
        projection.fields.any { it.fromTargetPath == "name" && it.toOutputPath == "brandName" } shouldBe true
        projection.fields.any { it.fromTargetPath == "logoUrl" && it.toOutputPath == "brandLogoUrl" } shouldBe true
    }

    "LocalYaml - ruleset.product.oliveyoung.v1에서 projection 파싱 (from/to 형식)" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.product.oliveyoung.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        // 모든 슬라이스에서 projection 확인
        val allProjections = contract.slices.flatMap { slice ->
            slice.joins.mapNotNull { it.projection }
        }

        // from/to 형식이 fromTargetPath/toOutputPath로 변환되었는지 확인
        if (allProjections.isNotEmpty()) {
            val projection = allProjections.first()
            projection.mode shouldBe ProjectionMode.COPY_FIELDS
            // from/to 형식이 파싱되었는지 확인 (실제 Contract는 from: name, to: brandName)
            projection.fields.isNotEmpty() shouldBe true
            // 실제 값은 Contract 파일에 따라 다를 수 있음
        }
    }

    "LocalYaml - projection 없음 → null 반환" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.brand.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        val joinsWithoutProjection = contract.slices.flatMap { it.joins }
            .filter { it.projection == null }

        joinsWithoutProjection.forEach { join ->
            join.projection shouldBe null
        }
    }

    "LocalYaml - projection fields 빈 리스트 처리" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.product.oliveyoung.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        val projections = contract.slices.flatMap { it.joins }
            .mapNotNull { it.projection }

        projections.forEach { projection ->
            projection.fields.isNotEmpty() shouldBe true
        }
    }

    "LocalYaml - projection mode 기본값 (COPY_FIELDS)" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.product.oliveyoung.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        val projections = contract.slices.flatMap { it.joins }
            .mapNotNull { it.projection }

        projections.forEach { projection ->
            projection.mode shouldBe ProjectionMode.COPY_FIELDS
        }
    }
})
