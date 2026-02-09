package com.oliveyoung.ivmlite.pkg.contracts
import com.oliveyoung.ivmlite.shared.domain.types.Result

import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking

/**
 * LocalYamlContractRegistryAdapter 단위 테스트
 *
 * listContractRefs / listViewDefinitions 구현 검증
 */
class LocalYamlContractRegistryAdapterTest : StringSpec({

    val adapter = LocalYamlContractRegistryAdapter("/contracts/v1")

    "listContractRefs - VIEW_DEFINITION kind로 조회하면 ViewDefinition 계약들 반환" {
        runBlocking {
            val result = adapter.listContractRefs("VIEW_DEFINITION", null)

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val refs = (result as Result.Ok).value
            refs.shouldNotBeEmpty()

            // view-product-core, view-product-search 등이 포함되어야 함
            val ids = refs.map { it.id }
            ids.any { it.contains("view") } shouldBe true
        }
    }

    "listContractRefs - RULESET kind로 조회하면 RuleSet 계약들 반환" {
        runBlocking {
            val result = adapter.listContractRefs("RULESET", null)

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val refs = (result as Result.Ok).value
            refs.shouldNotBeEmpty()
        }
    }

    "listContractRefs - ACTIVE status 필터 적용" {
        runBlocking {
            val result = adapter.listContractRefs("VIEW_DEFINITION", ContractStatus.ACTIVE)

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val refs = (result as Result.Ok).value
            refs.shouldNotBeEmpty()
        }
    }

    "listContractRefs - 존재하지 않는 kind는 빈 목록 반환" {
        runBlocking {
            val result = adapter.listContractRefs("NONEXISTENT_KIND", null)

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val refs = (result as Result.Ok).value
            refs.size shouldBe 0
        }
    }

    "listViewDefinitions - ACTIVE 상태의 ViewDefinition 계약들 반환" {
        runBlocking {
            val result = adapter.listViewDefinitions(ContractStatus.ACTIVE)

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val contracts = (result as Result.Ok).value
            contracts.shouldNotBeEmpty()

            // 모든 계약이 ACTIVE 상태인지 확인
            contracts.forEach { it.meta.status shouldBe ContractStatus.ACTIVE }
        }
    }

    "listViewDefinitions - null status면 모든 ViewDefinition 반환" {
        runBlocking {
            val result = adapter.listViewDefinitions(null)

            result.shouldBeInstanceOf<Result.Ok<*>>()
            val contracts = (result as Result.Ok).value
            contracts.shouldNotBeEmpty()
        }
    }
})
