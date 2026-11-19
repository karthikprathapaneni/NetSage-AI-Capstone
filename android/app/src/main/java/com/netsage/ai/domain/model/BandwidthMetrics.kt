package com.netsage.ai.domain.model

data class BandwidthMetrics(
    val downstreamEstimateKbps: Int? = null,
    val upstreamEstimateKbps: Int? = null,
    val activeThroughputMbps: Double? = null,
    val isMetered: Boolean = false,
    val isMultipathCapable: Boolean = false
) {
    val downstreamMbpsFormatted: String
        get() = when {
            activeThroughputMbps != null && activeThroughputMbps > 0 -> String.format("%.2f Mbps (Active Probe)", activeThroughputMbps)
            downstreamEstimateKbps != null && downstreamEstimateKbps > 0 -> {
                val mbps = downstreamEstimateKbps / 1000.0
                String.format("%.1f Mbps (Link Est.)", mbps)
            }
            else -> "Unavailable"
        }

    val upstreamMbpsFormatted: String
        get() = when {
            upstreamEstimateKbps != null && upstreamEstimateKbps > 0 -> {
                val mbps = upstreamEstimateKbps / 1000.0
                String.format("%.1f Mbps (Link Est.)", mbps)
            }
            else -> "Unavailable"
        }
}
