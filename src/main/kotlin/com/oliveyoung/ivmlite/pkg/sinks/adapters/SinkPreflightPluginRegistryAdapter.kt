package com.oliveyoung.ivmlite.pkg.sinks.adapters

import com.oliveyoung.ivmlite.pkg.sinks.domain.SinkTargetType
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPluginRegistryPort
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPreflightPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result

/**
 * Sink Preflight Adapter - SinkPluginRegistry 기반 검증
 *
 * 각 target이 pluginRegistry.resolve(target)로 조회 가능한지 확인.
 * 미등록 시 환경변수 힌트와 함께 ConfigError 반환.
 */
class SinkPreflightPluginRegistryAdapter(
    private val pluginRegistry: SinkPluginRegistryPort,
) : SinkPreflightPort {

    override suspend fun validate(targets: List<String>): Result<Unit> {
        if (targets.isEmpty()) return Result.Ok(Unit)

        val unresolved = targets.filter { pluginRegistry.resolve(it) == null }
        if (unresolved.isEmpty()) return Result.Ok(Unit)

        val hint = unresolved.joinToString("; ") { target ->
            val type = SinkTargetType.fromPluginId(target)
            val envHint = type?.requiredEnvVar()?.let { " Set $it to enable." } ?: ""
            "'$target' is not registered.$envHint"
        }
        return Result.Err(
            DomainError.ConfigError(
                "Sink preflight failed: $hint"
            )
        )
    }
}
