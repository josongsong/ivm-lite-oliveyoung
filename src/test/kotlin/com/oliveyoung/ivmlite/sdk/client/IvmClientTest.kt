package com.oliveyoung.ivmlite.sdk.client

import com.oliveyoung.ivmlite.sdk.dsl.ingest.IngestContext
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IvmClientTest {

    @Test
    fun `Ivm client() 호출 가능`() {
        val client = Ivm.client()
        assertNotNull(client)
    }

    @Test
    fun `Ivm client() ingest() 체이닝 가능`() {
        val ingestContext = Ivm.client().ingest()
        assertNotNull(ingestContext)
        assertTrue(ingestContext is IngestContext)
    }

    @Test
    fun `IvmClientConfig 기본값 확인`() {
        val config = IvmClientConfig()
        assertEquals("http://localhost:8080", config.baseUrl)
        assertNull(config.tenantId)
        assertEquals(Duration.ofSeconds(30), config.timeout)
    }

    @Test
    fun `IvmClientConfig Builder로 설정 변경`() {
        val config = IvmClientConfig.Builder().apply {
            baseUrl("https://api.example.com")
            tenantId("tenant-123")
            timeout(Duration.ofSeconds(60))
        }.build()

        assertEquals("https://api.example.com", config.baseUrl)
        assertEquals("tenant-123", config.tenantId)
        assertEquals(Duration.ofSeconds(60), config.timeout)
    }

    @Test
    fun `Ivm configure로 글로벌 설정 변경`() {
        Ivm.configure {
            baseUrl("https://configured.example.com")
            tenantId("global-tenant")
            timeout(Duration.ofSeconds(45))
        }

        val client = Ivm.client()
        assertNotNull(client)
    }

    @Test
    fun `configure 후 client 생성 시 설정 반영 확인`() {
        Ivm.configure {
            baseUrl("https://test.example.com")
            tenantId("test-tenant")
        }

        val context = Ivm.client().ingest()
        assertNotNull(context)
    }
}
