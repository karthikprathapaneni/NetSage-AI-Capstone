package com.netsage.ai.domain.model

enum class MeasurementState(val statusLabel: String) {
    INITIALIZING("Initializing Telemetry Engine..."),
    ACTIVE("Live Telemetry Active"),
    PROBING("Active Probe in Progress..."),
    PARTIAL_TELEMETRY("Basic Telemetry (Elevated Permissions Recommended)"),
    OFFLINE("Device Offline - No Network Available"),
    ERROR("Measurement Error Encountered")
}
