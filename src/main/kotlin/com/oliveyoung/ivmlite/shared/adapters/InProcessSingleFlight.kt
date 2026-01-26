package com.oliveyoung.ivmlite.shared.adapters

import com.oliveyoung.ivmlite.shared.ports.SingleFlightPort
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM 로컬 single-flight. 동일 key는 mutex로 직렬화한다.
 * 유저 트래픽이 매우 크면 key-cardinality에 따라 캐시 전략이 필요함.
 */
class InProcessSingleFlight : SingleFlightPort {
    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun <T> run(key: String, block: suspend () -> T): T {
        val m = locks.computeIfAbsent(key) { Mutex() }
        return m.withLock { block() }
    }
}
