package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.model.CompileMode
import com.oliveyoung.ivmlite.sdk.model.ShipMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DeployAsyncBuilderTest {

    @Test
    fun `DeployAsyncBuilder - compile은 항상 async`() {
        val builder = DeployAsyncBuilder()

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
    fun `DeployAsyncBuilder - ship은 async만 지원`() {
        val builder = DeployAsyncBuilder()

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
    fun `DeployAsyncBuilder - cutover 설정 가능`() {
        val builder = DeployAsyncBuilder()

        builder.compile.async()
        builder.ship.async {
            opensearch {
                index("products")
            }
        }
        builder.cutover.done()

        val spec = builder.build()

        assertEquals(CompileMode.Async, spec.compileMode)
    }
}
