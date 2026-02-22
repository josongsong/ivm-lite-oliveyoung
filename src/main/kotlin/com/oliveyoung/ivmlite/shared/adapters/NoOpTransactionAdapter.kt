package com.oliveyoung.ivmlite.shared.adapters

import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.ports.TransactionPort

/**
 * NoOpTransactionAdapter - 테스트용 트랜잭션 어댑터
 *
 * 실제 트랜잭션 없이 블록을 바로 실행
 */
class NoOpTransactionAdapter : TransactionPort {
    override suspend fun <T> execute(block: suspend () -> Result<T>): Result<T> = block()
}
