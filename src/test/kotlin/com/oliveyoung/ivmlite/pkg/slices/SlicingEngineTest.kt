package com.oliveyoung.ivmlite.pkg.slices

import com.oliveyoung.ivmlite.pkg.contracts.domain.*
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.pkg.rawdata.domain.RawDataRecord
import com.oliveyoung.ivmlite.pkg.slices.domain.SlicingEngine
import com.oliveyoung.ivmlite.shared.domain.determinism.Hashing
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.Result
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import com.oliveyoung.ivmlite.shared.domain.types.SliceType as SharedSliceType

/**
 * RFC-IMPL-010 Phase D-3: SlicingEngine RuleSet 기반 슬라이싱 TDD
 *
 * 엣지/코너 케이스 전수:
 * 1. PassThrough 규칙 → 전체 payload 복사
 * 2. MapFields 규칙 → 지정 필드만 매핑
 * 3. 다중 SliceDefinition → 여러 SliceRecord 생성
 * 4. 동일 입력 → 동일 slice_hash (결정성)
 * 5. RuleSet 로드 실패 → Err 전파
 * 6. 빈 payload → 빈 slice (에러 아님)
 * 7. 중첩 JSON path 매핑 ($.nested.field)
 * 8. 배열 필드 매핑 ($.items[*].name)
 */
class SlicingEngineTest : StringSpec({

    // ==================== 1. PassThrough 규칙 → 전체 payload 복사 ====================

    "PassThrough [*] → 전체 payload 복사" {
        val rawData = createRawData(payload = """{"name":"product","price":1000}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.PassThrough(fields = listOf("*")),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices.size shouldBe 1
        slices[0].data shouldBe """{"name":"product","price":1000}"""
        slices[0].sliceType shouldBe SharedSliceType.CORE
    }

    // ==================== 2. MapFields 규칙 → 지정 필드만 매핑 ====================

    "MapFields → 지정 필드만 매핑" {
        val rawData = createRawData(payload = """{"name":"product","price":1000,"stock":50}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.PRICE,
                    buildRules = SliceBuildRules.MapFields(
                        mappings = mapOf(
                            "price" to "price",
                        ),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices.size shouldBe 1
        slices[0].sliceType shouldBe SharedSliceType.PRICE
        slices[0].data shouldBe """{"price":1000}"""
    }

    "MapFields → 필드 이름 변환" {
        val rawData = createRawData(payload = """{"product_name":"apple","product_price":500}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(
                        mappings = mapOf(
                            "product_name" to "name",
                            "product_price" to "price",
                        ),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe """{"name":"apple","price":500}"""
    }

    // ==================== 3. 다중 SliceDefinition → 여러 SliceRecord 생성 ====================

    "다중 SliceDefinition → 여러 SliceRecord 생성" {
        val rawData = createRawData(payload = """{"name":"product","price":1000,"stock":50}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.PassThrough(fields = listOf("*")),
                ),
                SliceDefinition(
                    type = SharedSliceType.PRICE,
                    buildRules = SliceBuildRules.MapFields(mapOf("price" to "price")),
                ),
                SliceDefinition(
                    type = SharedSliceType.INVENTORY,
                    buildRules = SliceBuildRules.MapFields(mapOf("stock" to "stock")),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices.size shouldBe 3
        slices.map { it.sliceType.name }.toSet() shouldBe setOf("CORE", "PRICE", "INVENTORY")
    }

    // ==================== 4. 동일 입력 → 동일 slice_hash (결정성) ====================

    "결정성: 동일 입력 → 동일 slice_hash" {
        val rawData1 = createRawData(payload = """{"name":"test","value":100}""")
        val rawData2 = createRawData(payload = """{"name":"test","value":100}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.PassThrough(fields = listOf("*")),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result1 = runBlocking { engine.slice(rawData1, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }
        val result2 = runBlocking { engine.slice(rawData2, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result1.shouldBeInstanceOf<Result.Ok<*>>()
        result2.shouldBeInstanceOf<Result.Ok<*>>()
        val hash1 = (result1 as Result.Ok).value.slices[0].hash
        val hash2 = (result2 as Result.Ok).value.slices[0].hash
        hash1 shouldBe hash2
    }

    "결정성: MapFields도 동일 hash" {
        val rawData1 = createRawData(payload = """{"a":1,"b":2}""")
        val rawData2 = createRawData(payload = """{"a":1,"b":2}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(mapOf("a" to "x", "b" to "y")),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result1 = runBlocking { engine.slice(rawData1, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }
        val result2 = runBlocking { engine.slice(rawData2, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        val hash1 = (result1 as Result.Ok).value.slices[0].hash
        val hash2 = (result2 as Result.Ok).value.slices[0].hash
        hash1 shouldBe hash2
    }

    // ==================== 5. RuleSet 로드 실패 → Err 전파 ====================

    "RuleSet 로드 실패 → Err 전파" {
        val rawData = createRawData(payload = """{"name":"test"}""")
        val registry = MockContractRegistry(null) // RuleSet 없음
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.NotFoundError>()
    }

    // ==================== 6. 빈 payload → 빈 slice (에러 아님) ====================

    "빈 payload → 빈 slice (에러 아님)" {
        val rawData = createRawData(payload = """{}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.PassThrough(fields = listOf("*")),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices.size shouldBe 1
        slices[0].data shouldBe """{}"""
        slices[0].hash shouldBe Hashing.sha256Tagged("""{}""")
    }

    "빈 payload + MapFields → 빈 객체" {
        val rawData = createRawData(payload = """{}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.PRICE,
                    buildRules = SliceBuildRules.MapFields(mapOf("price" to "price")),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe """{}"""
    }

    // ==================== 7. 중첩 JSON path 매핑 ($.nested.field) ====================

    "중첩 필드 매핑 (nested.field)" {
        val rawData = createRawData(payload = """{"product":{"name":"apple","price":500}}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(
                        mapOf("product.name" to "name"),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe """{"name":"apple"}"""
    }

    "깊은 중첩 필드 매핑 (a.b.c.d)" {
        val rawData = createRawData(payload = """{"a":{"b":{"c":{"d":"deep"}}}}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(
                        mapOf("a.b.c.d" to "value"),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe """{"value":"deep"}"""
    }

    // ==================== 8. 배열 필드 매핑 ($.items[*].name) ====================

    "배열 전체 매핑 (items)" {
        val rawData = createRawData(payload = """{"items":[{"name":"a"},{"name":"b"}]}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(
                        mapOf("items" to "items"),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe """{"items":[{"name":"a"},{"name":"b"}]}"""
    }

    "배열 내부 필드 추출 (items[*].name)" {
        val rawData = createRawData(payload = """{"items":[{"name":"a","price":100},{"name":"b","price":200}]}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(
                        mapOf("items[*].name" to "names"),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe """{"names":["a","b"]}"""
    }

    // ==================== 9. 필드 경로 엣지 케이스 ====================

    "존재하지 않는 필드 참조 → 빈 객체" {
        val rawData = createRawData(payload = """{"title":"Test"}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(
                        mapOf("nonexistent" to "result"),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe "{}"
    }

    "중첩 경로 중간에 null → 빈 객체" {
        val rawData = createRawData(payload = """{"product":null}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(
                        mapOf("product.name" to "name"),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe "{}"
    }

    "배열 아닌데 [*] 사용 → 빈 객체" {
        val rawData = createRawData(payload = """{"item":{"name":"test"}}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(
                        mapOf("item[*].name" to "names"),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe "{}"
    }

    "매우 깊은 중첩 (20 levels) → 정상 처리" {
        val deepJson = buildString {
            append("{")
            repeat(20) { i -> append("\"level$i\":{") }
            append("\"value\":\"deep\"")
            repeat(20) { append("}") }
            append("}")
        }
        val deepPath = (0..19).joinToString(".") { "level$it" } + ".value"

        val rawData = createRawData(payload = deepJson)
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(
                        mapOf(deepPath to "result"),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe """{"result":"deep"}"""
    }

    "malformed JSON payload → InvariantViolation" {
        val rawData = createRawData(payload = """{invalid json}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.PassThrough(listOf("*")),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking {
            engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0")))
        }

        result.shouldBeInstanceOf<Result.Err>()
        (result as Result.Err).error.shouldBeInstanceOf<DomainError.InvariantViolation>()
    }

    // ==================== 추가 엣지 케이스 ====================

    "빈 문자열 payload → 빈 객체" {
        val rawData = createRawData(payload = "")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.PassThrough(listOf("*")),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe "{}"
    }

    "PassThrough 일부 필드 선택" {
        val rawData = createRawData(payload = """{"a":1,"b":2,"c":3}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.PassThrough(fields = listOf("a", "c")),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe """{"a":1,"c":3}"""
    }

    "PassThrough 존재하지 않는 필드 → 빈 객체" {
        val rawData = createRawData(payload = """{"name":"test"}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.PassThrough(fields = listOf("nonexistent")),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe "{}"
    }

    "빈 배열 매핑 → 빈 배열" {
        val rawData = createRawData(payload = """{"items":[]}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(
                        mapOf("items[*].name" to "names"),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe """{"names":[]}"""
    }

    "배열 내부 중첩 필드 (items[*].product.name)" {
        val rawData = createRawData(
            payload = """{"items":[{"product":{"name":"a","price":100}},{"product":{"name":"b","price":200}}]}""",
        )
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(
                        mapOf("items[*].product.name" to "names"),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe """{"names":["a","b"]}"""
    }

    "items[*] 로만 끝나는 경로 → 배열 전체" {
        val rawData = createRawData(payload = """{"items":[{"name":"a"},{"name":"b"}]}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(
                        mapOf("items[*]" to "allItems"),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe """{"allItems":[{"name":"a"},{"name":"b"}]}"""
    }

    "null 값 필드 → 무시" {
        val rawData = createRawData(payload = """{"name":"test","nullField":null}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(
                        mapOf("name" to "name", "nullField" to "result"),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        slices[0].data shouldBe """{"name":"test"}"""
    }

    "순서 다른 JSON → 동일 hash (결정성)" {
        val rawData1 = createRawData(payload = """{"z":3,"a":1,"m":2}""")
        val rawData2 = createRawData(payload = """{"a":1,"m":2,"z":3}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.PassThrough(fields = listOf("*")),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result1 = runBlocking { engine.slice(rawData1, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }
        val result2 = runBlocking { engine.slice(rawData2, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result1.shouldBeInstanceOf<Result.Ok<*>>()
        result2.shouldBeInstanceOf<Result.Ok<*>>()
        val hash1 = (result1 as Result.Ok).value.slices[0].hash
        val hash2 = (result2 as Result.Ok).value.slices[0].hash
        hash1 shouldBe hash2
    }

    "매우 큰 배열 (100개) → 정상 처리" {
        val items = (1..100).joinToString(",") { """{"id":$it,"name":"item$it"}""" }
        val rawData = createRawData(payload = """{"items":[$items]}""")
        val ruleSet = createRuleSet(
            slices = listOf(
                SliceDefinition(
                    type = SharedSliceType.CORE,
                    buildRules = SliceBuildRules.MapFields(
                        mapOf("items[*].id" to "ids"),
                    ),
                ),
            ),
        )
        val registry = MockContractRegistry(ruleSet)
        val engine = SlicingEngine(registry)

        val result = runBlocking { engine.slice(rawData, ContractRef("ruleset.v1", SemVer.parse("1.0.0"))) }

        result.shouldBeInstanceOf<Result.Ok<*>>()
        val slices = (result as Result.Ok<SlicingEngine.SlicingResult>).value.slices
        val ids = slices[0].data
        ids.contains("\"ids\":[1,2,") shouldBe true
        ids.contains(",99,100]") shouldBe true
    }
})

// ==================== Helper Functions ====================

private fun createRawData(
    tenantId: String = "tenant-1",
    entityKey: String = "entity-1",
    version: Long = 1L,
    payload: String,
): RawDataRecord {
    val payloadHash = Hashing.sha256Tagged(payload)
    return RawDataRecord(
        tenantId = TenantId(tenantId),
        entityKey = EntityKey(entityKey),
        version = version,
        schemaId = "schema.product.v1",
        schemaVersion = SemVer.parse("1.0.0"),
        payload = payload,
        payloadHash = payloadHash,
    )
}

private fun createRuleSet(
    id: String = "ruleset.v1",
    version: String = "1.0.0",
    entityType: String = "product",
    slices: List<SliceDefinition>,
): RuleSetContract {
    return RuleSetContract(
        meta = ContractMeta(
            kind = "RuleSet",
            id = id,
            version = SemVer.parse(version),
            status = ContractStatus.ACTIVE,
        ),
        entityType = entityType,
        impactMap = emptyMap(),
        joins = emptyList(),
        slices = slices,
    )
}

private class MockContractRegistry(
    private val ruleSet: RuleSetContract?,
) : ContractRegistryPort {
    override suspend fun loadChangeSetContract(ref: ContractRef): Result<ChangeSetContract> {
        throw NotImplementedError("Not needed for this test")
    }

    override suspend fun loadJoinSpecContract(ref: ContractRef): Result<JoinSpecContract> {
        throw NotImplementedError("Not needed for this test")
    }

    override suspend fun loadInvertedIndexContract(ref: ContractRef): Result<InvertedIndexContract> {
        throw NotImplementedError("Not needed for this test")
    }

    override suspend fun loadRuleSetContract(ref: ContractRef): Result<RuleSetContract> {
        return if (ruleSet != null) {
            Result.Ok(ruleSet)
        } else {
            Result.Err(DomainError.NotFoundError("RuleSet", ref.id))
        }
    }

    override suspend fun loadViewDefinitionContract(ref: ContractRef): Result<ViewDefinitionContract> {
        throw NotImplementedError("Not needed for this test")
    }
}
