package com.oliveyoung.ivmlite.tooling.application

import java.io.File

/**
 * pathsToImpactMap Gradle 실행용 main
 * ./gradlew pathsToImpactMap -Dpaths=paths.yaml -Doutput=impact-map-draft.yaml
 */
fun main(args: Array<String>) {
    val paths = args.findArg("--paths") ?: System.getProperty("paths") ?: "paths.yaml"
    val output = args.findArg("--output") ?: System.getProperty("output") ?: "impact-map-draft.yaml"

    println("PathsToImpactMap - impactMap 초안 생성 (product-schema-dx-proposal RFC 2.2)")
    println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    println("Paths:  $paths")
    println("Output: $output")
    println()

    val count = PathsToImpactMap.generate(File(paths), File(output))
    println("✅ ${count}개 경로 매핑 → $output")
}

private fun Array<String>.findArg(name: String): String? {
    val index = indexOf(name)
    return if (index >= 0 && index < size - 1) get(index + 1) else null
}
