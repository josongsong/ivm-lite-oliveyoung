package com.oliveyoung.ivmlite.sdk

import com.oliveyoung.ivmlite.pkg.orchestration.application.QueryViewWorkflow
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk

/**
 * IvmContext 단위 테스트
 *
 * 커버리지:
 * - Builder 패턴 (config, executor, queryWorkflow)
 * - EMPTY 상수
 * - canDeploy / canQuery 헬퍼
 * - requireExecutor / requireQueryWorkflow (fail-fast)
 * - data class equality
 */
class IvmContextTest : DescribeSpec({

    describe("Builder") {

        it("기본 빌드 → 모든 필드 null/default") {
            val ctx = IvmContext.builder().build()

            ctx.executor shouldBe null
            ctx.queryWorkflow shouldBe null
            ctx.config shouldNotBe null
        }

        it("executor 설정") {
            val executor = mockk<DeployExecutor>()
            val ctx = IvmContext.builder()
                .executor(executor)
                .build()

            ctx.executor shouldBe executor
        }

        it("queryWorkflow 설정") {
            val workflow = mockk<QueryViewWorkflow>()
            val ctx = IvmContext.builder()
                .queryWorkflow(workflow)
                .build()

            ctx.queryWorkflow shouldBe workflow
        }

        it("config(IvmClientConfig) 설정") {
            val config = IvmClientConfig.Builder().apply {
                baseUrl("http://custom:8080")
            }.build()

            val ctx = IvmContext.builder()
                .config(config)
                .build()

            ctx.config.baseUrl shouldBe "http://custom:8080"
        }

        it("config DSL 설정") {
            val ctx = IvmContext.builder()
                .config {
                    baseUrl("http://dsl:8080")
                }
                .build()

            ctx.config.baseUrl shouldBe "http://dsl:8080"
        }

        it("모든 필드 설정") {
            val executor = mockk<DeployExecutor>()
            val workflow = mockk<QueryViewWorkflow>()

            val ctx = IvmContext.builder()
                .executor(executor)
                .queryWorkflow(workflow)
                .config {
                    baseUrl("http://full:8080")
                }
                .build()

            ctx.executor shouldBe executor
            ctx.queryWorkflow shouldBe workflow
            ctx.config.baseUrl shouldBe "http://full:8080"
        }
    }

    describe("EMPTY") {

        it("EMPTY → 모든 필드 null/default") {
            val empty = IvmContext.EMPTY

            empty.executor shouldBe null
            empty.queryWorkflow shouldBe null
            empty.canDeploy shouldBe false
            empty.canQuery shouldBe false
        }
    }

    describe("canDeploy / canQuery") {

        it("executor 있으면 canDeploy=true") {
            val ctx = IvmContext.builder()
                .executor(mockk<DeployExecutor>())
                .build()

            ctx.canDeploy shouldBe true
            ctx.canQuery shouldBe false
        }

        it("queryWorkflow 있으면 canQuery=true") {
            val ctx = IvmContext.builder()
                .queryWorkflow(mockk<QueryViewWorkflow>())
                .build()

            ctx.canDeploy shouldBe false
            ctx.canQuery shouldBe true
        }

        it("둘 다 설정 → 둘 다 true") {
            val ctx = IvmContext.builder()
                .executor(mockk<DeployExecutor>())
                .queryWorkflow(mockk<QueryViewWorkflow>())
                .build()

            ctx.canDeploy shouldBe true
            ctx.canQuery shouldBe true
        }
    }

    describe("requireExecutor / requireQueryWorkflow") {

        it("executor 없으면 → ISE") {
            val ctx = IvmContext.builder().build()

            val ex = shouldThrow<IllegalStateException> {
                ctx.requireExecutor()
            }
            ex.message shouldContain "DeployExecutor not configured"
        }

        it("executor 있으면 → 반환") {
            val executor = mockk<DeployExecutor>()
            val ctx = IvmContext.builder().executor(executor).build()

            ctx.requireExecutor() shouldBe executor
        }

        it("queryWorkflow 없으면 → ISE") {
            val ctx = IvmContext.builder().build()

            val ex = shouldThrow<IllegalStateException> {
                ctx.requireQueryWorkflow()
            }
            ex.message shouldContain "QueryViewWorkflow not configured"
        }

        it("queryWorkflow 있으면 → 반환") {
            val workflow = mockk<QueryViewWorkflow>()
            val ctx = IvmContext.builder().queryWorkflow(workflow).build()

            ctx.requireQueryWorkflow() shouldBe workflow
        }
    }

    describe("data class equality") {

        it("같은 설정 → 동일") {
            val executor = mockk<DeployExecutor>()
            val ctx1 = IvmContext.builder().executor(executor).build()
            val ctx2 = IvmContext.builder().executor(executor).build()

            ctx1 shouldBe ctx2
        }
    }
})
