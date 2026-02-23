package com.oliveyoung.ivmlite.tooling.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * JSON 샘플에서 PathExpr 형식 경로 추출 (product-schema-dx-proposal RFC 2.2)
 *
 * 출력 형식: options[*].gdsSelprcUprc, displayCategories[*].sclsCtgrNo
 * JSON Pointer(/options/3/price)가 아닌 PathExpr 사용 — ruleset 작성용
 *
 * 실행:
 * ```bash
 * ./gradlew extractJsonPaths -Dsample=.tmp/product/UA30953620.json -Doutput=paths.yaml
 * ```
 */
object ExtractJsonPaths {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    /**
     * JSON 파일에서 모든 leaf 경로를 PathExpr 형식으로 추출
     *
     * @param sampleFile JSON 샘플 파일
     * @param outputFile 출력 YAML 파일 (paths 리스트)
     * @return 추출된 경로 수
     */
    fun extract(sampleFile: File, outputFile: File): Int {
        if (!sampleFile.exists()) {
            throw IllegalArgumentException("Sample file not found: ${sampleFile.path}")
        }

        val root = mapper.readTree(sampleFile.readText())
        val paths = mutableSetOf<String>()

        collectPathExpr(root, "", paths)

        val sortedPaths = paths.sorted()

        outputFile.parentFile?.mkdirs()
        val yaml = createYaml()
        val output = mapOf(
            "source" to sampleFile.name,
            "pathExprFormat" to "options[*].field (배열은 [*], 인덱스 포함 JSON Pointer 아님)",
            "paths" to sortedPaths,
        )
        outputFile.writeText(yaml.dump(output))

        return sortedPaths.size
    }

    /**
     * JsonNode를 재귀 순회하며 leaf 경로를 PathExpr 형식으로 수집
     */
    private fun collectPathExpr(node: JsonNode, prefix: String, out: MutableSet<String>) {
        when {
            node.isObject -> {
                node.fields().forEachRemaining { (key, value) ->
                    val newPrefix = if (prefix.isEmpty()) key else "$prefix.$key"
                    collectPathExpr(value, newPrefix, out)
                }
            }
            node.isArray -> {
                // 배열: [*] 와일드카드 사용 (인덱스 포함하지 않음)
                node.forEach { element ->
                    val arrayPrefix = if (prefix.isEmpty()) "[*]" else "$prefix[*]"
                    collectPathExpr(element, arrayPrefix, out)
                }
            }
            else -> {
                // leaf: primitive 값
                out.add(prefix)
            }
        }
    }

    private fun createYaml(): Yaml {
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            indent = 2
        }
        return Yaml(options)
    }
}

/**
 * Gradle 실행용 main
 * ./gradlew extractJsonPaths -Dsample=.tmp/product/UA11279226.json -Doutput=paths.yaml
 */
fun main(args: Array<String>) {
    val sample = args.findArg("--sample") ?: System.getProperty("sample") ?: ".tmp/product/UA11279226.json"
    val output = args.findArg("--output") ?: System.getProperty("output") ?: "paths.yaml"

    println("ExtractJsonPaths - PathExpr 경로 추출 (product-schema-dx-proposal RFC 2.2)")
    println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    println("Sample: $sample")
    println("Output: $output")
    println()

    val count = ExtractJsonPaths.extract(File(sample), File(output))
    println("✅ ${count}개 경로 추출 → $output")
}

private fun Array<String>.findArg(name: String): String? {
    val index = indexOf(name)
    return if (index >= 0 && index < size - 1) get(index + 1) else null
}
