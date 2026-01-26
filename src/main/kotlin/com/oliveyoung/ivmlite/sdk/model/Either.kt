package com.oliveyoung.ivmlite.sdk.model

/**
 * Either 타입 (Right = 성공, Left = 실패)
 * RFC-IMPL-011 Wave 2-G
 */
sealed class Either<out L, out R> {
    data class Left<out L>(val value: L) : Either<L, Nothing>()
    data class Right<out R>(val value: R) : Either<Nothing, R>()

    fun <T> fold(ifLeft: (L) -> T, ifRight: (R) -> T): T = when (this) {
        is Left -> ifLeft(value)
        is Right -> ifRight(value)
    }

    fun leftOrNull(): L? = when (this) {
        is Left -> value
        is Right -> null
    }

    fun rightOrNull(): R? = when (this) {
        is Left -> null
        is Right -> value
    }

    fun getOrNull(): R? = rightOrNull()

    fun isRight(): Boolean = this is Right
    fun isLeft(): Boolean = this is Left
}

fun <L> L.left(): Either<L, Nothing> = Either.Left(this)
fun <R> R.right(): Either<Nothing, R> = Either.Right(this)
