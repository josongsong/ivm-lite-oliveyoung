package com.oliveyoung.ivmlite.apps.runtimeapi.routes

import com.oliveyoung.ivmlite.pkg.sinks.adapters.InMemorySinkEventRepository
import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkEvent
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkEventRepositoryPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
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
import kotlinx.serialization.json.*
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

/**
 * SinkEventRoutes HTTP-level 테스트
 *
 * GET /api/v1/sink-events/pending: PENDING 상태 SinkEvent 조회
 * GET /api/v1/sink-events/{id}: ID로 SinkEvent 조회
 */
class SinkEventRoutesTest : StringSpec({

    val sinkEventRepo = InMemorySinkEventRepository()

    fun ApplicationTestBuilder.configureSinkEventApp() {
        application {
            install(ContentNegotiation) { json() }
            install(Koin) {
                modules(module {
                    single<SinkEventRepositoryPort> { sinkEventRepo }
                })
            }
            routing {
                sinkEventRoutes()
            }
        }
    }

    suspend fun seedSinkEvent(
        viewType: String = "product-search",
        entityKey: String = "product:SKU-001",
        jobId: String? = null
    ): SinkEvent {
        val event = SinkEvent.create(
            tenantId = "test-tenant",
            entityKey = entityKey,
            version = 1L,
            viewType = viewType,
            payload = """{"tenantId":"test-tenant","entityKey":"$entityKey","version":"1"}""",
            sinkTargets = listOf("opensearch-sink"),
            jobId = jobId
        )
        val result = sinkEventRepo.put(event)
        check(result is Result.Ok) { "seedSinkEvent put failed: $result" }
        return result.value
    }

    beforeTest {
        runCatching { stopKoin() }
        sinkEventRepo.clear()
    }

    "GET /api/v1/sink-events/pending → 200 OK, entries 포함" {
        seedSinkEvent()

        testApplication {
            configureSinkEventApp()

            val response = client.get("/api/v1/sink-events/pending")

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["success"]?.jsonPrimitive?.boolean shouldBe true
            json["count"]?.jsonPrimitive?.int shouldBe 1

            val entries = json["entries"]?.jsonArray
            check(entries != null) { "entries should not be null" }
            entries.size shouldBe 1
            entries[0].jsonObject["entityKey"]?.jsonPrimitive?.content shouldBe "product:SKU-001"
            entries[0].jsonObject["status"]?.jsonPrimitive?.content shouldBe "PENDING"
            entries[0].jsonObject["viewType"]?.jsonPrimitive?.content shouldBe "product-search"
        }
    }

    "GET /api/v1/sink-events/pending → limit 파라미터 적용" {
        seedSinkEvent(entityKey = "product:001")
        seedSinkEvent(entityKey = "product:002")
        seedSinkEvent(entityKey = "product:003")

        testApplication {
            configureSinkEventApp()

            val response = client.get("/api/v1/sink-events/pending?limit=2")

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["count"]?.jsonPrimitive?.int shouldBe 2
            json["entries"]?.jsonArray?.size shouldBe 2
        }
    }

    "GET /api/v1/sink-events/pending → 빈 목록" {
        testApplication {
            configureSinkEventApp()

            val response = client.get("/api/v1/sink-events/pending")

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["success"]?.jsonPrimitive?.boolean shouldBe true
            json["count"]?.jsonPrimitive?.int shouldBe 0
            json["entries"]?.jsonArray?.size shouldBe 0
        }
    }

    "GET /api/v1/sink-events/{id} → 200 OK, 단일 이벤트" {
        val event = seedSinkEvent()

        testApplication {
            configureSinkEventApp()

            val response = client.get("/api/v1/sink-events/${event.id}")

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["success"]?.jsonPrimitive?.boolean shouldBe true
            val entry = json["entry"]?.jsonObject
            check(entry != null) { "entry should not be null" }
            entry["id"]?.jsonPrimitive?.content shouldBe event.id.toString()
            entry["entityKey"]?.jsonPrimitive?.content shouldBe "product:SKU-001"
            entry["status"]?.jsonPrimitive?.content shouldBe "PENDING"
        }
    }

    "GET /api/v1/sink-events/{id} → 존재하지 않는 ID → 404" {
        testApplication {
            configureSinkEventApp()

            val fakeId = "00000000-0000-0000-0000-000000000000"
            val response = client.get("/api/v1/sink-events/$fakeId")

            response.status shouldBe HttpStatusCode.NotFound
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["code"]?.jsonPrimitive?.content shouldBe "NOT_FOUND"
        }
    }

    "GET /api/v1/sink-events/{id} → 잘못된 UUID 형식 → 400" {
        testApplication {
            configureSinkEventApp()

            val response = client.get("/api/v1/sink-events/invalid-uuid")

            response.status shouldBe HttpStatusCode.BadRequest
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["code"]?.jsonPrimitive?.content shouldBe "INVALID_UUID"
        }
    }
})
