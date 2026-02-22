package com.oliveyoung.ivmlite.shared.domain.types

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Topic / TopicConfig 단위 테스트
 *
 * 커버리지:
 * - Topic enum: toTopicName, fromAggregateType, fromTopicName, toAggregateType, allTopicNames
 * - TopicConfig: topicNames, aggregateTypes, all, of, fromTopicNames, fromAggregateTypes
 */
class TopicTest : DescribeSpec({

    describe("Topic enum") {

        it("toTopicName - 기본 prefix") {
            Topic.RAW_DATA.toTopicName() shouldBe "ivm.events.raw_data"
            Topic.SLICE.toTopicName() shouldBe "ivm.events.slice"
            Topic.CHANGESET.toTopicName() shouldBe "ivm.events.changeset"
        }

        it("toTopicName - 커스텀 prefix") {
            Topic.RAW_DATA.toTopicName("custom") shouldBe "custom.events.raw_data"
        }

        it("fromAggregateType") {
            Topic.fromAggregateType(AggregateType.RAW_DATA) shouldBe Topic.RAW_DATA
            Topic.fromAggregateType(AggregateType.SLICE) shouldBe Topic.SLICE
            Topic.fromAggregateType(AggregateType.CHANGESET) shouldBe Topic.CHANGESET
        }

        it("fromAggregateType - VIEW → NoSuchElementException (VIEW는 Topic에 없음)") {
            shouldThrow<NoSuchElementException> {
                Topic.fromAggregateType(AggregateType.VIEW)
            }
        }

        it("fromTopicName - 정상 파싱") {
            Topic.fromTopicName("ivm.events.raw_data") shouldBe Topic.RAW_DATA
            Topic.fromTopicName("ivm.events.slice") shouldBe Topic.SLICE
            Topic.fromTopicName("ivm.events.changeset") shouldBe Topic.CHANGESET
        }

        it("fromTopicName - 커스텀 prefix도 동작") {
            Topic.fromTopicName("custom.events.raw_data") shouldBe Topic.RAW_DATA
        }

        it("fromTopicName - 알 수 없는 suffix → null") {
            Topic.fromTopicName("ivm.events.unknown") shouldBe null
        }

        it("toAggregateType") {
            Topic.toAggregateType("ivm.events.raw_data") shouldBe AggregateType.RAW_DATA
            Topic.toAggregateType("ivm.events.slice") shouldBe AggregateType.SLICE
            Topic.toAggregateType("ivm.events.unknown") shouldBe null
        }

        it("allTopicNames") {
            val names = Topic.allTopicNames()
            names.size shouldBe 3
            names shouldBe listOf("ivm.events.raw_data", "ivm.events.slice", "ivm.events.changeset")
        }

        it("allTopicNames - 커스텀 prefix") {
            val names = Topic.allTopicNames("prod")
            names shouldBe listOf("prod.events.raw_data", "prod.events.slice", "prod.events.changeset")
        }

        it("aggregateType 매핑") {
            Topic.RAW_DATA.aggregateType shouldBe AggregateType.RAW_DATA
            Topic.SLICE.aggregateType shouldBe AggregateType.SLICE
            Topic.CHANGESET.aggregateType shouldBe AggregateType.CHANGESET
        }
    }

    describe("TopicConfig") {

        it("all() → 모든 토픽") {
            val config = TopicConfig.all()
            config.topics shouldBe null
            config.topicNames.size shouldBe 3
            config.aggregateTypes shouldBe null
        }

        it("of(RAW_DATA) → 단일 토픽") {
            val config = TopicConfig.of(Topic.RAW_DATA)
            config.topics shouldBe listOf(Topic.RAW_DATA)
            config.topicNames shouldBe listOf("ivm.events.raw_data")
            config.aggregateTypes shouldBe listOf(AggregateType.RAW_DATA)
        }

        it("of(RAW_DATA, SLICE) → 복수 토픽") {
            val config = TopicConfig.of(Topic.RAW_DATA, Topic.SLICE)
            config.topicNames shouldBe listOf("ivm.events.raw_data", "ivm.events.slice")
        }

        it("fromTopicNames → Topic 리스트 파싱") {
            val config = TopicConfig.fromTopicNames(listOf("ivm.events.raw_data", "ivm.events.slice"))
            config.topics shouldNotBe null
            config.topics!!.size shouldBe 2
        }

        it("fromTopicNames - 알 수 없는 토픽명 → 필터링") {
            val config = TopicConfig.fromTopicNames(listOf("ivm.events.unknown"))
            config.topics shouldBe null // 모두 필터링되면 null
        }

        it("fromAggregateTypes") {
            val config = TopicConfig.fromAggregateTypes(listOf(AggregateType.SLICE, AggregateType.CHANGESET))
            config.topics shouldBe listOf(Topic.SLICE, Topic.CHANGESET)
            config.topicNames shouldBe listOf("ivm.events.slice", "ivm.events.changeset")
        }

        it("커스텀 prefix") {
            val config = TopicConfig.of(Topic.RAW_DATA, prefix = "prod")
            config.topicNames shouldBe listOf("prod.events.raw_data")
        }
    }
})
