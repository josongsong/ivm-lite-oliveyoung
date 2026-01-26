package com.oliveyoung.ivmlite.sdk.dsl.sink

import com.oliveyoung.ivmlite.sdk.model.OpenSearchSinkSpec
import com.oliveyoung.ivmlite.sdk.model.PersonalizeSinkSpec
import com.oliveyoung.ivmlite.sdk.model.SinkSpec
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SinkBuilderTest {

    @Test
    fun `opensearch 기본 호출`() {
        val builder = SinkBuilder()
        builder.opensearch()

        val sinks = builder.build()
        assertEquals(1, sinks.size)
        assertTrue(sinks[0] is OpenSearchSinkSpec)

        val spec = sinks[0] as OpenSearchSinkSpec
        assertNull(spec.index)
        assertNull(spec.alias)
        assertEquals(1000, spec.batchSize)
    }

    @Test
    fun `opensearch - index와 batchSize 설정`() {
        val builder = SinkBuilder()
        builder.opensearch {
            index("products")
            batchSize(500)
        }

        val sinks = builder.build()
        assertEquals(1, sinks.size)

        val spec = sinks[0] as OpenSearchSinkSpec
        assertEquals("products", spec.index)
        assertNull(spec.alias)
        assertEquals(500, spec.batchSize)
    }

    @Test
    fun `opensearch - 모든 속성 설정`() {
        val builder = SinkBuilder()
        builder.opensearch {
            index("products")
            alias("products-alias")
            batchSize(2000)
        }

        val sinks = builder.build()
        assertEquals(1, sinks.size)

        val spec = sinks[0] as OpenSearchSinkSpec
        assertEquals("products", spec.index)
        assertEquals("products-alias", spec.alias)
        assertEquals(2000, spec.batchSize)
    }

    @Test
    fun `personalize 기본 호출`() {
        val builder = SinkBuilder()
        builder.personalize()

        val sinks = builder.build()
        assertEquals(1, sinks.size)
        assertTrue(sinks[0] is PersonalizeSinkSpec)

        val spec = sinks[0] as PersonalizeSinkSpec
        assertNull(spec.datasetArn)
        assertNull(spec.roleArn)
    }

    @Test
    fun `personalize - ARN 설정`() {
        val builder = SinkBuilder()
        builder.personalize {
            datasetArn("arn:aws:personalize:us-east-1:123456789012:dataset/test")
            roleArn("arn:aws:iam::123456789012:role/PersonalizeRole")
        }

        val sinks = builder.build()
        assertEquals(1, sinks.size)

        val spec = sinks[0] as PersonalizeSinkSpec
        assertEquals("arn:aws:personalize:us-east-1:123456789012:dataset/test", spec.datasetArn)
        assertEquals("arn:aws:iam::123456789012:role/PersonalizeRole", spec.roleArn)
    }

    @Test
    fun `여러 sink 동시 등록`() {
        val builder = SinkBuilder()
        builder.opensearch {
            index("products")
            batchSize(500)
        }
        builder.personalize {
            datasetArn("arn:aws:personalize:us-east-1:123456789012:dataset/test")
        }
        builder.opensearch {
            index("categories")
            alias("cat-alias")
        }

        val sinks = builder.build()
        assertEquals(3, sinks.size)

        assertTrue(sinks[0] is OpenSearchSinkSpec)
        assertTrue(sinks[1] is PersonalizeSinkSpec)
        assertTrue(sinks[2] is OpenSearchSinkSpec)

        val os1 = sinks[0] as OpenSearchSinkSpec
        assertEquals("products", os1.index)
        assertEquals(500, os1.batchSize)

        val ps = sinks[1] as PersonalizeSinkSpec
        assertEquals("arn:aws:personalize:us-east-1:123456789012:dataset/test", ps.datasetArn)

        val os2 = sinks[2] as OpenSearchSinkSpec
        assertEquals("categories", os2.index)
        assertEquals("cat-alias", os2.alias)
    }

    @Test
    fun `build()는 불변 리스트 반환`() {
        val builder = SinkBuilder()
        builder.opensearch {
            index("test")
        }

        val sinks1 = builder.build()
        val sinks2 = builder.build()

        assertEquals(sinks1.size, sinks2.size)
        assertEquals(1, sinks1.size)
    }

    @Test
    fun `빈 빌더는 빈 리스트 반환`() {
        val builder = SinkBuilder()
        val sinks = builder.build()

        assertTrue(sinks.isEmpty())
    }
}
