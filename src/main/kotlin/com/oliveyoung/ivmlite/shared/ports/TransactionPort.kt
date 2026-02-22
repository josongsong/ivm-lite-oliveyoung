package com.oliveyoung.ivmlite.shared.ports

import com.oliveyoung.ivmlite.shared.domain.types.Result

/**
 * TransactionPort - 트랜잭션 관리 Port
 *
 * Hexagonal Architecture: Domain이 Infrastructure에 의존하지 않도록 Port 정의
 *
 * 구현체:
 * - ExposedTransactionAdapter: Exposed 기반 실제 트랜잭션
 * - NoOpTransactionAdapter: 테스트용 no-op
 */
interface TransactionPort {
    /**
     * 트랜잭션 실행
     *
     * @param block 트랜잭션 블록 (Result 반환)
     * @return Result<T>
     */
    suspend fun <T> execute(block: suspend () -> Result<T>): Result<T>
}
