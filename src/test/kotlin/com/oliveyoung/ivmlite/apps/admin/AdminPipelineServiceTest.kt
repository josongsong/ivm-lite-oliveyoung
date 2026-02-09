package com.oliveyoung.ivmlite.apps.admin

import com.oliveyoung.ivmlite.apps.admin.application.AdminPipelineService
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.jooq.DSLContext

/**
 * AdminPipelineService 단위 테스트
 *
 * SOTA TDD:
 * - Validation 로직 검증
 * - DSL 의존 메서드는 통합 테스트에서 검증
 */
class AdminPipelineServiceTest : DescribeSpec({

    lateinit var dsl: DSLContext
    lateinit var service: AdminPipelineService

    beforeEach {
        dsl = mockk(relaxed = true)
        service = AdminPipelineService(dsl)
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
