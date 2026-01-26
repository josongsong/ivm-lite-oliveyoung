package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.model.OpenSearchSinkSpec
import com.oliveyoung.ivmlite.sdk.model.ShipMode
import com.oliveyoung.ivmlite.sdk.model.ShipSpec
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShipAsyncOnlyAccessorTest {

    @Test
    fun `async() 호출 시 ShipMode_Async로 ShipSpec 생성`() {
        var capturedSpec: ShipSpec? = null
        val accessor = ShipAsyncOnlyAccessor { capturedSpec = it }

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
    fun `async() - 여러 sink 설정`() {
        var capturedSpec: ShipSpec? = null
        val accessor = ShipAsyncOnlyAccessor { capturedSpec = it }

        accessor.async {
            opensearch {
                index("products")
            }
            personalize {
                datasetArn("arn:aws:personalize:us-east-1:123456789012:dataset/test")
            }
        }

        assertEquals(ShipMode.Async, capturedSpec?.mode)
        assertEquals(2, capturedSpec?.sinks?.size)
    }

    @Test
    fun `async() - 빈 sink 설정`() {
        var capturedSpec: ShipSpec? = null
        val accessor = ShipAsyncOnlyAccessor { capturedSpec = it }

        accessor.async {
            // 빈 설정
        }

        assertEquals(ShipMode.Async, capturedSpec?.mode)
        assertTrue(capturedSpec?.sinks?.isEmpty() ?: false)
    }

    @Test
    fun `ShipAsyncOnlyAccessor는 sync 메서드를 제공하지 않음 - 컴파일 타임 체크`() {
        // 이 테스트는 컴파일 타임에 타입 안정성을 보장함을 문서화
        // ShipAsyncOnlyAccessor에는 sync() 메서드가 없음
        val accessor = ShipAsyncOnlyAccessor { }

        // accessor.sync { } // 컴파일 에러 발생

        // 런타임에서는 타입 확인만 수행
        assertTrue(accessor is ShipAsyncOnlyAccessor)
    }
}
