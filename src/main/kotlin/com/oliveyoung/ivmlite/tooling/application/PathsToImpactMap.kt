package com.oliveyoung.ivmlite.tooling.application

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * PathExpr 경로 목록 → impactMap 초안 생성 (product-schema-dx-proposal RFC 2.2)
 *
 * 추천은 "초안"으로만. 사람이 확정하는 흐름.
 * Product 도메인 휴리스틱: uaCode→CORE, options→PRICE, noticeInfo→NOTICE 등
 *
 * 실행:
 * ```bash
 * ./gradlew pathsToImpactMap -Dpaths=paths.yaml -Doutput=impact-map-draft.yaml
 * ```
 */
object PathsToImpactMap {

    // Product 도메인 경로 → Slice 휴리스틱 (RFC 1.1, 2.3.1 기준)
    private val pathToSliceRules = listOf(
        Regex("^uaCode$") to "CORE",
        Regex("^_meta") to "CORE",
        Regex("^_audit") to "CORE",
        Regex("^masterInfo\\.(?!packaging|standardCategory)") to "CORE",
        Regex("^onlineInfo\\.(?!orderQuantity|orderLimits|sellStatCode)") to "CORE",
        Regex("^options$") to "PRICE",
        Regex("^masterInfo\\.packaging") to "PRICE",
        Regex("^onlineInfo\\.(orderQuantity|orderLimits|sellStatCode)") to "INVENTORY",
        Regex("^reservationSaleInfo") to "INVENTORY",
        Regex("^shippingInfo") to "INVENTORY",
        Regex("^options\\[\\*\\]\\.(existYn|gdsStatCd)") to "INVENTORY",
        Regex("^thumbnailImages") to "MEDIA",
        Regex("^videoInfo") to "MEDIA",
        Regex("^detailThumbnails") to "MEDIA",
        Regex("^techSpecInfo") to "MEDIA",
        Regex("^options\\[\\*\\]\\.(optnImagePath|colrChipImagePath)") to "MEDIA",
        Regex("^displayCategories") to "CATEGORY",
        Regex("^masterInfo\\.standardCategory") to "CATEGORY",
        Regex("^emblemInfo") to "INDEX",
        Regex("^attributes") to "INDEX",
        Regex("^colorChipUseYn") to "INDEX",
        Regex("^additionalInfo") to "INDEX",
        Regex("^languageDisplayList") to "INDEX",
        Regex("^noticeInfo") to "NOTICE",
        Regex("^descriptionInfo") to "NOTICE",
        Regex("^globalInfo") to "NOTICE",
        Regex("^certifications") to "NOTICE",
        Regex("^safetyCertCategory") to "NOTICE",
        Regex("^associatedProducts") to "ASSOCIATED",
        Regex("^masterInfo\\.brand\\.code") to "ENRICHED",
    )

    /**
     * PathExpr 경로 목록을 읽어 impactMap 초안 생성
     *
     * @param pathsFile extractJsonPaths 출력 YAML (paths 리스트 포함)
     * @param outputFile impactMap 초안 출력
     * @return 매핑된 경로 수
     */
    fun generate(pathsFile: File, outputFile: File): Int {
        if (!pathsFile.exists()) {
            throw IllegalArgumentException("Paths file not found: ${pathsFile.path}")
        }

        val yaml = Yaml()
        @Suppress("UNCHECKED_CAST")
        val data = yaml.load<Map<String, Any>>(pathsFile.readText()) ?: emptyMap()
        val paths = (data["paths"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        val impactMap = mutableMapOf<String, MutableList<String>>()

        for (pathExpr in paths) {
            val slice = suggestSlice(pathExpr)
            val jsonPointer = "/" + pathExpr.replace("[*]", "/*").replace(".", "/")
            impactMap.getOrPut(slice) { mutableListOf() }.add(jsonPointer)
        }

        // 중복 제거 및 정렬
        val sortedImpactMap = impactMap.mapValues { (_, list) ->
            list.distinct().sorted()
        }.toSortedMap()

        outputFile.parentFile?.mkdirs()
        val outputYaml = Yaml(DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            indent = 2
        })
        val output = mapOf(
            "source" to pathsFile.name,
            "note" to "초안 — 사람이 확정 필요. options 충돌 규칙(RFC 3.2) 확인.",
            "impactMap" to sortedImpactMap,
        )
        outputFile.writeText(outputYaml.dump(output))

        return paths.size
    }

    private fun suggestSlice(pathExpr: String): String {
        for ((regex, slice) in pathToSliceRules) {
            if (regex.containsMatchIn(pathExpr)) return slice
        }
        return "CORE" // 기본값
    }

}
