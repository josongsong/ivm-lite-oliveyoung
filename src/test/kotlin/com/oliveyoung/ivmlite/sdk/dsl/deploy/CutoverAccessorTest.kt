package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.model.CutoverMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CutoverAccessorTest {

    @Test
    fun `ready() 호출 시 CutoverMode_Ready 설정`() {
        var capturedMode: CutoverMode? = null
        val accessor = CutoverAccessor { capturedMode = it }

        accessor.ready()

        assertEquals(CutoverMode.Ready, capturedMode)
    }

    @Test
    fun `done() 호출 시 CutoverMode_Done 설정`() {
        var capturedMode: CutoverMode? = null
        val accessor = CutoverAccessor { capturedMode = it }

        accessor.done()

        assertEquals(CutoverMode.Done, capturedMode)
    }

    @Test
    fun `여러 번 호출 시 마지막 설정값이 유지됨`() {
        var capturedMode: CutoverMode? = null
        val accessor = CutoverAccessor { capturedMode = it }

        accessor.ready()
        assertEquals(CutoverMode.Ready, capturedMode)

        accessor.done()
        assertEquals(CutoverMode.Done, capturedMode)

        accessor.ready()
        assertEquals(CutoverMode.Ready, capturedMode)
    }
}
