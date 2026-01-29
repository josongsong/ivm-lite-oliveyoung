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
    @Suppress("DEPRECATION")
    fun `RFC-IMPL-013 이후 모든 ship 조합은 정상 (ship_sync deprecated)`() {
        // RFC-IMPL-013: ship.sync 제거됨, validator에서 검증 불필요
        val spec = DeploySpec(
            compileMode = CompileMode.Async,
            shipSpec = ShipSpec(ShipMode.Sync, emptyList()),
            cutoverMode = CutoverMode.Ready
        )

        val errors = AxisValidator.validate(spec)

        // RFC-IMPL-013: 모든 ship은 outbox를 통해 처리되므로 에러 없음
        assertTrue(errors.isEmpty())
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
