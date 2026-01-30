package com.oliveyoung.ivmlite.pkg.webhooks.adapters

import com.oliveyoung.ivmlite.pkg.webhooks.domain.Webhook
import com.oliveyoung.ivmlite.pkg.webhooks.domain.WebhookEventPayload
import com.oliveyoung.ivmlite.pkg.webhooks.ports.CircuitState
import com.oliveyoung.ivmlite.pkg.webhooks.ports.DispatchResult
import com.oliveyoung.ivmlite.pkg.webhooks.ports.RateLimitState
import com.oliveyoung.ivmlite.pkg.webhooks.ports.WebhookDispatcherPort
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HTTP Webhook Dispatcher (SOTA)
 *
 * SOTA급 웹훅 HTTP 전송 구현:
 * - Circuit Breaker: 장애 전파 차단
 * - Rate Limiter: 외부 서비스 보호
 * - HMAC-SHA256: 페이로드 서명
 * - Connection Pooling: 효율적인 커넥션 재사용
 */
class HttpWebhookDispatcher(
    private val circuitBreakerConfig: CircuitBreaker.Config = CircuitBreaker.Config(),
    private val rateLimiterConfig: RateLimiter.Config = RateLimiter.Config()
) : WebhookDispatcherPort {

    // Per-webhook Circuit Breakers
    private val circuitBreakers = ConcurrentHashMap<UUID, CircuitBreaker>()

    // Per-webhook Rate Limiters
    private val rateLimiters = ConcurrentHashMap<UUID, RateLimiter>()

    // HTTP Client with connection pooling and timeouts
    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 10_000
        }
        engine {
            maxConnectionsCount = 100
        }
    }

    override suspend fun dispatch(
        webhook: Webhook,
        payload: WebhookEventPayload
    ): DispatchResult {
        // Circuit Breaker 체크
        val breaker = circuitBreakers.getOrPut(webhook.id) {
            CircuitBreaker(circuitBreakerConfig)
        }
        if (breaker.isOpen()) {
            return DispatchResult.CircuitOpen
        }

        // Rate Limiter 체크
        val limiter = rateLimiters.getOrPut(webhook.id) {
            RateLimiter(rateLimiterConfig)
        }
        if (!limiter.tryAcquire()) {
            return DispatchResult.RateLimited
        }

        return executeRequest(webhook, payload, breaker)
    }

    override suspend fun testDispatch(
        webhook: Webhook,
        payload: WebhookEventPayload
    ): DispatchResult {
        // 테스트 전송은 Circuit Breaker/Rate Limiter 무시
        return executeRequest(webhook, payload, circuitBreaker = null)
    }

    private suspend fun executeRequest(
        webhook: Webhook,
        payload: WebhookEventPayload,
        circuitBreaker: CircuitBreaker?
    ): DispatchResult {
        val startTime = System.currentTimeMillis()
        val payloadJson = payload.toJson()

        return try {
            val response = httpClient.post(webhook.url) {
                contentType(ContentType.Application.Json)

                // 커스텀 헤더 추가
                headers {
                    webhook.headers.forEach { (key, value) ->
                        append(key, value)
                    }

                    // HMAC-SHA256 서명
                    webhook.secretToken?.let { secret ->
                        val signature = signPayload(secret, payloadJson)
                        append("X-Webhook-Signature", signature)
                    }

                    // 표준 웹훅 헤더
                    append("X-Webhook-Event", payload.event)
                    append("X-Webhook-Timestamp", Instant.now().epochSecond.toString())
                    append("X-Webhook-Delivery", payload.id)
                }

                setBody(payloadJson)
            }

            val latencyMs = (System.currentTimeMillis() - startTime).toInt()
            val responseBody = response.bodyAsText()

            if (response.status.value in 200..299) {
                circuitBreaker?.recordSuccess()
                DispatchResult.Success(
                    statusCode = response.status.value,
                    responseBody = responseBody.take(10_000),
                    latencyMs = latencyMs
                )
            } else {
                circuitBreaker?.recordFailure()
                DispatchResult.Failed(
                    statusCode = response.status.value,
                    errorMessage = "HTTP ${response.status.value}: ${response.status.description}",
                    latencyMs = latencyMs,
                    retryable = response.status.value in 500..599
                )
            }
        } catch (e: Exception) {
            val latencyMs = (System.currentTimeMillis() - startTime).toInt()
            circuitBreaker?.recordFailure()
            DispatchResult.Failed(
                statusCode = null,
                errorMessage = e.message ?: "Unknown error",
                latencyMs = latencyMs,
                retryable = true
            )
        }
    }

    override fun getCircuitState(webhookId: UUID): CircuitState {
        val breaker = circuitBreakers[webhookId]
        return if (breaker != null) {
            CircuitState(
                webhookId = webhookId,
                state = when (breaker.getState()) {
                    CircuitBreaker.State.CLOSED -> CircuitState.State.CLOSED
                    CircuitBreaker.State.OPEN -> CircuitState.State.OPEN
                    CircuitBreaker.State.HALF_OPEN -> CircuitState.State.HALF_OPEN
                },
                failureCount = breaker.getFailureCount(),
                successCount = breaker.getSuccessCount(),
                lastFailureTime = breaker.getLastFailureTime()
            )
        } else {
            CircuitState(
                webhookId = webhookId,
                state = CircuitState.State.CLOSED,
                failureCount = 0,
                successCount = 0,
                lastFailureTime = null
            )
        }
    }

    override fun resetCircuit(webhookId: UUID) {
        circuitBreakers[webhookId]?.reset()
    }

    override fun getRateLimitState(webhookId: UUID): RateLimitState {
        val limiter = rateLimiters[webhookId]
        return if (limiter != null) {
            RateLimitState(
                webhookId = webhookId,
                requestsPerSecond = limiter.getConfig().requestsPerSecond,
                burstCapacity = limiter.getConfig().burstCapacity,
                availableTokens = limiter.availableTokens()
            )
        } else {
            RateLimitState(
                webhookId = webhookId,
                requestsPerSecond = rateLimiterConfig.requestsPerSecond,
                burstCapacity = rateLimiterConfig.burstCapacity,
                availableTokens = rateLimiterConfig.burstCapacity
            )
        }
    }

    /**
     * HMAC-SHA256 서명 생성
     */
    private fun signPayload(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal(payload.toByteArray())
        return "sha256=${hash.joinToString("") { "%02x".format(it) }}"
    }

    /**
     * 리소스 정리
     */
    fun close() {
        httpClient.close()
    }
}
