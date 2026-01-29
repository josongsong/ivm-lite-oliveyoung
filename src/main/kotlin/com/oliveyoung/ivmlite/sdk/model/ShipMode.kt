package com.oliveyoung.ivmlite.sdk.model

/**
 * ShipMode - RFC-IMPL-013: Ship은 항상 Outbox를 통해 비동기 처리
 *
 * Sync 모드 제거됨. 모든 ship은 outbox를 통해 처리됩니다.
 *
 * @deprecated ShipMode는 더 이상 사용되지 않습니다. 모든 ship은 자동으로 async(outbox)입니다.
 */
@Deprecated("ShipMode는 더 이상 사용되지 않습니다. 모든 ship은 자동으로 async(outbox)입니다.")
enum class ShipMode {
    @Deprecated("Sync 모드는 제거되었습니다. 모든 ship은 outbox를 통해 처리됩니다.")
    Sync,
    Async
}
