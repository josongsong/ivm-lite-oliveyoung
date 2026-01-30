package com.oliveyoung.ivmlite.sdk.ops

import com.oliveyoung.ivmlite.pkg.orchestration.application.OutboxPollingWorker
import org.slf4j.LoggerFactory

/**
 * WorkerControl - Worker 제어
 *
 * Admin에서 Worker를 시작/중지할 수 있습니다.
 */
class WorkerControl(
    private val worker: OutboxPollingWorker?
) {
    private val logger = LoggerFactory.getLogger(WorkerControl::class.java)

    /**
     * Worker 실행 중 여부
     */
    val isRunning: Boolean
        get() = worker?.isRunning() ?: false

    /**
     * Worker 시작
     */
    fun start(): ControlResult {
        val w = worker
            ?: return ControlResult.NotConfigured("Worker not configured")

        return try {
            val started = w.start()
            if (started) {
                logger.info("[WorkerControl] Worker started")
                ControlResult.Started
            } else {
                ControlResult.AlreadyRunning
            }
        } catch (e: Exception) {
            logger.error("[WorkerControl] Failed to start worker", e)
            ControlResult.Error("Failed to start worker: ${e.message}")
        }
    }

    /**
     * Worker 중지
     */
    suspend fun stop(): ControlResult {
        val w = worker
            ?: return ControlResult.NotConfigured("Worker not configured")

        return try {
            val stopped = w.stop()
            if (stopped) {
                logger.info("[WorkerControl] Worker stopped")
                ControlResult.Stopped
            } else {
                ControlResult.AlreadyStopped
            }
        } catch (e: Exception) {
            logger.error("[WorkerControl] Failed to stop worker", e)
            ControlResult.Error("Failed to stop worker: ${e.message}")
        }
    }
}

/**
 * ControlResult - Worker 제어 결과
 */
sealed class ControlResult {
    abstract val success: Boolean
    abstract val message: String

    object Started : ControlResult() {
        override val success = true
        override val message = "Worker started successfully"
    }

    object Stopped : ControlResult() {
        override val success = true
        override val message = "Worker stopped successfully"
    }

    object AlreadyRunning : ControlResult() {
        override val success = false
        override val message = "Worker already running"
    }

    object AlreadyStopped : ControlResult() {
        override val success = false
        override val message = "Worker already stopped"
    }

    data class NotConfigured(override val message: String) : ControlResult() {
        override val success = false
    }

    data class Error(override val message: String) : ControlResult() {
        override val success = false
    }
}
