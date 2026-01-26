package com.oliveyoung.ivmlite.sdk.validation

import com.oliveyoung.ivmlite.sdk.model.CompileMode
import com.oliveyoung.ivmlite.sdk.model.CutoverMode
import com.oliveyoung.ivmlite.sdk.model.DeploySpec
import com.oliveyoung.ivmlite.sdk.model.ShipMode
import com.oliveyoung.ivmlite.sdk.model.ShipSpec
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AxisValidationTest {

    @Test
    fun `compile_async + ship_sync 조합은 에러 반환`() {
        val spec = DeploySpec(
            compileMode = CompileMode.Async,
            shipSpec = ShipSpec(ShipMode.Sync, emptyList()),
            cutoverMode = CutoverMode.Ready
        )

        val errors = AxisValidator.validate(spec)

        assertEquals(1, errors.size)
        assertEquals("compile.async + ship.sync is not allowed", errors[0])
    }

    @Test
    fun `compile_sync + ship_sync 조합은 정상`() {
        val spec = DeploySpec(
            compileMode = CompileMode.Sync,
            shipSpec = ShipSpec(ShipMode.Sync, emptyList()),
            cutoverMode = CutoverMode.Ready
        )

        val errors = AxisValidator.validate(spec)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `compile_sync + ship_async 조합은 정상`() {
        val spec = DeploySpec(
            compileMode = CompileMode.Sync,
            shipSpec = ShipSpec(ShipMode.Async, emptyList()),
            cutoverMode = CutoverMode.Ready
        )

        val errors = AxisValidator.validate(spec)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `compile_async + ship_async 조합은 정상`() {
        val spec = DeploySpec(
            compileMode = CompileMode.Async,
            shipSpec = ShipSpec(ShipMode.Async, emptyList()),
            cutoverMode = CutoverMode.Ready
        )

        val errors = AxisValidator.validate(spec)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `shipSpec null인 경우 정상`() {
        val spec = DeploySpec(
            compileMode = CompileMode.Async,
            shipSpec = null,
            cutoverMode = CutoverMode.Ready
        )

        val errors = AxisValidator.validate(spec)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `SyncWithTargets + ship_sync 조합은 정상`() {
        val spec = DeploySpec(
            compileMode = CompileMode.SyncWithTargets(emptyList()),
            shipSpec = ShipSpec(ShipMode.Sync, emptyList()),
            cutoverMode = CutoverMode.Ready
        )

        val errors = AxisValidator.validate(spec)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `SyncWithTargets + ship_async 조합은 정상`() {
        val spec = DeploySpec(
            compileMode = CompileMode.SyncWithTargets(emptyList()),
            shipSpec = ShipSpec(ShipMode.Async, emptyList()),
            cutoverMode = CutoverMode.Ready
        )

        val errors = AxisValidator.validate(spec)

        assertTrue(errors.isEmpty())
    }
}
