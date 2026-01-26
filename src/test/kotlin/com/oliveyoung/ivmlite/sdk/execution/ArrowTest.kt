package com.oliveyoung.ivmlite.sdk.execution

import com.oliveyoung.ivmlite.sdk.model.Either
import com.oliveyoung.ivmlite.sdk.model.left
import com.oliveyoung.ivmlite.sdk.model.right
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ArrowTest : DescribeSpec({
    describe("Custom Either") {
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
