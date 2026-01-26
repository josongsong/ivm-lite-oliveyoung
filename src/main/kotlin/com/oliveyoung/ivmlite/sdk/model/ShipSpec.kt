package com.oliveyoung.ivmlite.sdk.model

data class ShipSpec(
    val mode: ShipMode,
    val sinks: List<SinkSpec>
)
