package com.oliveyoung.ivmlite.sdk.dsl.deploy

import com.oliveyoung.ivmlite.sdk.model.CompileMode
import com.oliveyoung.ivmlite.sdk.model.DeploySpec
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * DeployBuilder 테스트 (RFC-IMPL-013 업데이트)
 *
 * 핵심 변경:
 * - ship 없이 deploy() 가능 (SinkRule 기반 자동 Ship)
 * - ship.to { } 는 선택적 override
 * - DeploySpec이 sealed class로 변경
 */
class DeployBuilderTest {

    @Test
    fun `deploy - ship 없이 빌드 시 CompileOnly 반환 (SinkRule 기반 자동 Ship)`() {
        val builder = DeployBuilder()
        builder.compile.sync()

        val spec = builder.build()

        // ship 없으면 CompileOnly (OutboxPollingWorker에서 SinkRule 기반 자동 Ship)
        assertTrue(spec is DeploySpec.CompileOnly)
        assertEquals(CompileMode.Sync, spec.compileMode)
    }

    @Test
    fun `deploy - ship_to 명시 시 Full 반환 (명시적 override)`() {
        val builder = DeployBuilder()
        builder.compile.sync()
        builder.ship.to {
            opensearch {
                index("products")
            }
        }

        val spec = builder.build()

        assertTrue(spec is DeploySpec.Full)
        val fullSpec = spec as DeploySpec.Full
        assertEquals(CompileMode.Sync, fullSpec.compileMode)
        assertNotNull(fullSpec.ship)
    }

    @Test
    fun `deploy - compile_async + ship_to 정상 동작`() {
        val builder = DeployBuilder()
        builder.compile.async()
        builder.ship.to {
            opensearch {
                index("products")
            }
        }

        val spec = builder.build()

        assertTrue(spec is DeploySpec.Full)
        assertEquals(CompileMode.Async, spec.compileMode)
    }

    @Test
    fun `deploy - ship_async는 ship_to와 동일 동작 (하위 호환)`() {
        val builder = DeployBuilder()
        builder.compile.sync()
        builder.ship.async {
            opensearch {
                index("products")
            }
        }

        val spec = builder.build()

        assertTrue(spec is DeploySpec.Full)
    }

    @Test
    fun `deploy - cutover 설정 포함`() {
        val builder = DeployBuilder()
        builder.compile.async()
        builder.ship.to {
            opensearch {
                index("products")
            }
        }
        builder.cutover.done()

        val spec = builder.build()

        assertTrue(spec is DeploySpec.Full)
        assertEquals(CompileMode.Async, spec.compileMode)
    }

    @Test
    fun `compileOnly - ship 없이 CompileOnly 반환`() {
        val builder = CompileOnlyBuilder()
        builder.compile.sync()

        val spec = builder.build()

        assertTrue(spec is DeploySpec.CompileOnly)
        assertEquals(CompileMode.Sync, spec.compileMode)
    }

    @Test
    fun `compileOnly - async 모드`() {
        val builder = CompileOnlyBuilder()
        builder.compile.async()

        val spec = builder.build()

        assertTrue(spec is DeploySpec.CompileOnly)
        assertEquals(CompileMode.Async, spec.compileMode)
    }
}
