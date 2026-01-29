package com.oliveyoung.ivmlite.shared.config

import java.io.File

/**
 * .env 파일 로더
 *
 * 애플리케이션 시작 시 호출하여 .env 파일의 환경변수를 System Property로 설정.
 * Hoplite가 ${VAR} 플레이스홀더 해결 시 참조할 수 있도록 함.
 *
 * 우선순위: .env 파일 > 시스템 환경변수 (Hoplite의 SystemProperty 우선 설정과 함께 작동)
 *
 * NOTE: dotenv 라이브러리는 시스템 환경변수와 병합하므로, 직접 파싱하여 무조건 덮어씀
 */
object DotenvLoader {

    @Volatile
    private var loaded = false

    /**
     * .env 파일 로드 및 System Property 설정
     * 중복 호출 시 무시 (idempotent)
     */
    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                // .env 파일을 직접 파싱 (환경변수 병합 없이)
                val envFile = File(".env")
                if (envFile.exists()) {
                    envFile.readLines()
                        .filter { it.isNotBlank() && !it.startsWith("#") }
                        .mapNotNull { line ->
                            val idx = line.indexOf('=')
                            if (idx > 0) {
                                val key = line.substring(0, idx).trim()
                                var value = line.substring(idx + 1).trim()
                                // 따옴표 제거 (single/double quotes)
                                if ((value.startsWith("'") && value.endsWith("'")) ||
                                    (value.startsWith("\"") && value.endsWith("\""))) {
                                    value = value.substring(1, value.length - 1)
                                }
                                key to value
                            } else null
                        }
                        .forEach { (key, value) ->
                            // .env 파일의 값을 항상 System Property로 설정
                            // Hoplite가 System Property를 환경변수보다 우선함
                            System.setProperty(key, value)
                        }
                }
                loaded = true
            } catch (e: Exception) {
                // .env 로드 실패 시 무시 (환경변수만 사용)
            }
        }
    }
}
