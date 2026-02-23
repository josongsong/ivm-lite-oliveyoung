package com.oliveyoung.ivmlite.sinks.contract

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * Sink 직렬화 정책 (LOCK)
 *
 * RFC-017: Sink Plugin Architecture
 *
 * Forward-Compatibility 보장:
 * - ignoreUnknownKeys = true: 신규 필드 무시
 * - explicitNulls = false: null 생략
 * - encodeDefaults = false: 기본값 생략
 */
object SinkJson {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        prettyPrint = false
    }
}
