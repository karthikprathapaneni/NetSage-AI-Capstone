package com.netsage.ai.domain.model

data class LatencyMetrics(
    val rttMs: Long? = null,
    val dnsLookupMs: Long? = null,
    val tcpHandshakeMs: Long? = null,
    val httpLatencyMs: Long? = null,
    val jitterMs: Long? = null,
    val targetHost: String = "dns.google / generate_204",
    val isProbeSuccessful: Boolean = false,
    val probeError: String? = null
) {
    val latencyCategory: String
        get() = when {
            rttMs == null -> "No Measurement"
            rttMs < 30 -> "Ultra-Low (<30ms)"
            rttMs < 75 -> "Optimal (<75ms)"
            rttMs < 150 -> "Acceptable (<150ms)"
            rttMs < 300 -> "High Latency (<300ms)"
            else -> "Degraded (>300ms)"
        }
}
