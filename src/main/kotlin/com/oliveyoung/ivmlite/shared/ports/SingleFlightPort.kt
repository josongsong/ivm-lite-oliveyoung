package com.oliveyoung.ivmlite.shared.ports

/**
 * 동일 key에 대한 동시 실행을 1회로 수렴시키는 single-flight.
 * v4: JVM 프로세스 로컬 구현으로 시작하고, 필요 시 분산락으로 승격한다.
 */
interface SingleFlightPort {
    suspend fun <T> run(key: String, block: suspend () -> T): T
}
