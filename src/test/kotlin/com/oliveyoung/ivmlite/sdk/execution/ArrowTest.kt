package com.oliveyoung.ivmlite.sdk.execution

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ArrowTest : DescribeSpec({
    describe("Arrow Either") {
        it("right") {
            val result: Either<String, Int> = 42.right()
            result shouldBe Either.Right(42)
        }

        it("left") {
            val result: Either<String, Int> = "error".left()
            result shouldBe Either.Left("error")
        }
    }
})
