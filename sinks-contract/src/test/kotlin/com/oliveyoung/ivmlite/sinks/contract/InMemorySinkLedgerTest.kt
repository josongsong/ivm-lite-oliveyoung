package com.oliveyoung.ivmlite.sinks.contract

import arrow.core.getOrElse
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * InMemorySinkLedger 테스트 (SOTA-grade Idempotency)
 */
class InMemorySinkLedgerTest : DescribeSpec({

    describe("InMemorySinkLedger Idempotency") {

        it("✅ 첫 tryStart는 true 반환 (처리 허용)") {
            val ledger = InMemorySinkLedger()

            val result = ledger.tryStart(
                pluginId = "s3-sink",
                idempotencyKey = "idem-001",
                payloadDigest = "abc123",
                contractVersion = "1.0"
            )

            result.getOrElse { false } shouldBe true
            ledger.size() shouldBe 1
        }

        it("✅ 동일 키로 재시도 시 false 반환 (이미 완료)") {
            val ledger = InMemorySinkLedger()

            // 첫 처리
            ledger.tryStart("s3-sink", "idem-002", "digest1", "1.0")
            ledger.complete(
                "s3-sink",
                "idem-002",
                SinkResult(
                    idempotencyKey = "idem-002",
                    status = SinkStatus.SUCCESS,
                    processedAt = Instant.now().toString()
                )
            )

            // 재시도
            val retry = ledger.tryStart("s3-sink", "idem-002", "digest1", "1.0")

            retry.getOrElse { true } shouldBe false  // 재처리 방지
        }

        it("✅ 동일 키 + 다른 digest는 에러") {
            val ledger = InMemorySinkLedger()

            ledger.tryStart("s3-sink", "idem-003", "digest1", "1.0")

            // 다른 digest로 재시도
            val result = ledger.tryStart("s3-sink", "idem-003", "digest2", "1.0")

            result.isLeft() shouldBe true
            result.swap().getOrNull()
                .shouldBeInstanceOf<SinkError.NonRetryableError>()
        }

        it("✅ 실패 후 재시도는 허용") {
            val ledger = InMemorySinkLedger()

            ledger.tryStart("s3-sink", "idem-004", "digest1", "1.0")
            ledger.fail(
                "s3-sink",
                "idem-004",
                SinkError.RetryableError(
                    reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                    message = "Timeout"
                ),
                attemptCount = 1
            )

            // 재시도
            val retry = ledger.tryStart("s3-sink", "idem-004", "digest1", "1.0")

            retry.getOrElse { false } shouldBe true  // 재시도 허용
        }
    }

    describe("Replay Query") {

        it("✅ 에러 카테고리로 필터링") {
            val ledger = InMemorySinkLedger()

            // 실패 항목 추가
            ledger.tryStart("s3-sink", "idem-005", "digest1", "1.0")
            ledger.fail(
                "s3-sink",
                "idem-005",
                SinkError.RetryableError(
                    reasonCode = ErrorReasonCode.RATE_LIMIT_EXCEEDED,
                    message = "Rate limit"
                ),
                attemptCount = 1
            )

            // Query
            val result = ledger.queryForReplay(
                pluginId = "s3-sink",
                filters = ReplayFilters(
                    errorCategory = ErrorCategory.RETRYABLE
                ),
                limit = 10
            )

            val entries = result.getOrElse { emptyList() }
            entries.size shouldBe 1
            entries[0].lastError?.category shouldBe ErrorCategory.RETRYABLE
        }

        it("✅ Reason Code로 필터링") {
            val ledger = InMemorySinkLedger()

            // 두 개의 실패 항목
            ledger.tryStart("s3-sink", "idem-006", "digest1", "1.0")
            ledger.fail(
                "s3-sink",
                "idem-006",
                SinkError.RetryableError(
                    reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                    message = "Timeout"
                ),
                attemptCount = 1
            )

            ledger.tryStart("s3-sink", "idem-007", "digest2", "1.0")
            ledger.fail(
                "s3-sink",
                "idem-007",
                SinkError.NonRetryableError(
                    reasonCode = ErrorReasonCode.PERMISSION_DENIED,
                    message = "Access denied"
                ),
                attemptCount = 1
            )

            // NETWORK_TIMEOUT만 조회
            val result = ledger.queryForReplay(
                pluginId = "s3-sink",
                filters = ReplayFilters(
                    reasonCode = ErrorReasonCode.NETWORK_TIMEOUT
                ),
                limit = 10
            )

            val entries = result.getOrElse { emptyList() }
            entries.size shouldBe 1
            entries[0].lastError?.reasonCode shouldBe ErrorReasonCode.NETWORK_TIMEOUT
        }
    }

    describe("Complete/Fail Flow") {

        it("✅ complete() → status COMPLETED") {
            val ledger = InMemorySinkLedger()

            ledger.tryStart("s3-sink", "idem-008", "digest1", "1.0")
            ledger.complete(
                "s3-sink",
                "idem-008",
                SinkResult(
                    idempotencyKey = "idem-008",
                    status = SinkStatus.SUCCESS,
                    processedAt = Instant.now().toString(),
                    metadata = mapOf("s3_key" to "bucket/key")
                )
            )

            val status = ledger.getStatus("s3-sink", "idem-008")
                .getOrElse { null }

            status?.status shouldBe LedgerStatus.COMPLETED
            status?.resultMetadata?.get("s3_key") shouldBe "bucket/key"
        }

        it("✅ fail() → status FAILED + attemptCount 증가") {
            val ledger = InMemorySinkLedger()

            ledger.tryStart("s3-sink", "idem-009", "digest1", "1.0")
            ledger.fail(
                "s3-sink",
                "idem-009",
                SinkError.RetryableError(
                    reasonCode = ErrorReasonCode.NETWORK_TIMEOUT,
                    message = "Timeout after 5s"
                ),
                attemptCount = 3
            )

            val status = ledger.getStatus("s3-sink", "idem-009")
                .getOrElse { null }

            status?.status shouldBe LedgerStatus.FAILED
            status?.attemptCount shouldBe 3
            status?.lastError?.message shouldBe "Timeout after 5s"
        }
    }

    describe("Edge Cases") {

        it("✅ 존재하지 않는 항목 complete → 에러") {
            val ledger = InMemorySinkLedger()

            val result = ledger.complete(
                "s3-sink",
                "non-existent",
                SinkResult(
                    idempotencyKey = "non-existent",
                    status = SinkStatus.SUCCESS,
                    processedAt = Instant.now().toString()
                )
            )

            result.isLeft() shouldBe true
        }

        it("✅ getStatus() null 반환 (없는 항목)") {
            val ledger = InMemorySinkLedger()

            val result = ledger.getStatus("s3-sink", "non-existent")
                .getOrElse { null }

            result shouldBe null
        }
    }
})
