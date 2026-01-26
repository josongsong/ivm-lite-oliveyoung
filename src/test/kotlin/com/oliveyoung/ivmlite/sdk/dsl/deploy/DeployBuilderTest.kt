package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.model.CompileMode
import com.oliveyoung.ivmlite.sdk.model.ShipMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeployBuilderTest {

    @Test
    fun `deploy - compile_sync + ship_async 정상 동작`() {
        val builder = DeployBuilder()

        builder.compile.sync()
        builder.ship.async {
            opensearch {
                index("products")
            }
        }

        val spec = builder.build()

        assertEquals(CompileMode.Sync, spec.compileMode)
        assertNotNull(spec.shipSpec)
        assertEquals(ShipMode.Async, spec.shipSpec?.mode)
    }

    @Test
    fun `deploy - compile_async + ship_async 정상 동작`() {
        val builder = DeployBuilder()

        builder.compile.async()
        builder.ship.async {
            opensearch {
                index("products")
            }
        }

        val spec = builder.build()

        assertEquals(CompileMode.Async, spec.compileMode)
        assertNotNull(spec.shipSpec)
        assertEquals(ShipMode.Async, spec.shipSpec?.mode)
    }

    @Test
    fun `deploy - compile_async + ship_sync IllegalStateException 발생`() {
        val builder = DeployBuilder()

        builder.compile.async()
        builder.ship.sync {
            opensearch {
                index("products")
            }
        }

        val exception = assertThrows<IllegalStateException> {
            builder.build()
        }

        assertEquals(
            "Invalid axis combination: compile.async + ship.sync is not allowed. Use ship.async instead.",
            exception.message
        )
    }

    @Test
    fun `deploy - compile_sync + ship_sync 정상 동작`() {
        val builder = DeployBuilder()

        builder.compile.sync()
        builder.ship.sync {
            opensearch {
                index("products")
            }
        }

        val spec = builder.build()

        assertEquals(CompileMode.Sync, spec.compileMode)
        assertNotNull(spec.shipSpec)
        assertEquals(ShipMode.Sync, spec.shipSpec?.mode)
    }

    @Test
    fun `deploy - shipSpec 없이 정상 동작`() {
        val builder = DeployBuilder()

        builder.compile.sync()

        val spec = builder.build()

        assertEquals(CompileMode.Sync, spec.compileMode)
        assertNull(spec.shipSpec)
    }

    @Test
    fun `deploy - cutover 설정 포함`() {
        val builder = DeployBuilder()

        builder.compile.async()
        builder.ship.async {
            opensearch {
                index("products")
            }
        }
        builder.cutover.done()

        val spec = builder.build()

        assertEquals(CompileMode.Async, spec.compileMode)
        assertNotNull(spec.shipSpec)
    }
}
