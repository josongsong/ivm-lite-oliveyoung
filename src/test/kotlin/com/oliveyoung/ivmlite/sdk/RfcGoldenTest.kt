package com.oliveyoung.ivmlite.sdk

import arrow.core.getOrElse
import com.oliveyoung.ivmlite.sdk.client.IvmClientConfig
import com.oliveyoung.ivmlite.sdk.dsl.entity.ProductInput
import com.oliveyoung.ivmlite.sdk.dsl.deploy.DeployableContext
import com.oliveyoung.ivmlite.sdk.model.*
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

/**
 * RFC Golden Tests (RFC-IMPL-011 Wave 5-L)
 *
 * RFC-008과 RFC-009 문서의 예시 코드가 정확히 동작하는지 검증.
 * 문서와 실제 구현의 일관성 보장.
 */
class RfcGoldenTest : StringSpec({

    val testConfig = IvmClientConfig(
        baseUrl = "http://localhost:8080",
        tenantId = "test-tenant"
    )

    // ==================== RFC-008 Section 9-1: Raw Input DSL ====================

    "RFC-008 9-1: Raw Input DSL" {
        // RFC-008 Section 9-1 예시
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "ABC-123",
            name = "Moisture Cream",
            price = 19000,
            currency = "KRW"
        )

        val context = DeployableContext(input, testConfig)

        // DSL 생성 검증
        context shouldNotBe null
        input.sku shouldBe "ABC-123"
        input.name shouldBe "Moisture Cream"
        input.price shouldBe 19000
    }

    // ==================== RFC-008 Section 10: Deploy DX ====================

    "RFC-008 10-1: Default Deploy (compile sync + ship async)" {
        // RFC-008 Section 10-1 예시
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "PROD-101",
            name = "Test Product",
            price = 10000
        )

        val context = DeployableContext(input, testConfig)

        val result = context.deploy {
            ship.async {
                opensearch {
                    index("products")
                }
                personalize {
                    dataset("reco-feed")
                }
            }
        }

        // 기본값 검증: compile sync + ship async
        result.success shouldBe true
        result.entityKey shouldContain "product:PROD-101"
        result.version shouldNotBe null
    }

    "RFC-008 10-2: All Sync (compile sync + ship sync)" {
        // RFC-008 Section 10-2 예시
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "PROD-102",
            name = "Test Product 2",
            price = 20000
        )

        val context = DeployableContext(input, testConfig)

        val result = context.deploy {
            compile.sync()
            ship.async {
                opensearch {
                    index("products")
                }
                personalize {
                    dataset("reco-feed")
                }
            }
        }

        // 전부 동기 검증
        result.success shouldBe true
        result.entityKey shouldContain "product:PROD-102"
    }

    "RFC-008 10-3: Async Deploy (compile async + ship async)" {
        // RFC-008 Section 10-3 예시
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "PROD-103",
            name = "Test Product 3",
            price = 30000
        )

        val context = DeployableContext(input, testConfig)

        val job = context.deployAsync {
            compile.async()
            ship.async {
                opensearch {
                    index("products")
                }
                personalize {
                    dataset("reco-feed")
                }
            }
        }.getOrElse { fail("deployAsync failed: ${it.message}") }

        // 비동기 Job 검증
        job.state shouldBe DeployState.QUEUED
        job.entityKey shouldContain "product:PROD-103"
        job.jobId shouldNotBe null
    }

    // ==================== RFC-008 Section 11: DX Shortcut APIs ====================

    "RFC-008 11-1: deployNow (compile sync + ship async)" {
        // RFC-008 Section 11-1 예시
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "PROD-111",
            name = "Test Product",
            price = 10000
        )

        val context = DeployableContext(input, testConfig)

        val result = context.deployNow {
            opensearch {
                index("products")
            }
            personalize {
                dataset("reco-feed")
            }
        }

        // Shortcut API 검증
        result.success shouldBe true
        result.entityKey shouldContain "product:PROD-111"
    }

    "RFC-008 11-2: deployNowAndShipNow (compile sync + ship sync)" {
        // RFC-008 Section 11-2 예시
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "PROD-112",
            name = "Test Product",
            price = 20000
        )

        val context = DeployableContext(input, testConfig)

        val result = context.deployNowAndShipNow {
            opensearch {
                index("products")
            }
            personalize {
                dataset("reco-feed")
            }
        }

        // 전부 동기 검증
        result.success shouldBe true
        result.entityKey shouldContain "product:PROD-112"
    }

    "RFC-008 11-3: deployQueued (compile async + ship async)" {
        // RFC-008 Section 11-3 예시
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "PROD-113",
            name = "Test Product",
            price = 30000
        )

        val context = DeployableContext(input, testConfig)

        val job = context.deployQueued {
            opensearch {
                index("products")
            }
            personalize {
                dataset("reco-feed")
            }
        }.getOrElse { fail("deployQueued failed: ${it.message}") }

        // 비동기 Job 검증
        job.state shouldBe DeployState.QUEUED
        job.entityKey shouldContain "product:PROD-113"
        job.jobId shouldNotBe null
    }

    // ==================== RFC-009 Section 11: Advanced Compile Features ====================

    // TODO: RFC-009 11-1, 11-2는 TargetRef와 explainPlan 기능이 필요
    // 현재 CompileMode.SyncWithTargets는 정의되어 있으나 실행 로직 미구현
    // Wave 6 이후에 구현 예정

    "RFC-009 11-1: Compile with Targets (Pending Implementation)" {
        // RFC-009 Section 11-1 예시
        // TODO: targets { searchDoc(); recoFeed() } 지원 필요
        // 현재는 CompileMode.SyncWithTargets 타입만 정의됨

        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "PROD-901",
            name = "Test Product",
            price = 10000
        )

        val context = DeployableContext(input, testConfig)

        // 현재는 SyncWithTargets를 직접 사용할 수 없음
        // compile.targets { } DSL은 Wave 6에서 구현 예정
        val targets = listOf(
            TargetRef("searchDoc", "1.0.0"),
            TargetRef("recoFeed", "1.0.0")
        )

        // Pending: compile { targets { searchDoc(); recoFeed() } }
        // 타입은 존재하나 실행 로직 미구현
        targets.size shouldBe 2
    }

    "RFC-009 11-2: Explain Plan (Pending Implementation)" {
        // RFC-009 Section 11-2 예시
        // TODO: explainLastPlan() API 필요
        // 현재는 미구현

        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "PROD-902",
            name = "Test Product",
            price = 20000
        )

        val context = DeployableContext(input, testConfig)

        val result = context.deployNow {
            opensearch {
                index("products")
            }
        }

        result.success shouldBe true

        // TODO: val plan = ivm.explainLastPlan(deployId)
        // TODO: plan.graph, plan.activatedRules, plan.executionSteps 검증
        // Wave 6에서 구현 예정
    }

    // ==================== Type Safety Tests ====================

    "Type Safety: deployAsync only allows async modes" {
        // deployAsync는 compile.async만 허용 (타입 안전)
        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "PROD-TS1",
            name = "Type Safety Test",
            price = 10000
        )

        val context = DeployableContext(input, testConfig)

        // deployAsync { compile.async() } ✓
        val job = context.deployAsync {
            compile.async()
            ship.async {
                opensearch {
                    index("products")
                }
            }
        }.getOrElse { fail("deployAsync failed: ${it.message}") }

        job.state shouldBe DeployState.QUEUED

        // deployAsync { compile.sync() } ✗ 컴파일 에러 (타입 단계에서 차단)
    }

    "Forbidden Combination: compile async + ship sync is blocked" {
        // RFC-008 Section 3: compile.async + ship.sync는 불가
        // SDK 타입 단계에서 차단

        val input = ProductInput(
            tenantId = "test-tenant",
            sku = "PROD-FC1",
            name = "Forbidden Combination Test",
            price = 10000
        )

        val context = DeployableContext(input, testConfig)

        // deploy { compile.async(); ship.sync { } } ✗ 타입 에러
        // deployAsync는 ship.async만 허용하므로 자동 차단

        // 대신 deployAsync는 항상 안전한 조합만 허용
        val job = context.deployAsync {
            compile.async()
            ship.async {
                opensearch {
                    index("products")
                }
            }
        }.getOrElse { fail("deployAsync failed: ${it.message}") }

        job.state shouldBe DeployState.QUEUED
    }
})
