package com.oliveyoung.ivmlite.apps.runtimeapi.routes

import com.oliveyoung.ivmlite.shared.ports.HealthCheckable
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * RFC-IMPL-010: HealthRoutes TDD 테스트
 *
 * 빵구없는 테스트 (엣지/코너 케이스 전수):
 * 1. 모든 어댑터 healthy → 200 OK, status: UP
 * 2. 하나라도 unhealthy → 503, status: DOWN
 * 3. 어댑터 healthCheck() 예외 → false 처리
 * 4. 어댑터 0개 등록 → 200 OK (vacuous truth)
 * 5. timeout 발생 → false 처리
 * 6. checks 맵에 각 어댑터 이름 + 상태 포함
 */
class HealthRoutesTest : StringSpec({

    "1. 모든 어댑터 healthy → 200 OK, status: UP" {
        testApplication {
            val adapters = listOf(
                TestHealthyAdapter("rawdata"),
                TestHealthyAdapter("slice"),
                TestHealthyAdapter("outbox"),
            )

            application { configureTestModule(adapters) }

            val response = client.get("/ready")

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["status"]?.jsonPrimitive?.content shouldBe "UP"

            val checks = json["checks"]?.jsonObject
            checks?.get("rawdata")?.jsonPrimitive?.boolean shouldBe true
            checks?.get("slice")?.jsonPrimitive?.boolean shouldBe true
            checks?.get("outbox")?.jsonPrimitive?.boolean shouldBe true
        }
    }

    "2. 하나라도 unhealthy → 503 ServiceUnavailable, status: DOWN" {
        testApplication {
            val adapters = listOf(
                TestHealthyAdapter("rawdata"),
                TestUnhealthyAdapter("slice"),
                TestHealthyAdapter("outbox"),
            )

            application { configureTestModule(adapters) }

            val response = client.get("/ready")

            response.status shouldBe HttpStatusCode.ServiceUnavailable
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["status"]?.jsonPrimitive?.content shouldBe "DOWN"

            val checks = json["checks"]?.jsonObject
            checks?.get("rawdata")?.jsonPrimitive?.boolean shouldBe true
            checks?.get("slice")?.jsonPrimitive?.boolean shouldBe false
            checks?.get("outbox")?.jsonPrimitive?.boolean shouldBe true
        }
    }

    "3. 어댑터 healthCheck() 예외 발생 → false 처리" {
        testApplication {
            val adapters = listOf(
                TestHealthyAdapter("rawdata"),
                TestExceptionAdapter("error-adapter"),
            )

            application { configureTestModule(adapters) }

            val response = client.get("/ready")

            response.status shouldBe HttpStatusCode.ServiceUnavailable
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["status"]?.jsonPrimitive?.content shouldBe "DOWN"

            val checks = json["checks"]?.jsonObject
            checks?.get("rawdata")?.jsonPrimitive?.boolean shouldBe true
            checks?.get("error-adapter")?.jsonPrimitive?.boolean shouldBe false
        }
    }

    "4. 어댑터 0개 등록 → 200 OK, status: UP (vacuous truth)" {
        testApplication {
            application { configureTestModule(emptyList()) }

            val response = client.get("/ready")

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["status"]?.jsonPrimitive?.content shouldBe "UP"
            json["checks"]?.jsonObject?.size shouldBe 0
        }
    }

    "5. timeout 발생 → false 처리" {
        testApplication {
            val adapters = listOf(
                TestHealthyAdapter("fast-adapter"),
                TestSlowAdapter("slow-adapter", delayMs = 10_000), // 10초 대기 (5초 timeout 초과)
            )

            application { configureTestModule(adapters, timeoutMs = 100) }

            val response = client.get("/ready")

            response.status shouldBe HttpStatusCode.ServiceUnavailable
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["status"]?.jsonPrimitive?.content shouldBe "DOWN"

            val checks = json["checks"]?.jsonObject
            checks?.get("fast-adapter")?.jsonPrimitive?.boolean shouldBe true
            checks?.get("slow-adapter")?.jsonPrimitive?.boolean shouldBe false
        }
    }

    "6. checks 맵에 각 어댑터 이름 + 상태 정확히 포함" {
        testApplication {
            val adapters = listOf(
                TestHealthyAdapter("contracts"),
                TestHealthyAdapter("rawdata-repository"),
                TestUnhealthyAdapter("slice-repository"),
                TestExceptionAdapter("outbox-repository"),
            )

            application { configureTestModule(adapters) }

            val response = client.get("/ready")
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val checks = json["checks"]?.jsonObject

            checks?.size shouldBe 4
            checks?.get("contracts")?.jsonPrimitive?.boolean shouldBe true
            checks?.get("rawdata-repository")?.jsonPrimitive?.boolean shouldBe true
            checks?.get("slice-repository")?.jsonPrimitive?.boolean shouldBe false
            checks?.get("outbox-repository")?.jsonPrimitive?.boolean shouldBe false
        }
    }

    "GET /health (liveness) → 항상 200 OK" {
        testApplication {
            application { configureTestModule(listOf(TestUnhealthyAdapter("broken"))) }

            val response = client.get("/health")

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["status"]?.jsonPrimitive?.content shouldBe "UP"
        }
    }

    // ==================== 추가 엣지/코너 케이스 ====================

    "7. 모든 어댑터 unhealthy → 503, 모든 checks false" {
        testApplication {
            val adapters = listOf(
                TestUnhealthyAdapter("db"),
                TestUnhealthyAdapter("cache"),
                TestExceptionAdapter("queue"),
            )

            application { configureTestModule(adapters) }

            val response = client.get("/ready")

            response.status shouldBe HttpStatusCode.ServiceUnavailable
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["status"]?.jsonPrimitive?.content shouldBe "DOWN"

            val checks = json["checks"]?.jsonObject
            checks?.values?.all { it.jsonPrimitive.boolean == false } shouldBe true
        }
    }

    "8. healthName 중복 시 마지막 결과만 Map에 포함 (경고 케이스)" {
        testApplication {
            // 같은 이름으로 2개 어댑터 - 실제로는 Koin 설계상 발생하지 않아야 함
            val adapters = listOf(
                TestHealthyAdapter("duplicate"),
                TestUnhealthyAdapter("duplicate"),
            )

            application { configureTestModule(adapters) }

            val response = client.get("/ready")
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val checks = json["checks"]?.jsonObject

            // Map은 중복 키 허용 안함 → 마지막 값(false)만 남음
            checks?.size shouldBe 1
            checks?.get("duplicate")?.jsonPrimitive?.boolean shouldBe false
        }
    }

    "9. 타임아웃 0ms → 모든 어댑터 즉시 실패 (경계값)" {
        testApplication {
            val adapters = listOf(
                TestHealthyAdapter("instant"), // 즉시 반환해도 0ms면 실패 가능
            )

            application { configureTestModule(adapters, timeoutMs = 0) }

            val response = client.get("/ready")

            // 0ms 타임아웃은 withTimeoutOrNull이 null 반환 → false
            response.status shouldBe HttpStatusCode.ServiceUnavailable
        }
    }

    "10. 단일 어댑터 healthy → 200 OK" {
        testApplication {
            val adapters = listOf(TestHealthyAdapter("single"))

            application { configureTestModule(adapters) }

            val response = client.get("/ready")

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["checks"]?.jsonObject?.size shouldBe 1
        }
    }

    "11. 단일 어댑터 unhealthy → 503" {
        testApplication {
            val adapters = listOf(TestUnhealthyAdapter("single"))

            application { configureTestModule(adapters) }

            val response = client.get("/ready")

            response.status shouldBe HttpStatusCode.ServiceUnavailable
        }
    }

    "12. 대량 어댑터(100개) 병렬 체크 성능" {
        testApplication {
            val adapters = (1..100).map { TestHealthyAdapter("adapter-$it") }

            application { configureTestModule(adapters) }

            val response = client.get("/ready")

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["checks"]?.jsonObject?.size shouldBe 100
        }
    }

    "13. 혼합 상태 (50% healthy, 50% unhealthy) → 503" {
        testApplication {
            val adapters = (1..10).map { i ->
                if (i % 2 == 0) TestHealthyAdapter("adapter-$i")
                else TestUnhealthyAdapter("adapter-$i")
            }

            application { configureTestModule(adapters) }

            val response = client.get("/ready")

            response.status shouldBe HttpStatusCode.ServiceUnavailable
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val checks = json["checks"]?.jsonObject

            // 정확히 5개 true, 5개 false
            checks?.values?.count { it.jsonPrimitive.boolean } shouldBe 5
            checks?.values?.count { !it.jsonPrimitive.boolean } shouldBe 5
        }
    }

    "14. 타임아웃 경계값 - 충분한 시간 내 완료하는 어댑터" {
        testApplication {
            // 100ms 타임아웃, 30ms 지연 → 성공해야 함 (race condition 방지)
            val adapters = listOf(TestSlowAdapter("boundary", delayMs = 30))

            application { configureTestModule(adapters, timeoutMs = 100) }

            val response = client.get("/ready")

            response.status shouldBe HttpStatusCode.OK
        }
    }

    "15. Error (not Exception) 발생 시 false 처리 (graceful degradation)" {
        testApplication {
            val adapters = listOf(
                TestErrorAdapter("error-adapter"),
                TestHealthyAdapter("normal"),
            )

            application { configureTestModule(adapters) }

            val response = client.get("/ready")

            // Error도 false로 처리됨 (다른 어댑터에 영향 없음)
            response.status shouldBe HttpStatusCode.ServiceUnavailable
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["checks"]?.jsonObject?.get("error-adapter")?.jsonPrimitive?.boolean shouldBe false
            json["checks"]?.jsonObject?.get("normal")?.jsonPrimitive?.boolean shouldBe true
        }
    }

    "16. 음수 타임아웃 → IllegalArgumentException" {
        shouldThrow<IllegalArgumentException> {
            testApplication {
                application { configureTestModule(emptyList(), timeoutMs = -1) }
            }
        }
    }

    "17. 빈 healthName 어댑터 포함 시 경고 로깅 (동작은 정상)" {
        testApplication {
            val adapters = listOf(
                TestHealthyAdapter(""), // 빈 이름
                TestHealthyAdapter("valid"),
            )

            application { configureTestModule(adapters) }

            val response = client.get("/ready")

            // 빈 이름도 Map 키로 사용됨 (경고만 로깅)
            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["checks"]?.jsonObject?.size shouldBe 2
        }
    }
})

// ==================== Test Helpers ====================

private fun Application.configureTestModule(
    adapters: List<HealthCheckable>,
    timeoutMs: Long = 5000,
) {
    install(ContentNegotiation) {
        json()
    }
    routing {
        healthRoutes(adapters, timeoutMs)
    }
}

private class TestHealthyAdapter(override val healthName: String) : HealthCheckable {
    override suspend fun healthCheck(): Boolean = true
}

private class TestUnhealthyAdapter(override val healthName: String) : HealthCheckable {
    override suspend fun healthCheck(): Boolean = false
}

private class TestExceptionAdapter(override val healthName: String) : HealthCheckable {
    override suspend fun healthCheck(): Boolean {
        throw RuntimeException("Simulated failure")
    }
}

private class TestSlowAdapter(
    override val healthName: String,
    private val delayMs: Long,
) : HealthCheckable {
    override suspend fun healthCheck(): Boolean {
        delay(delayMs)
        return true
    }
}

private class TestCancellationAdapter(override val healthName: String) : HealthCheckable {
    override suspend fun healthCheck(): Boolean {
        throw kotlinx.coroutines.CancellationException("Simulated cancellation")
    }
}

private class TestErrorAdapter(override val healthName: String) : HealthCheckable {
    override suspend fun healthCheck(): Boolean {
        throw OutOfMemoryError("Simulated OOM") // Error (not Exception)
    }
}
