package com.oliveyoung.ivmlite.pkg.alerts.adapters

import com.oliveyoung.ivmlite.pkg.alerts.domain.Alert
import com.oliveyoung.ivmlite.pkg.alerts.domain.NotificationChannel
import com.oliveyoung.ivmlite.pkg.alerts.ports.NotifierPort
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Slack Webhook Notifier
 * 
 * Slack Incoming Webhook을 통해 Alert를 발송한다.
 */
class SlackNotifier(
    private val webhookUrl: String?,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
) : NotifierPort {
    
    private val logger = LoggerFactory.getLogger(SlackNotifier::class.java)
    private val json = Json { encodeDefaults = true }
    
    override val channel = NotificationChannel.SLACK
    
    override fun isEnabled(): Boolean = !webhookUrl.isNullOrBlank()
    
    override suspend fun send(alert: Alert): Boolean {
        if (!isEnabled()) {
            logger.debug("Slack notifier is disabled")
            return false
        }
        
        return try {
            val payload = alert.toSlackPayload()
            val body = json.encodeToString(payload)
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl!!))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                logger.info("Slack alert sent: {} [{}]", alert.name, alert.id)
                true
            } else {
                logger.error("Slack webhook failed: status={}, body={}", response.statusCode(), response.body())
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to send Slack alert: {}", e.message, e)
            false
        }
    }
    
    override suspend fun sendResolved(alert: Alert): Boolean {
        if (!isEnabled()) return false
        
        return try {
            val payload = mapOf(
                "attachments" to listOf(
                    mapOf(
                        "color" to "#28a745", // green
                        "blocks" to listOf(
                            mapOf(
                                "type" to "section",
                                "text" to mapOf(
                                    "type" to "mrkdwn",
                                    "text" to "✅ *[RESOLVED]* ${alert.name}\n${alert.description}"
                                )
                            ),
                            mapOf(
                                "type" to "context",
                                "elements" to listOf(
                                    mapOf(
                                        "type" to "mrkdwn",
                                        "text" to "Duration: ${alert.duration()}"
                                    )
                                )
                            )
                        )
                    )
                )
            )
            
            val body = json.encodeToString(payload)
            val request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl!!))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            logger.error("Failed to send Slack resolved alert: {}", e.message)
            false
        }
    }
}
