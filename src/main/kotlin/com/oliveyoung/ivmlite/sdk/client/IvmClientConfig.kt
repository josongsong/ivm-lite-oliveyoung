package com.oliveyoung.ivmlite.sdk.client

import java.time.Duration

data class IvmClientConfig(
    val baseUrl: String = "http://localhost:8080",
    val tenantId: String? = null,
    val timeout: Duration = Duration.ofSeconds(30),
    val defaultSinks: List<String> = listOf("opensearch")
) {
    companion object {
        /**
         * 전역 설정 (Ivm.configure()로 설정됨)
         * 
         * ViewRef.query() 등에서 사용
         */
        @Volatile
        var global: IvmClientConfig = IvmClientConfig()
            internal set
    }
    
    class Builder {
        private var baseUrl: String = "http://localhost:8080"
        private var tenantId: String? = null
        private var timeout: Duration = Duration.ofSeconds(30)
        private var defaultSinks: List<String> = listOf("opensearch")

        fun baseUrl(value: String) {
            baseUrl = value
        }

        fun tenantId(value: String) {
            tenantId = value
        }

        fun timeout(value: Duration) {
            timeout = value
        }

        fun defaultSinks(vararg sinks: String) {
            defaultSinks = sinks.toList()
        }

        internal fun build(): IvmClientConfig = IvmClientConfig(baseUrl, tenantId, timeout, defaultSinks)
    }
}
