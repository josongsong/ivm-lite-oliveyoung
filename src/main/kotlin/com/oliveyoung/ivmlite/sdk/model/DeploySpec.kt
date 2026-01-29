package com.oliveyoung.ivmlite.sdk.model

/**
 * DeploySpec - 배포 명세 (RFC-IMPL-013: Ship 필수화)
 *
 * Ship 누락 리스크를 타입 레벨에서 차단:
 * - Full: compile + ship 필수 (기본)
 * - CompileOnly: compile만 수행 (명시적 선언 필요)
 *
 * 사용 예시:
 * ```kotlin
 * // 일반적인 배포 (ship 필수)
 * ivm.product(product).deploy {
 *     compile.sync()
 *     ship.async { opensearch() }  // 필수!
 * }
 *
 * // compile만 수행 (명시적으로 선언)
 * ivm.product(product).compileOnly {
 *     compile.sync()
 * }
 * ```
 */
sealed class DeploySpec {
    abstract val compileMode: CompileMode
    abstract val cutoverMode: CutoverMode

    /**
     * Full Deploy: compile + ship 필수
     * Ship이 없으면 컴파일 에러!
     */
    data class Full(
        override val compileMode: CompileMode = CompileMode.Sync,
        val ship: ShipSpec,  // 필수! (non-nullable)
        override val cutoverMode: CutoverMode = CutoverMode.Ready
    ) : DeploySpec()

    /**
     * Compile Only: compile만 수행, ship 없음
     * 명시적으로 선언해야만 사용 가능
     *
     * 사용 사례:
     * - 테스트/디버깅
     * - Ship을 나중에 별도로 트리거하는 경우
     * - 배치 처리 후 일괄 Ship
     */
    data class CompileOnly(
        override val compileMode: CompileMode = CompileMode.Sync,
        override val cutoverMode: CutoverMode = CutoverMode.Ready
    ) : DeploySpec()

    // === Backward Compatibility ===

    /**
     * Legacy 호환용 shipSpec getter
     * Full이면 shipSpec 반환, CompileOnly이면 null
     */
    val shipSpec: ShipSpec?
        get() = when (this) {
            is Full -> ship
            is CompileOnly -> null
        }

    companion object {
        /**
         * Legacy 생성자 (기존 코드 호환)
         * shipSpec이 null이면 CompileOnly, 아니면 Full
         */
        @JvmStatic
        operator fun invoke(
            compileMode: CompileMode = CompileMode.Sync,
            shipSpec: ShipSpec? = null,
            cutoverMode: CutoverMode = CutoverMode.Ready
        ): DeploySpec {
            return if (shipSpec != null) {
                Full(compileMode, shipSpec, cutoverMode)
            } else {
                CompileOnly(compileMode, cutoverMode)
            }
        }
    }
}
