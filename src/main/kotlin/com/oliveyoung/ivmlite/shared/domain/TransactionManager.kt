package com.oliveyoung.ivmlite.shared.domain

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.sql.Connection

/**
 * TransactionManager - DB 트랜잭션 관리
 *
 * Exposed 기반 트랜잭션 래퍼:
 * - 원자성 보장 (All-or-Nothing)
 * - 자동 롤백 (에러 시)
 * - Result 통합
 * - 코루틴 네이티브 (runBlocking 불필요)
 */
class TransactionManager(
    private val database: Database
) {
    private val logger = LoggerFactory.getLogger(TransactionManager::class.java)

    /**
     * 트랜잭션 실행
     *
     * @param isolationLevel 격리 수준 (기본: READ_COMMITTED)
     * @param block 트랜잭션 블록
     * @return 실행 결과 (성공 또는 에러)
     */
    suspend fun <T> transaction(
        isolationLevel: Int = Connection.TRANSACTION_READ_COMMITTED,
        block: suspend () -> Result<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            newSuspendedTransaction(Dispatchers.IO, database, transactionIsolation = isolationLevel) {
                when (val blockResult = block()) {
                    is Result.Ok -> blockResult
                    is Result.Err -> {
                        rollback()
                        throw TransactionException(blockResult.error)
                    }
                }
            }
        } catch (e: TransactionException) {
            logger.warn("Transaction failed: {}", e.domainError)
            Result.Err(e.domainError)
        } catch (e: Exception) {
            logger.error("Unexpected transaction error", e)
            Result.Err(DomainError.StorageError("Transaction failed: ${e.message}"))
        }
    }

    /**
     * 읽기 전용 트랜잭션
     */
    suspend fun <T> readOnlyTransaction(
        block: suspend () -> Result<T>
    ): Result<T> = transaction(Connection.TRANSACTION_REPEATABLE_READ, block)

    private class TransactionException(val domainError: DomainError) : RuntimeException(domainError.toString())
}
