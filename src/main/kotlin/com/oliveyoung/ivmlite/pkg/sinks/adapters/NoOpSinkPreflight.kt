package com.oliveyoung.ivmlite.pkg.sinks.adapters

import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPreflightPort
import com.oliveyoung.ivmlite.shared.domain.types.Result

/**
 * NoOp Sink Preflight - 검증 스킵 (테스트/레거시용)
 *
 * 모든 target을 항상 통과시킴.
 */
object NoOpSinkPreflight : SinkPreflightPort {

    override suspend fun validate(targets: List<String>): Result<Unit> = Result.Ok(Unit)
}
