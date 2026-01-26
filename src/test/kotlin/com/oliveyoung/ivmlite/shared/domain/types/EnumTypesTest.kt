package com.oliveyoung.ivmlite.shared.domain.types

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Enum Types SOTA 패턴 테스트
 * - @Serializable 지원
 * - toDbValue() / fromDbValue() 변환
 * - fromDbValueOrNull() safe 변환
 * - 잘못된 값 → ValidationError
 */
class EnumTypesTest : StringSpec({

    // ==================== SliceType ====================

    "SliceType.toDbValue - lowercase 변환" {
        SliceType.CORE.toDbValue() shouldBe "core"
        SliceType.JOINED.toDbValue() shouldBe "joined"
        SliceType.DERIVED.toDbValue() shouldBe "derived"
    }

    "SliceType.fromDbValue - 정상 변환 (case insensitive)" {
        SliceType.fromDbValue("core") shouldBe SliceType.CORE
        SliceType.fromDbValue("CORE") shouldBe SliceType.CORE
        SliceType.fromDbValue("Core") shouldBe SliceType.CORE
        SliceType.fromDbValue("joined") shouldBe SliceType.JOINED
        SliceType.fromDbValue("derived") shouldBe SliceType.DERIVED
    }

    "SliceType.fromDbValue - 잘못된 값 → ValidationError" {
        val ex = shouldThrow<DomainError.ValidationError> {
            SliceType.fromDbValue("unknown")
        }
        ex.field shouldBe "sliceType"
        ex.msg shouldBe "Unknown SliceType: unknown"
    }

    "SliceType.fromDbValueOrNull - 정상 변환" {
        SliceType.fromDbValueOrNull("core") shouldBe SliceType.CORE
        SliceType.fromDbValueOrNull("JOINED") shouldBe SliceType.JOINED
    }

    "SliceType.fromDbValueOrNull - 잘못된 값 → null" {
        SliceType.fromDbValueOrNull("invalid") shouldBe null
        SliceType.fromDbValueOrNull("") shouldBe null
    }

    // ==================== AggregateType ====================

    "AggregateType.toDbValue - lowercase 변환" {
        AggregateType.RAW_DATA.toDbValue() shouldBe "raw_data"
        AggregateType.SLICE.toDbValue() shouldBe "slice"
        AggregateType.CHANGESET.toDbValue() shouldBe "changeset"
    }

    "AggregateType.fromDbValue - 정상 변환 (case insensitive)" {
        AggregateType.fromDbValue("raw_data") shouldBe AggregateType.RAW_DATA
        AggregateType.fromDbValue("RAW_DATA") shouldBe AggregateType.RAW_DATA
        AggregateType.fromDbValue("slice") shouldBe AggregateType.SLICE
        AggregateType.fromDbValue("changeset") shouldBe AggregateType.CHANGESET
    }

    "AggregateType.fromDbValue - 잘못된 값 → ValidationError" {
        val ex = shouldThrow<DomainError.ValidationError> {
            AggregateType.fromDbValue("unknown")
        }
        ex.field shouldBe "aggregateType"
        ex.msg shouldBe "Unknown AggregateType: unknown"
    }

    "AggregateType.fromDbValueOrNull - 정상 변환" {
        AggregateType.fromDbValueOrNull("raw_data") shouldBe AggregateType.RAW_DATA
    }

    "AggregateType.fromDbValueOrNull - 잘못된 값 → null" {
        AggregateType.fromDbValueOrNull("invalid") shouldBe null
    }

    // ==================== OutboxStatus ====================

    "OutboxStatus.toDbValue - lowercase 변환" {
        OutboxStatus.PENDING.toDbValue() shouldBe "pending"
        OutboxStatus.PROCESSED.toDbValue() shouldBe "processed"
        OutboxStatus.FAILED.toDbValue() shouldBe "failed"
    }

    "OutboxStatus.fromDbValue - 정상 변환 (case insensitive)" {
        OutboxStatus.fromDbValue("pending") shouldBe OutboxStatus.PENDING
        OutboxStatus.fromDbValue("PENDING") shouldBe OutboxStatus.PENDING
        OutboxStatus.fromDbValue("processed") shouldBe OutboxStatus.PROCESSED
        OutboxStatus.fromDbValue("failed") shouldBe OutboxStatus.FAILED
    }

    "OutboxStatus.fromDbValue - 잘못된 값 → ValidationError" {
        val ex = shouldThrow<DomainError.ValidationError> {
            OutboxStatus.fromDbValue("unknown")
        }
        ex.field shouldBe "outboxStatus"
        ex.msg shouldBe "Unknown OutboxStatus: unknown"
    }

    "OutboxStatus.fromDbValueOrNull - 정상 변환" {
        OutboxStatus.fromDbValueOrNull("pending") shouldBe OutboxStatus.PENDING
    }

    "OutboxStatus.fromDbValueOrNull - 잘못된 값 → null" {
        OutboxStatus.fromDbValueOrNull("invalid") shouldBe null
    }

    // ==================== Roundtrip 테스트 ====================

    "SliceType - toDbValue → fromDbValue roundtrip" {
        SliceType.entries.forEach { original ->
            val dbValue = original.toDbValue()
            val restored = SliceType.fromDbValue(dbValue)
            restored shouldBe original
        }
    }

    "AggregateType - toDbValue → fromDbValue roundtrip" {
        AggregateType.entries.forEach { original ->
            val dbValue = original.toDbValue()
            val restored = AggregateType.fromDbValue(dbValue)
            restored shouldBe original
        }
    }

    "OutboxStatus - toDbValue → fromDbValue roundtrip" {
        OutboxStatus.entries.forEach { original ->
            val dbValue = original.toDbValue()
            val restored = OutboxStatus.fromDbValue(dbValue)
            restored shouldBe original
        }
    }
})
