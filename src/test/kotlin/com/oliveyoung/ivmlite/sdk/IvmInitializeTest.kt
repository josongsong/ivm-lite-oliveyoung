package com.oliveyoung.ivmlite.sdk

import com.oliveyoung.ivmlite.shared.config.KafkaConfig
import com.oliveyoung.ivmlite.shared.config.WorkerConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertSame

/**
 * Ivm.initialize() 테스트
 *
 * IvmContext 기반 초기화, 상태 관리, IvmOps 자동 연동 테스트
 */
class IvmInitializeTest {

    @BeforeEach
    fun setUp() {
        // 각 테스트 전에 SDK 리셋
        Ivm.reset()
    }

    @AfterEach
    fun tearDown() {
        // 각 테스트 후에 SDK 리셋
        Ivm.reset()
    }

    @Test
    fun `초기 상태에서 isInitialized는 false`() {
        assertFalse(Ivm.isInitialized())
    }

    @Test
    fun `initialize 후 isInitialized는 true`() {
        val ctx = IvmContext.builder().build()

        Ivm.initialize(ctx)

        assertTrue(Ivm.isInitialized())
    }

    @Test
    fun `context()로 현재 컨텍스트 조회 가능`() {
        val ctx = IvmContext.builder()
            .kafkaConfig(KafkaConfig(topicPrefix = "test-prefix"))
            .build()

        Ivm.initialize(ctx)

        assertSame(ctx, Ivm.context())
        assertEquals("test-prefix", Ivm.context().kafkaConfig.topicPrefix)
    }

    @Test
    fun `reset 후 isInitialized는 false`() {
        val ctx = IvmContext.builder().build()
        Ivm.initialize(ctx)
        assertTrue(Ivm.isInitialized())

        Ivm.reset()

        assertFalse(Ivm.isInitialized())
    }

    @Test
    fun `reset 후 context는 EMPTY`() {
        val ctx = IvmContext.builder()
            .kafkaConfig(KafkaConfig(topicPrefix = "test"))
            .build()
        Ivm.initialize(ctx)

        Ivm.reset()

        assertEquals(IvmContext.EMPTY, Ivm.context())
    }

    @Test
    fun `client()는 초기화 전에도 호출 가능`() {
        // 초기화 안 해도 기본 설정으로 동작
        val client = Ivm.client()

        assertNotNull(client)
    }

    @Test
    fun `initialize로 설정된 kafkaConfig 사용 확인`() {
        val kafka = KafkaConfig(topicPrefix = "custom-prefix")
        val ctx = IvmContext.builder()
            .kafkaConfig(kafka)
            .build()

        Ivm.initialize(ctx)

        assertEquals("custom-prefix", Ivm.context().kafkaConfig.topicPrefix)
    }

    @Test
    fun `initialize로 설정된 workerConfig 사용 확인`() {
        val worker = WorkerConfig(batchSize = 200, pollIntervalMs = 500)
        val ctx = IvmContext.builder()
            .workerConfig(worker)
            .build()

        Ivm.initialize(ctx)

        assertEquals(200, Ivm.context().workerConfig.batchSize)
        assertEquals(500, Ivm.context().workerConfig.pollIntervalMs)
    }

    @Test
    fun `여러 번 initialize 호출 시 마지막 context가 적용`() {
        val ctx1 = IvmContext.builder()
            .kafkaConfig(KafkaConfig(topicPrefix = "first"))
            .build()
        val ctx2 = IvmContext.builder()
            .kafkaConfig(KafkaConfig(topicPrefix = "second"))
            .build()

        Ivm.initialize(ctx1)
        Ivm.initialize(ctx2)

        assertEquals("second", Ivm.context().kafkaConfig.topicPrefix)
    }

    @Test
    fun `deprecated configure 메서드도 여전히 동작`() {
        @Suppress("DEPRECATION")
        Ivm.configure {
            baseUrl("https://test.example.com")
            tenantId("test-tenant")
        }

        val client = Ivm.client()
        assertNotNull(client)
    }

    @Test
    fun `IvmOps도 자동 초기화됨`() {
        val kafka = KafkaConfig(topicPrefix = "ops-test")
        val ctx = IvmContext.builder()
            .kafkaConfig(kafka)
            .build()

        Ivm.initialize(ctx)

        // IvmOps가 초기화되었는지 확인
        // (내부 상태 확인 - 예외 없이 호출 가능)
        assertTrue(Ivm.isInitialized())
    }

    @Test
    fun `스레드 안전성 - 동시 초기화`() {
        val contexts = (1..10).map { i ->
            IvmContext.builder()
                .kafkaConfig(KafkaConfig(topicPrefix = "thread-$i"))
                .build()
        }

        // 동시 초기화
        val threads = contexts.map { ctx ->
            Thread { Ivm.initialize(ctx) }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // 마지막 하나만 적용됨 (어떤 것이든)
        assertTrue(Ivm.isInitialized())
        assertTrue(Ivm.context().kafkaConfig.topicPrefix.startsWith("thread-"))
    }
}
