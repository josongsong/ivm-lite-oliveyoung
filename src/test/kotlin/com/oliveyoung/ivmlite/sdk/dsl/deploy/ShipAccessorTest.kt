package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.model.OpenSearchSinkSpec
import com.oliveyoung.ivmlite.sdk.model.ShipMode
import com.oliveyoung.ivmlite.sdk.model.ShipSpec
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShipAccessorTest {

    @Test
    fun `sync() 호출 시 ShipMode_Sync로 ShipSpec 생성`() {
        var capturedSpec: ShipSpec? = null
        val accessor = ShipAccessor { capturedSpec = it }

        accessor.sync {
            opensearch {
                index("products")
            }
        }

        assertEquals(ShipMode.Sync, capturedSpec?.mode)
        assertEquals(1, capturedSpec?.sinks?.size)
        assertTrue(capturedSpec?.sinks?.get(0) is OpenSearchSinkSpec)
    }

    @Test
    fun `async() 호출 시 ShipMode_Async로 ShipSpec 생성`() {
        var capturedSpec: ShipSpec? = null
        val accessor = ShipAccessor { capturedSpec = it }

        accessor.async {
            opensearch {
                index("products")
            }
        }

        assertEquals(ShipMode.Async, capturedSpec?.mode)
        assertEquals(1, capturedSpec?.sinks?.size)
        assertTrue(capturedSpec?.sinks?.get(0) is OpenSearchSinkSpec)
    }

    @Test
    fun `sync() - 여러 sink 설정`() {
        var capturedSpec: ShipSpec? = null
        val accessor = ShipAccessor { capturedSpec = it }

        accessor.sync {
            opensearch {
                index("products")
            }
            personalize {
                datasetArn("arn:aws:personalize:us-east-1:123456789012:dataset/test")
            }
        }

        assertEquals(ShipMode.Sync, capturedSpec?.mode)
        assertEquals(2, capturedSpec?.sinks?.size)
    }

    @Test
    fun `async() - 빈 sink 설정`() {
        var capturedSpec: ShipSpec? = null
        val accessor = ShipAccessor { capturedSpec = it }

        accessor.async {
            // 빈 설정
        }

        assertEquals(ShipMode.Async, capturedSpec?.mode)
        assertTrue(capturedSpec?.sinks?.isEmpty() ?: false)
    }
}
