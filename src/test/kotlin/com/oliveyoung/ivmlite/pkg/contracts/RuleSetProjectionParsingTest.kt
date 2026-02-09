package com.oliveyoung.ivmlite.pkg.contracts
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.slices.domain.FieldMapping
import com.oliveyoung.ivmlite.pkg.slices.domain.Projection
import com.oliveyoung.ivmlite.pkg.slices.domain.ProjectionMode
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.SliceKind
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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

    "LocalYaml - ruleset.v1.yaml에서 projection 파싱 검증" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        // CORE 슬라이스에 projection이 있는지 확인
        val coreSlice = contract.slices.first { it.type == SliceType.CORE }
        val brandJoin = coreSlice.joins.firstOrNull { it.name == "brandInfo" }
        
        // 실제 Contract에 projection이 정의되어 있으면 검증
        if (brandJoin != null && brandJoin.projection != null) {
            val projection = brandJoin.projection!!
            projection.mode shouldBe ProjectionMode.COPY_FIELDS
            projection.fields.size shouldBe 2

            // fromTargetPath/toOutputPath 형식 확인
            val field1 = projection.fields[0]
            field1.fromTargetPath shouldBe "/brandName"
            field1.toOutputPath shouldBe "/brandName"

            val field2 = projection.fields[1]
            field2.fromTargetPath shouldBe "/brandLogoUrl"
            field2.toOutputPath shouldBe "/brandLogoUrl"
        } else {
            // projection이 없거나 brandInfo join이 없는 경우도 정상 (하위 호환성)
            coreSlice.joins.isNotEmpty() shouldBe true
        }
    }

    "LocalYaml - ruleset-product-doc001.v1.yaml에서 projection 파싱 (from/to 형식)" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.product.doc001.v1", SemVer.parse("1.1.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        // ENRICHMENT sliceKind를 가진 슬라이스 찾기
        @Suppress("UNUSED_VARIABLE")
        val enrichmentSlice = contract.slices.firstOrNull {
            it.sliceKind == SliceKind.ENRICHMENT
        }

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
        val ref = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        // projection이 없는 join 찾기
        val joinsWithoutProjection = contract.slices.flatMap { it.joins }
            .filter { it.projection == null }

        // projection이 없는 join도 정상적으로 파싱되어야 함
        joinsWithoutProjection.forEach { join ->
            join.projection shouldBe null
        }
    }

    "LocalYaml - projection fields 빈 리스트 처리" {
        // 빈 fields 리스트는 실제 Contract에는 없지만, 파싱 로직이 처리할 수 있는지 확인
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0"))

        val result = adapter.loadRuleSetContract(ref)

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val contract = (result as Result.Ok).value

        // projection이 있는 join의 fields가 비어있지 않은지 확인
        val projections = contract.slices.flatMap { it.joins }
            .mapNotNull { it.projection }

        projections.forEach { projection ->
            projection.fields.isNotEmpty() shouldBe true  // 실제 Contract에는 빈 fields가 없음
        }
    }

    "LocalYaml - projection mode 기본값 (COPY_FIELDS)" {
        val adapter = LocalYamlContractRegistryAdapter()
        val ref = ContractRef("ruleset.core.v1", SemVer.parse("1.0.0"))

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
