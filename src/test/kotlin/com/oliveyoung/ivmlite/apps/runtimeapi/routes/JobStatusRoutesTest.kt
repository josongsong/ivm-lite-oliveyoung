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
 * JobStatusRoutes HTTP-level 테스트
 *
 * GET /api/v1/jobs/{jobId}/status: jobId로 SinkEvent 추적
 */
class JobStatusRoutesTest : StringSpec({

    val sinkEventRepo = InMemorySinkEventRepository()

    fun ApplicationTestBuilder.configureJobStatusApp() {
        application {
            install(ContentNegotiation) { json() }
            install(Koin) {
                modules(module {
                    single<SinkEventRepositoryPort> { sinkEventRepo }
                })
            }
            routing {
                jobStatusRoutes()
            }
        }
    }

    suspend fun seedSinkEventWithJob(
        jobId: String,
        viewType: String = "pdp",
        entityKey: String = "product:SKU-001"
    ): SinkEvent {
        val event = SinkEvent.create(
            tenantId = "test-tenant",
            entityKey = entityKey,
            version = 1L,
            viewType = viewType,
            payload = """{"tenantId":"test-tenant","entityKey":"product:SKU-001","version":"1"}""",
            sinkTargets = listOf("s3"),
            jobId = jobId
        )
        val result = sinkEventRepo.put(event)
        check(result is Result.Ok) { "seedSinkEventWithJob put failed: $result" }
        return result.value
    }

    beforeTest {
        runCatching { stopKoin() }
        sinkEventRepo.clear()
    }

    "GET /api/v1/jobs/{jobId}/status → 200 OK, 이벤트 추적" {
        seedSinkEventWithJob("job-001")

        testApplication {
            configureJobStatusApp()

            val response = client.get("/api/v1/jobs/job-001/status")

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["jobId"]?.jsonPrimitive?.content shouldBe "job-001"
            json["eventCount"]?.jsonPrimitive?.int shouldBe 1

            val events = json["events"]?.jsonArray
            check(events != null) { "events should not be null" }
            events.size shouldBe 1
            events[0].jsonObject["eventType"]?.jsonPrimitive?.content shouldBe "pdp"
            events[0].jsonObject["status"]?.jsonPrimitive?.content shouldBe "PENDING"
        }
    }

    "GET /api/v1/jobs/{jobId}/status → 다중 이벤트 추적" {
        seedSinkEventWithJob("job-multi", viewType = "pdp", entityKey = "product:001")
        seedSinkEventWithJob("job-multi", viewType = "list", entityKey = "product:002")

        testApplication {
            configureJobStatusApp()

            val response = client.get("/api/v1/jobs/job-multi/status")

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["jobId"]?.jsonPrimitive?.content shouldBe "job-multi"
            json["eventCount"]?.jsonPrimitive?.int shouldBe 2
        }
    }

    "GET /api/v1/jobs/{jobId}/status → 존재하지 않는 jobId → 빈 이벤트" {
        testApplication {
            configureJobStatusApp()

            val response = client.get("/api/v1/jobs/nonexistent-job/status")

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["jobId"]?.jsonPrimitive?.content shouldBe "nonexistent-job"
            json["eventCount"]?.jsonPrimitive?.int shouldBe 0
            json["events"]?.jsonArray?.size shouldBe 0
        }
    }

    "GET /api/v1/jobs/{jobId}/status → 이벤트 필드 검증" {
        seedSinkEventWithJob("job-fields")

        testApplication {
            configureJobStatusApp()

            val response = client.get("/api/v1/jobs/job-fields/status")

            response.status shouldBe HttpStatusCode.OK
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val event = json["events"]?.jsonArray?.get(0)?.jsonObject

            check(event != null) { "event should not be null" }
            event.containsKey("eventType") shouldBe true
            event.containsKey("aggregateType") shouldBe true
            event.containsKey("aggregateId") shouldBe true
            event.containsKey("status") shouldBe true
            event.containsKey("createdAt") shouldBe true
            event.containsKey("retryCount") shouldBe true

            event["aggregateType"]?.jsonPrimitive?.content shouldBe "SINK_EVENT"
            event["retryCount"]?.jsonPrimitive?.int shouldBe 0
        }
    }

    "GET /api/v1/jobs/{jobId}/status → 다른 jobId의 이벤트 혼합 안됨" {
        seedSinkEventWithJob("job-A", entityKey = "product:001")
        seedSinkEventWithJob("job-B", entityKey = "product:002")

        testApplication {
            configureJobStatusApp()

            val responseA = client.get("/api/v1/jobs/job-A/status")
            val responseB = client.get("/api/v1/jobs/job-B/status")

            val jsonA = Json.parseToJsonElement(responseA.bodyAsText()).jsonObject
            val jsonB = Json.parseToJsonElement(responseB.bodyAsText()).jsonObject

            jsonA["eventCount"]?.jsonPrimitive?.int shouldBe 1
            jsonB["eventCount"]?.jsonPrimitive?.int shouldBe 1
        }
    }

    "GET /api/v1/jobs/{jobId}/status → processedAt, failureReason null 허용" {
        seedSinkEventWithJob("job-nullable")

        testApplication {
            configureJobStatusApp()

            val response = client.get("/api/v1/jobs/job-nullable/status")

            response.status shouldBe HttpStatusCode.OK
            val event = Json.parseToJsonElement(response.bodyAsText()).jsonObject["events"]?.jsonArray?.get(0)?.jsonObject

            // PENDING 상태에서는 processedAt, failureReason이 null
            val processedAt = event?.get("processedAt")
            val failureReason = event?.get("failureReason")
            assert(processedAt == null || processedAt == JsonNull) { "processedAt should be null for PENDING" }
            assert(failureReason == null || failureReason == JsonNull) { "failureReason should be null for PENDING" }
        }
    }
})
