package com.oliveyoung.ivmlite.sdk

import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.execution.DeployExecutor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk

/**
 * Ivm.kt 단위 테스트 (SDK Entry Point)
 *
 * 커버리지:
 * - initialize / isInitialized / reset 상태 관리
 * - context() 조회
 * - configure(IvmClientConfig) / configure(DSL)
 * - product/brand/category DSL → DeployableContext 반환
 * - client() 캐싱 동작
 * - getConfig / getQueryWorkflow (internal)
 */
class IvmTest : DescribeSpec({

    afterEach {
        Ivm.reset()
    }

    describe("initialize / isInitialized / reset") {

        it("초기 상태 → isInitialized=false") {
            Ivm.isInitialized() shouldBe false
        }

        it("initialize 후 → isInitialized=true") {
            val ctx = IvmContext.builder().build()
            Ivm.initialize(ctx)

            Ivm.isInitialized() shouldBe true
        }

        it("reset 후 → isInitialized=false") {
            val ctx = IvmContext.builder().build()
            Ivm.initialize(ctx)
            Ivm.reset()

            Ivm.isInitialized() shouldBe false
        }

        it("initialize 시 context 저장") {
            val executor = mockk<DeployExecutor>()
            val ctx = IvmContext.builder().executor(executor).build()
            Ivm.initialize(ctx)

            Ivm.context().executor shouldBe executor
        }
    }

    describe("context()") {

        it("초기 상태 → EMPTY context") {
            val ctx = Ivm.context()
            ctx.executor shouldBe null
            ctx.queryWorkflow shouldBe null
        }

        it("initialize 후 → 설정된 context 반환") {
            val executor = mockk<DeployExecutor>()
            val ctx = IvmContext.builder()
                .executor(executor)
                .build()
            Ivm.initialize(ctx)

            Ivm.context().executor shouldBe executor
        }
    }

    describe("configure") {

        it("configure(IvmClientConfig) → config 변경 + client 캐시 무효화") {
            val config1 = IvmClientConfig.Builder().apply {
                baseUrl("http://first:8080")
            }.build()
            Ivm.configure(config1)

            Ivm.getConfig().baseUrl shouldBe "http://first:8080"

            val config2 = IvmClientConfig.Builder().apply {
                baseUrl("http://second:8080")
            }.build()
            Ivm.configure(config2)

            Ivm.getConfig().baseUrl shouldBe "http://second:8080"
        }

        it("configure DSL") {
            Ivm.configure {
                baseUrl("http://dsl-test:8080")
            }

            Ivm.getConfig().baseUrl shouldBe "http://dsl-test:8080"
        }
    }

    describe("product / brand / category DSL") {

        it("product DSL → DeployableContext 반환") {
            val ctx = Ivm.product {
                tenantId("tenant-1")
                sku("SKU-001")
                name("Test Product")
                price(10000)
            }

            ctx shouldNotBe null
        }

        it("brand DSL → DeployableContext 반환") {
            val ctx = Ivm.brand {
                tenantId("tenant-1")
                brandId("BRAND-001")
                name("Test Brand")
            }

            ctx shouldNotBe null
        }

        it("category DSL → DeployableContext 반환") {
            val ctx = Ivm.category {
                tenantId("tenant-1")
                categoryId("CAT-001")
                name("Test Category")
            }

            ctx shouldNotBe null
        }

        it("product DSL에서 필수 필드 누락 → IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                Ivm.product {
                    // tenantId 누락
                    sku("SKU-001")
                    name("Test")
                    price(1000)
                }
            }
        }
    }

    describe("client()") {

        it("client() → IvmClient 반환") {
            val client = Ivm.client()
            client shouldNotBe null
        }

        it("client() 연속 호출 → 동일 인스턴스 (캐싱)") {
            val client1 = Ivm.client()
            val client2 = Ivm.client()
            client1 shouldBe client2
        }

        it("configure 후 client() → 새 인스턴스") {
            val client1 = Ivm.client()
            Ivm.configure {
                baseUrl("http://new:8080")
            }
            val client2 = Ivm.client()
            // configure가 cachedClient = null 처리하므로 새 인스턴스
            client1 shouldNotBe client2
        }
    }

    describe("getQueryWorkflow") {

        it("초기 상태 → null") {
            Ivm.getQueryWorkflow() shouldBe null
        }
    }
})
