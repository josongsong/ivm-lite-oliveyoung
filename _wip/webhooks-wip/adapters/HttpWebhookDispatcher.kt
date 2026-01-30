package com.oliveyoung.ivmlite.pkg.webhooks.adapters

import com.oliveyoung.ivmlite.pkg.webhooks.domain.Webhook
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEventPayload
import com.oliveyoung.ivmlite.pkg.webhooks.ports.CircuitState
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DispatchResult
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DispatcherStats
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookDispatcherPort
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * HTTP Webhook Dispatcher 설정
 */
data class DispatcherConfig(
    /** 요청 타임아웃 (밀리초) */
    val requestTimeoutMs: Long = 30_000,
    /** 연결 타임아웃 (밀리초) */
    val connectTimeoutMs: Long = 5_000,
    /** 소켓 타임아웃 (밀리초) */
    val socketTimeoutMs: Long = 10_000,
    /** 최대 커넥션 수 */
    val maxConnections: Int = 100,
    /** 호스트당 최대 커넥션 수 */
    val maxConnectionsPerHost: Int = 10,
    /** Circuit Breaker 설정 */
    val circuitBreaker: CircuitBreakerConfig = CircuitBreakerConfig.DEFAULT,
    /** Rate Limiter 설정 */
    val rateLimiter: RateLimiterConfig = RateLimiterConfig.DEFAULT,
    /** 지수 백오프 최대 재시도 횟수 (dispatch 호출당) */
    val maxRetries: Int = 3,
    /** 초기 재시도 대기 (밀리초) */
    val initialRetryDelayMs: Long = 1000,
    /** 최대 재시도 대기 (밀리초) */
    val maxRetryDelayMs: Long = 30_000,
    /** 재시도 배수 */
    val retryMultiplier: Double = 2.0,
    /** 지터 팩터 */
    val jitterFactor: Double = 0.1
) {
    companion object {
        val DEFAULT = DispatcherConfig()
    }
}

/**
 * SOTA급 HTTP Webhook Dispatcher
 *
 * Features:
 * - Circuit Breaker: 연속 실패 시 회로 열기
 * - Rate Limiter: 초과 요청 제한
 * - Exponential Backoff: 지수 백오프 + 지터 재시도
 * - HMAC-SHA256 서명: 페이로드 무결성 검증
 * - 커넥션 풀: 효율적 HTTP 연결 관리
 */
class HttpWebhookDispatcher(
    private val config: DispatcherConfig = DispatcherConfig.DEFAULT
) : WebhookDispatcherPort {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    // 웹훅별 Circuit Breaker
    private val circuitBreakers = ConcurrentHashMap<UUID, CircuitBreaker>()

    // 웹훅별 Rate Limiter
    private val rateLimiters = ConcurrentHashMap<UUID, RateLimiter>()

    // 통계
    private val totalDispatched = AtomicLong(0)
    private val successCount = AtomicLong(0)
    private val failureCount = AtomicLong(0)
    private val circuitOpenCount = AtomicLong(0)
    private val rateLimitedCount = AtomicLong(0)
    private val timeoutCount = AtomicLong(0)

    // HTTP Client (커넥션 풀)
    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeoutMs
            connectTimeoutMillis = config.connectTimeoutMs
            socketTimeoutMillis = config.socketTimeoutMs
        }

        engine {
            maxConnectionsCount = config.maxConnections
            endpoint {
                maxConnectionsPerRoute = config.maxConnectionsPerHost
                pipelineMaxSize = 20
                keepAliveTime = 5000
                connectTimeout = config.connectTimeoutMs
                connectAttempts = 1 // 재시도는 우리가 직접 관리
            }
        }

        expectSuccess = false // 4xx, 5xx도 예외 없이 처리
    }

    override suspend fun dispatch(webhook: Webhook, payload: WebhookEventPayload): DispatchResult {
        totalDispatched.incrementAndGet()

        // 1. Circuit Breaker 체크
        val breaker = circuitBreakers.getOrPut(webhook.id) {
            CircuitBreaker(config.circuitBreaker)
        }
        if (!breaker.isAllowed()) {
            circuitOpenCount.incrementAndGet()
            return DispatchResult.CircuitOpen
        }

        // 2. Rate Limiter 체크
        val limiter = rateLimiters.getOrPut(webhook.id) {
            RateLimiter(config.rateLimiter)
        }
        if (!limiter.tryAcquire()) {
            rateLimitedCount.incrementAndGet()
            return DispatchResult.RateLimited
        }

        // 3. 지수 백오프 재시도로 전송
        return withExponentialBackoff { attempt ->
            executeRequest(webhook, payload, attempt)
        }.also { result ->
            // 4. 결과에 따라 Circuit Breaker 업데이트
            when (result) {
                is DispatchResult.Success -> {
                    breaker.recordSuccess()
                    successCount.incrementAndGet()
                }
                is DispatchResult.Failure -> {
                    if (result.isRetryable) {
                        breaker.recordFailure()
                    }
                    failureCount.incrementAndGet()
                }
                is DispatchResult.Timeout -> {
                    breaker.recordFailure()
                    timeoutCount.incrementAndGet()
                    failureCount.incrementAndGet()
                }
                else -> {}
            }
        }
    }

    override suspend fun testDispatch(webhook: Webhook, payload: WebhookEventPayload): DispatchResult {
        // 테스트는 Circuit Breaker, Rate Limiter 무시하고 1회만 시도
        return executeRequest(webhook, payload, attempt = 1)
    }

    /**
     * 지수 백오프 재시도
     */
    private suspend fun withExponentialBackoff(
        block: suspend (attempt: Int) -> DispatchResult
    ): DispatchResult {
        var attempt = 0
        var delayMs = config.initialRetryDelayMs
        var lastResult: DispatchResult? = null

        while (attempt < config.maxRetries) {
            attempt++

            val result = block(attempt)

            // 성공 또는 재시도 불가능한 실패면 즉시 반환
            when (result) {
                is DispatchResult.Success -> return result
                is DispatchResult.Failure -> {
                    if (!result.isRetryable || attempt >= config.maxRetries) {
                        return result
                    }
                    lastResult = result
                }
                is DispatchResult.CircuitOpen,
                is DispatchResult.RateLimited -> return result
                is DispatchResult.Timeout -> {
                    if (attempt >= config.maxRetries) {
                        return result
                    }
                    lastResult = result
                }
            }

            // 지터를 포함한 대기
            val jitter = (delayMs * config.jitterFactor * Random.nextDouble(-1.0, 1.0)).toLong()
            val actualDelay = (delayMs + jitter).coerceIn(0, config.maxRetryDelayMs)
            delay(actualDelay)

            // 다음 대기 시간 계산
            delayMs = minOf((delayMs * config.retryMultiplier).toLong(), config.maxRetryDelayMs)
        }

        return lastResult ?: DispatchResult.Failure(
            statusCode = null,
            errorMessage = "Max retries exceeded",
            responseBody = null,
            latencyMs = null,
            isRetryable = false
        )
    }

    /**
     * HTTP 요청 실행
     */
    private suspend fun executeRequest(
        webhook: Webhook,
        payload: WebhookEventPayload,
        attempt: Int
    ): DispatchResult {
        val startTime = System.currentTimeMillis()
        val requestBody = json.encodeToString(payload.toMap())

        try {
            val response = httpClient.post(webhook.url) {
                contentType(ContentType.Application.Json)

                // 기본 헤더
                header("User-Agent", "IVM-Lite-Webhook/1.0")
                header("X-Webhook-Id", webhook.id.toString())
                header("X-Webhook-Event", payload.eventType.name)
                header("X-Webhook-Timestamp", Instant.now().epochSecond.toString())
                header("X-Webhook-Attempt", attempt.toString())

                // HMAC 서명
                webhook.secretToken?.let { secret ->
                    val signature = signPayload(secret, requestBody)
                    header("X-Webhook-Signature", signature)
                }

                // 커스텀 헤더
                webhook.headers.forEach { (key, value) ->
                    header(key, value)
                }

                setBody(requestBody)
            }

            val latencyMs = (System.currentTimeMillis() - startTime).toInt()
            val responseBody = response.bodyAsText().take(10_000) // 10KB 제한
            val responseHeaders = response.headers.entries()
                .associate { it.key to it.value.firstOrNull().orEmpty() }

            return if (response.status.isSuccess()) {
                DispatchResult.Success(
                    statusCode = response.status.value,
                    responseBody = responseBody,
                    responseHeaders = responseHeaders,
                    latencyMs = latencyMs
                )
            } else {
                DispatchResult.Failure(
                    statusCode = response.status.value,
                    errorMessage = "HTTP ${response.status.value}: ${response.status.description}",
                    responseBody = responseBody,
                    latencyMs = latencyMs,
                    isRetryable = response.status.value in RETRYABLE_STATUS_CODES
                )
            }
        } catch (e: HttpRequestTimeoutException) {
            return DispatchResult.Timeout(config.requestTimeoutMs)
        } catch (e: Exception) {
            val latencyMs = (System.currentTimeMillis() - startTime).toInt()
            return DispatchResult.Failure(
                statusCode = null,
                errorMessage = "${e::class.simpleName}: ${e.message}",
                responseBody = null,
                latencyMs = latencyMs,
                isRetryable = isRetryableException(e)
            )
        }
    }

    /**
     * HMAC-SHA256 서명
     */
    private fun signPayload(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return "sha256=${hash.toHex()}"
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /**
     * 재시도 가능한 예외인지 판단
     */
    private fun isRetryableException(e: Exception): Boolean {
        return when (e) {
            is java.net.ConnectException,
            is java.net.SocketTimeoutException,
            is java.net.UnknownHostException,
            is java.io.IOException -> true
            else -> false
        }
    }

    override fun getCircuitState(webhookId: UUID): CircuitState {
        return circuitBreakers[webhookId]?.getState() ?: CircuitState.CLOSED
    }

    override fun resetCircuit(webhookId: UUID) {
        circuitBreakers[webhookId]?.reset()
    }

    override fun getDispatcherStats(): DispatcherStats {
        val openCount = circuitBreakers.values.count { it.getState() == CircuitState.OPEN }

        return DispatcherStats(
            totalDispatched = totalDispatched.get(),
            successCount = successCount.get(),
            failureCount = failureCount.get(),
            circuitOpenCount = circuitOpenCount.get(),
            rateLimitedCount = rateLimitedCount.get(),
            timeoutCount = timeoutCount.get(),
            activeCircuitBreakers = circuitBreakers.size,
            openCircuitBreakers = openCount
        )
    }

    /**
     * 리소스 정리
     */
    fun close() {
        httpClient.close()
    }

    companion object {
        /** 재시도 가능한 HTTP 상태 코드 */
        private val RETRYABLE_STATUS_CODES = setOf(
            408, // Request Timeout
            425, // Too Early
            429, // Too Many Requests
            500, // Internal Server Error
            502, // Bad Gateway
            503, // Service Unavailable
            504  // Gateway Timeout
        )
    }
}
