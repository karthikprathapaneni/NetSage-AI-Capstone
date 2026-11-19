package com.netsage.ai.domain.model

enum class AdaptiveAction(val title: String, val rationale: String) {
    MAINTAIN_CURRENT(
        "Maintain Current Link",
        "Current network connection exhibits optimal throughput, low latency, and stable signal parameters."
    ),
    PREPARE_WIFI_HANDOFF(
        "Prepare Wi-Fi to Cellular Handoff",
        "Wi-Fi RSSI degradation or rising latency jitter detected. Prepare seamless socket failover."
    ),
    SWITCH_TO_CELLULAR(
        "Switch Traffic to Cellular",
        "Wi-Fi connection is degraded or packet round-trip time is unacceptable. Cellular link is superior."
    ),
    SWITCH_TO_WIFI(
        "Switch Traffic to Wi-Fi",
        "High-capacity Wi-Fi network detected with superior SNR and unmetered bandwidth."
    ),
    THROTTLE_BACKGROUND_TASKS(
        "Constrain Background Traffic",
        "Bandwidth saturation or cellular metering constraint detected. Prioritize critical foreground traffic."
    ),
    POOR_COVERAGE_ALERT(
        "Sub-optimal Coverage",
        "Low cellular and Wi-Fi reception. Offline-first caching advised."
    ),
    INSUFFICIENT_DATA(
        "Gathering Telemetry Baseline",
        "Accumulating historical observations to calibrate the adaptive decision engine."
    )
}

data class PredictionDecision(
    val qualityScore: Int, // 0..100
    val stabilityIndex: Int, // 0..100
    val action: AdaptiveAction,
    val confidence: Float, // 0.0 .. 1.0
    val latencyTrendDescription: String,
    val signalTrendDescription: String,
    val isMlModelReady: Boolean = false,
    val mlModelStatus: String = "Heuristic Baseline Active (ML Hook Ready)",
    val mlPredictedClass: String? = null,
    val mlConfidence: Float? = null,
    val isMlAssisted: Boolean = false,
    val featureVector: FloatArray? = null
)
