package com.oliveyoung.ivmlite.tooling.application

import java.io.File

/**
 * validateRawData Gradle 실행용 main
 * ./gradlew validateRawData -Dsample=.tmp/product/UA11279226.json
 */
fun main(args: Array<String>) {
    val sample = args.findArg("--sample") ?: System.getProperty("sample") ?: ".tmp/product/UA11279226.json"
    val ruleSetId = args.findArg("--ruleSet") ?: System.getProperty("ruleSet") ?: "ruleset.product.oliveyoung.v1"

    println("ValidateRawData - Pre-Ingest 검증 (product-schema-dx-proposal RFC 5.1)")
    println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    println("Sample: $sample")
    println()

    val file = File(sample)
    if (file.isDirectory) {
        val results: List<Pair<File, ValidateRawData.ValidationResult>> =
            ValidateRawData.validateDir(file, ruleSetId)
        var hasError = false
        for ((f, result) in results) {
            if (result.valid) {
                println("✅ ${f.name}")
            } else {
                hasError = true
                println("❌ ${f.name}")
                for (err in result.errors) println("   - $err")
            }
        }
        if (results.isEmpty()) {
            println("⚠️ JSON 파일 없음")
        }
        System.exit(if (hasError) 1 else 0)
    } else {
        val result = ValidateRawData.validate(file, ruleSetId)
        if (result.valid) {
            println("✅ 검증 통과")
            for (w in result.warnings) println("   ⚠ $w")
        } else {
            println("❌ 검증 실패")
            for (e in result.errors) println("   - $e")
            System.exit(1)
        }
    }
}

private fun Array<String>.findArg(name: String): String? {
    val index = indexOf(name)
    return if (index >= 0 && index < size - 1) get(index + 1) else null
}
