package com.oliveyoung.ivmlite.shared.domain.types

import arrow.core.Either
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError

/**
 * 공유 Result 타입 (SSOT - Single Source of Truth)
 *
 * 중요: 이 타입은 프로젝트 전체에서 단일 정의되어야 합니다.
 * 각 Service에서 별도 정의하지 마세요.
 *
 * 권장: Arrow Either<DomainError, T> 사용을 점진적으로 마이그레이션
 * - 기존 코드: Result<T> 사용
 * - 신규 코드: Either<DomainError, T> 사용 권장
 */
sealed class Result<out T> {
    data class Ok<T>(val value: T) : Result<T>()
    data class Err(val error: DomainError) : Result<Nothing>()

    val isOk: Boolean get() = this is Ok
    val isErr: Boolean get() = this is Err

    fun getOrNull(): T? = when (this) {
        is Ok -> value
        is Err -> null
    }

    fun errorOrNull(): DomainError? = when (this) {
        is Ok -> null
        is Err -> error
    }

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }

    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Ok -> transform(value)
        is Err -> this
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Ok) action(value)
        return this
    }

    inline fun onFailure(action: (DomainError) -> Unit): Result<T> {
        if (this is Err) action(error)
        return this
    }

    inline fun <R> fold(onErr: (DomainError) -> R, onOk: (T) -> R): R = when (this) {
        is Ok -> onOk(value)
        is Err -> onErr(error)
    }

    /**
     * Arrow Either로 변환 (마이그레이션 지원)
     */
    fun toEither(): Either<DomainError, T> = when (this) {
        is Ok -> Either.Right(value)
        is Err -> Either.Left(error)
    }

    companion object {
        /**
         * Arrow Either에서 변환 (마이그레이션 지원)
         */
        fun <T> fromEither(either: Either<DomainError, T>): Result<T> =
            either.fold({ Err(it) }, { Ok(it) })

        /**
         * 예외를 Result로 래핑
         */
        inline fun <T> catch(block: () -> T): Result<T> =
            try {
                Ok(block())
            } catch (e: DomainError) {
                Err(e)
            } catch (e: Exception) {
                Err(DomainError.InternalError(e.message ?: "Unknown error"))
            }
    }
}
