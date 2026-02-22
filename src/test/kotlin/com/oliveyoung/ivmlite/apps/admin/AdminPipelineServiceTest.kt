package com.oliveyoung.ivmlite.apps.admin

import com.oliveyoung.ivmlite.apps.admin.application.AdminPipelineService
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * AdminPipelineService 단위 테스트
 *
 * DynamoDB 기반 (ExplorerRepositoryPort, SinkEventRepositoryPort)
 */
class AdminPipelineServiceTest : DescribeSpec({

    lateinit var service: AdminPipelineService

    beforeEach {
        service = AdminPipelineService(contractRegistry = null, explorerRepo = null, sinkEventRepo = null)
    }

    describe("getEntityFlow") {
        it("should return validation error for blank entityKey") {
            // When
            val result = service.getEntityFlow("")

            // Then
            result.shouldBeInstanceOf<Result.Err>()
            val error = (result as Result.Err).error
            error.shouldBeInstanceOf<DomainError.ValidationError>()
            (error as DomainError.ValidationError).field shouldBe "entityKey"
        }

        it("should return validation error for too long entityKey") {
            // Given
            val longKey = "a".repeat(300)

            // When
            val result = service.getEntityFlow(longKey)

            // Then
            result.shouldBeInstanceOf<Result.Err>()
            val error = (result as Result.Err).error
            error.shouldBeInstanceOf<DomainError.ValidationError>()
            (error as DomainError.ValidationError).field shouldBe "entityKey"
        }
    }
})
