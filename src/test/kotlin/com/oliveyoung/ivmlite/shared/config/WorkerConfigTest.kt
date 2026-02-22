package com.oliveyoung.ivmlite.shared.config

import com.oliveyoung.ivmlite.shared.domain.types.AggregateType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * WorkerConfig 단위 테스트
 *
 * 커버리지:
 * - 기본값 확인
 * - resolvedAggregateTypes: topics 우선, aggregateTypes fallback, null 반환
 */
class WorkerConfigTest : DescribeSpec({

    describe("기본값") {

        it("default config") {
            val config = WorkerConfig()

            config.enabled shouldBe true
            config.pollIntervalMs shouldBe 100
            config.idlePollIntervalMs shouldBe 1000
            config.batchSize shouldBe 100
            config.maxBackoffMs shouldBe 30_000
            config.backoffMultiplier shouldBe 2.0
            config.jitterFactor shouldBe 0.1
            config.shutdownTimeoutMs shouldBe 10_000
            config.aggregateTypes shouldBe null
            config.topics shouldBe null
        }
    }

    describe("resolvedAggregateTypes") {

        it("topics도 aggregateTypes도 null → null") {
            val config = WorkerConfig()
            config.resolvedAggregateTypes() shouldBe null
        }

        it("topics 설정 → topics에서 AggregateType 추론") {
            val config = WorkerConfig(topics = listOf("ivm.events.raw_data", "ivm.events.slice"))
            val resolved = config.resolvedAggregateTypes()

            resolved shouldBe listOf(AggregateType.RAW_DATA, AggregateType.SLICE)
        }

        it("topics에 알 수 없는 토픽 → 필터링") {
            val config = WorkerConfig(topics = listOf("ivm.events.unknown"))
            config.resolvedAggregateTypes() shouldBe null
        }

        it("topics 우선 (aggregateTypes 무시)") {
            val config = WorkerConfig(
                topics = listOf("ivm.events.slice"),
                aggregateTypes = listOf("RAW_DATA")
            )
            val resolved = config.resolvedAggregateTypes()

            resolved shouldBe listOf(AggregateType.SLICE) // topics 우선
        }

        it("aggregateTypes만 설정 → aggregateTypes에서 파싱") {
            val config = WorkerConfig(aggregateTypes = listOf("RAW_DATA", "CHANGESET"))
            val resolved = config.resolvedAggregateTypes()

            resolved shouldBe listOf(AggregateType.RAW_DATA, AggregateType.CHANGESET)
        }

        it("aggregateTypes에 잘못된 값 → 필터링") {
            val config = WorkerConfig(aggregateTypes = listOf("INVALID", "RAW_DATA"))
            val resolved = config.resolvedAggregateTypes()

            resolved shouldBe listOf(AggregateType.RAW_DATA)
        }

        it("aggregateTypes 모두 잘못된 값 → null") {
            val config = WorkerConfig(aggregateTypes = listOf("INVALID"))
            config.resolvedAggregateTypes() shouldBe null
        }

        it("빈 topics → null (topics 비어있으면 aggregateTypes 무시됨)") {
            val config = WorkerConfig(topics = emptyList())
            // topics가 비어있으면 null이 아니므로 topics 분기 진입 → 빈 결과 → null
            config.resolvedAggregateTypes() shouldBe null
        }
    }
})
