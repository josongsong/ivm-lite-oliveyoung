package com.oliveyoung.ivmlite.apps.admin

import com.oliveyoung.ivmlite.apps.admin.dto.ApiError
import com.oliveyoung.ivmlite.apps.admin.dto.toKtorStatus
import com.oliveyoung.ivmlite.apps.admin.routes.adminRoutes
import com.oliveyoung.ivmlite.apps.admin.routes.alertRoutes
import com.oliveyoung.ivmlite.apps.admin.routes.backfillRoutes
import com.oliveyoung.ivmlite.apps.admin.routes.contractRoutes
import com.oliveyoung.ivmlite.apps.admin.routes.explorerRoutes
import com.oliveyoung.ivmlite.apps.admin.routes.healthRoutes
import com.oliveyoung.ivmlite.apps.admin.routes.observabilityRoutes
import com.oliveyoung.ivmlite.apps.admin.routes.pipelineRoutes
import com.oliveyoung.ivmlite.apps.admin.routes.workflowCanvasRoutes
import com.oliveyoung.ivmlite.apps.admin.wiring.adminAllModules
import com.oliveyoung.ivmlite.pkg.alerts.application.AlertEngine
import com.oliveyoung.ivmlite.shared.config.AppConfig
import com.oliveyoung.ivmlite.shared.config.DotenvLoader
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

/**
 * IVM Lite Admin Application
 *
 * 관리자 대시보드 전용 애플리케이션
 * - 포트: 8081 (기본값, ADMIN_PORT 환경 변수로 변경 가능)
 * - 목적: 시스템 모니터링 및 관리
 * 
 * 런타임 독립성:
 * - runtimeapi와 완전히 독립적으로 실행 가능
 * - 별도 포트, 별도 프로세스
 * - 같은 데이터베이스 공유 (모니터링 목적)
 * 
 * 코드 공유:
 * - wiring 모듈은 runtimeapi와 공유 (코드 중복 방지)
 * - 실제 런타임은 완전히 독립적
 */
fun main() {
    DotenvLoader.load()
    val adminPort = System.getenv("ADMIN_PORT")?.toIntOrNull() ?: 8081

    embeddedServer(Netty, port = adminPort, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    // Koin DI (Admin 앱 전용 모듈)
    install(Koin) {
        slf4jLogger()
        modules(adminAllModules)
    }
    
    // CORS (개발 환경에서 프론트엔드 직접 호출 허용)
    install(CORS) {
        allowHost("localhost:3000")  // Vite 개발 서버
        allowHost("localhost:8081")  // 같은 origin
        allowHost("127.0.0.1:3000")
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }

    // Content Negotiation (JSON)
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            ignoreUnknownKeys = false
            isLenient = false
        })
    }
    
    // Status Pages (전역 에러 핸들링 - SOTA 트레이싱)
    // DEV_MODE=true 시 스택트레이스 포함
    val devMode = System.getenv("DEV_MODE")?.toBoolean() ?: false

    install(StatusPages) {
        // DomainError: 비즈니스 에러 (400, 404, 500 등)
        exception<DomainError> { call, cause ->
            val requestId = ApiError.generateRequestId()
            val path = call.request.path()
            call.application.log.warn("[{}] [DomainError] {} {} - {}: {}",
                requestId, call.request.httpMethod.value, path, cause::class.simpleName, cause.message)
            call.respond(cause.toKtorStatus(), ApiError.from(cause, requestId, path, devMode))
        }

        // SerializationException: JSON 파싱 에러 (400)
        exception<SerializationException> { call, cause ->
            val requestId = ApiError.generateRequestId()
            val path = call.request.path()
            call.application.log.warn("[{}] [SerializationError] {} {} - {}",
                requestId, call.request.httpMethod.value, path, cause.message)
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError.fromException("INVALID_JSON", "Invalid JSON: ${cause.message}", requestId, path, cause, devMode)
            )
        }

        // IllegalArgumentException: 입력 검증 에러 (400)
        exception<IllegalArgumentException> { call, cause ->
            val requestId = ApiError.generateRequestId()
            val path = call.request.path()
            call.application.log.warn("[{}] [ValidationError] {} {} - {}",
                requestId, call.request.httpMethod.value, path, cause.message)
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError.fromException("VALIDATION_ERROR", cause.message ?: "Invalid request", requestId, path, cause, devMode)
            )
        }

        // BadRequestException: Ktor 요청 에러 (400)
        exception<io.ktor.server.plugins.BadRequestException> { call, cause ->
            val requestId = ApiError.generateRequestId()
            val path = call.request.path()
            call.application.log.warn("[{}] [BadRequest] {} {} - {}",
                requestId, call.request.httpMethod.value, path, cause.message)
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError.fromException("BAD_REQUEST", cause.message ?: "Bad request", requestId, path, cause, devMode)
            )
        }

        // Throwable: 기타 모든 에러 (500)
        exception<Throwable> { call, cause ->
            val requestId = ApiError.generateRequestId()
            val path = call.request.path()
            call.application.log.error("[{}] [InternalError] {} {} - {}: {}",
                requestId, call.request.httpMethod.value, path, cause::class.simpleName, cause.message, cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError.fromException("INTERNAL_ERROR", "${cause::class.simpleName}: ${cause.message}", requestId, path, cause, devMode)
            )
        }
    }
    
    // Config
    val config by inject<AppConfig>()
    
    // Alert Engine 시작 (백그라운드)
    val alertEngine by inject<AlertEngine>()
    alertEngine.start()
    
    // Routes
    routing {
        // API Routes (prefix: /api) - 먼저 정의해야 우선순위가 높음
        route("/api") {
            // Admin API Routes (Dashboard, Worker, Outbox)
            adminRoutes()
            
            // Contract API Routes (Schema, RuleSet, ViewDef)
            contractRoutes()
            
            // Pipeline API Routes (Raw → Slice → View → Sink)
            pipelineRoutes()
            
            // Alert Routes (알림 관리)
            alertRoutes()
            
            // Backfill Routes (재처리 작업)
            backfillRoutes()
            
            // Health Routes (시스템 상태)
            healthRoutes()
            
            // Observability Routes (모니터링)
            observabilityRoutes()

            // Workflow Canvas Routes (RFC-IMPL-015: 파이프라인 시각화)
            workflowCanvasRoutes()

            // Data Explorer Routes (RawData, Slice, View 조회)
            route("/query") {
                explorerRoutes()
            }
        }
        
        // Static Resources (React Admin UI)
        staticResources("/assets", "static/admin/assets")
        staticResources("/favicon.svg", "static/admin")
        
        // SPA Fallback: 모든 다른 경로는 index.html로
        get("{...}") {
            val indexHtml = this::class.java.classLoader.getResource("static/admin/index.html")
            if (indexHtml != null) {
                call.respondText(indexHtml.readText(), ContentType.Text.Html)
            } else {
                call.respond(HttpStatusCode.NotFound, "Admin UI not found")
            }
        }
    }
    
    log.info("IVM Lite Admin Application started on ${config.server.host}:${System.getenv("ADMIN_PORT")?.toIntOrNull() ?: 8081}")
}
