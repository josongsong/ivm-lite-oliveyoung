package com.oliveyoung.ivmlite.sdk

import com.oliveyoung.ivmlite.shared.config.KafkaConfig
import com.oliveyoung.ivmlite.shared.config.WorkerConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * IvmContext 단위 테스트
 *
 * Builder 패턴, 기본값, 검증 헬퍼 테스트
 */
class IvmContextTest {

    @Test
    fun `EMPTY context는 모든 의존성이 null`() {
        val ctx = IvmContext.EMPTY

        assertNull(ctx.executor)
        assertNull(ctx.queryWorkflow)
        assertNull(ctx.worker)
        assertNull(ctx.outboxRepository)
        assertNull(ctx.slicingWorkflow)
        assertNull(ctx.shipWorkflow)
        assertNull(ctx.sliceRepository)
    }

    @Test
    fun `builder로 빈 context 생성 가능`() {
        val ctx = IvmContext.builder().build()

        assertNotNull(ctx)
        assertNotNull(ctx.config)
        assertNotNull(ctx.kafkaConfig)
        assertNotNull(ctx.workerConfig)
    }

    @Test
    fun `kafkaConfig 설정 가능`() {
        val kafka = KafkaConfig(topicPrefix = "custom-prefix")
        val ctx = IvmContext.builder()
            .kafkaConfig(kafka)
            .build()

        assertEquals("custom-prefix", ctx.kafkaConfig.topicPrefix)
    }

    @Test
    fun `workerConfig 설정 가능`() {
        val worker = WorkerConfig(batchSize = 100)
        val ctx = IvmContext.builder()
            .workerConfig(worker)
            .build()

        assertEquals(100, ctx.workerConfig.batchSize)
    }

    @Test
    fun `canDeploy는 executor가 없으면 false`() {
        val ctx = IvmContext.builder().build()

        assertFalse(ctx.canDeploy)
    }

    @Test
    fun `canQuery는 queryWorkflow가 없으면 false`() {
        val ctx = IvmContext.builder().build()

        assertFalse(ctx.canQuery)
    }

    @Test
    fun `canControlWorker는 worker가 없으면 false`() {
        val ctx = IvmContext.builder().build()

        assertFalse(ctx.canControlWorker)
    }

    @Test
    fun `canConsume는 outboxRepository가 없으면 false`() {
        val ctx = IvmContext.builder().build()

        assertFalse(ctx.canConsume)
    }

    @Test
    fun `canOps는 slicingWorkflow, shipWorkflow, sliceRepository 모두 필요`() {
        val ctx = IvmContext.builder().build()

        assertFalse(ctx.canOps)
    }

    @Test
    fun `requireExecutor는 executor가 없으면 예외 발생`() {
        val ctx = IvmContext.builder().build()

        val exception = assertFailsWith<IllegalStateException> {
            ctx.requireExecutor()
        }

        assertTrue(exception.message!!.contains("DeployExecutor not configured"))
    }

    @Test
    fun `requireQueryWorkflow는 queryWorkflow가 없으면 예외 발생`() {
        val ctx = IvmContext.builder().build()

        val exception = assertFailsWith<IllegalStateException> {
            ctx.requireQueryWorkflow()
        }

        assertTrue(exception.message!!.contains("QueryViewWorkflow not configured"))
    }

    @Test
    fun `requireWorker는 worker가 없으면 예외 발생`() {
        val ctx = IvmContext.builder().build()

        val exception = assertFailsWith<IllegalStateException> {
            ctx.requireWorker()
        }

        assertTrue(exception.message!!.contains("OutboxPollingWorker not configured"))
    }

    @Test
    fun `requireOutboxRepository는 outboxRepository가 없으면 예외 발생`() {
        val ctx = IvmContext.builder().build()

        val exception = assertFailsWith<IllegalStateException> {
            ctx.requireOutboxRepository()
        }

        assertTrue(exception.message!!.contains("OutboxRepository not configured"))
    }

    @Test
    fun `requireSlicingWorkflow는 slicingWorkflow가 없으면 예외 발생`() {
        val ctx = IvmContext.builder().build()

        val exception = assertFailsWith<IllegalStateException> {
            ctx.requireSlicingWorkflow()
        }

        assertTrue(exception.message!!.contains("SlicingWorkflow not configured"))
    }

    @Test
    fun `requireShipWorkflow는 shipWorkflow가 없으면 예외 발생`() {
        val ctx = IvmContext.builder().build()

        val exception = assertFailsWith<IllegalStateException> {
            ctx.requireShipWorkflow()
        }

        assertTrue(exception.message!!.contains("ShipWorkflow not configured"))
    }

    @Test
    fun `requireSliceRepository는 sliceRepository가 없으면 예외 발생`() {
        val ctx = IvmContext.builder().build()

        val exception = assertFailsWith<IllegalStateException> {
            ctx.requireSliceRepository()
        }

        assertTrue(exception.message!!.contains("SliceRepository not configured"))
    }

    @Test
    fun `IvmContext는 data class라서 equals 동작`() {
        val ctx1 = IvmContext.builder()
            .kafkaConfig(KafkaConfig(topicPrefix = "test"))
            .build()
        val ctx2 = IvmContext.builder()
            .kafkaConfig(KafkaConfig(topicPrefix = "test"))
            .build()

        assertEquals(ctx1, ctx2)
    }

    @Test
    fun `IvmContext는 immutable - 변경 불가`() {
        val ctx = IvmContext.builder().build()

        // data class라서 copy로만 변경 가능
        val newCtx = ctx.copy(kafkaConfig = KafkaConfig(topicPrefix = "new"))

        // 원본 불변 (기본값 "ivm")
        assertEquals("ivm", ctx.kafkaConfig.topicPrefix)
        assertEquals("new", newCtx.kafkaConfig.topicPrefix)
    }
}
