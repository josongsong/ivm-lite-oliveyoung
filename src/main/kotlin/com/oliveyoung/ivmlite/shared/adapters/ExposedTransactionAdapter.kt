package com.oliveyoung.ivmlite.shared.adapters

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.ports.TransactionPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection

/**
 * ExposedTransactionAdapter - Exposed 기반 트랜잭션 어댑터
 *
 * 코루틴 네이티브: newSuspendedTransaction 사용 (runBlocking 불필요)
 */
class ExposedTransactionAdapter(
    private val database: Database,
    private val isolationLevel: Int = Connection.TRANSACTION_READ_COMMITTED
) : TransactionPort {

    override suspend fun <T> execute(block: suspend () -> Result<T>): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                newSuspendedTransaction(Dispatchers.IO, database, transactionIsolation = isolationLevel) {
                    when (val blockResult = block()) {
                        is Result.Ok -> blockResult
                        is Result.Err -> {
                            rollback()
                            throw TransactionRollbackException(blockResult.error)
                        }
                    }
                }
            } catch (e: TransactionRollbackException) {
                Result.Err(e.error)
            } catch (e: Exception) {
                Result.Err(DomainError.StorageError("Transaction failed: ${e.message}"))
            }
        }

    private class TransactionRollbackException(val error: DomainError) : RuntimeException(error.toString())
}
