package com.netsage.ai.domain.model

data class NetworkObservation(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val networkType: NetworkType = NetworkType.UNKNOWN,
    val transportTypeName: String = "Unknown",
    val isConnected: Boolean = false,
    val isValidated: Boolean = false,
    val interfaceName: String? = null,
    val signalMetrics: SignalMetrics = SignalMetrics(),
    val latencyMetrics: LatencyMetrics = LatencyMetrics(),
    val bandwidthMetrics: BandwidthMetrics = BandwidthMetrics(),
    val measurementState: MeasurementState = MeasurementState.INITIALIZING,
    val errorMessage: String? = null
) {
    val isOnline: Boolean
        get() = isConnected && networkType != NetworkType.NONE

    val effectiveLatencyMs: Long?
        get() = latencyMetrics.rttMs ?: latencyMetrics.httpLatencyMs

    val effectiveSignalDbm: Int?
        get() = signalMetrics.signalDbm
}
