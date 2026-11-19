package com.netsage.ai.domain.model

data class SignalMetrics(
    val signalDbm: Int? = null,
    val signalLevel: Int = 0, // 0..4 bars
    val wifiSsid: String? = null,
    val wifiBssid: String? = null,
    val wifiFrequencyMhz: Int? = null,
    val wifiLinkSpeedMbps: Int? = null,
    val cellularCarrier: String? = null,
    val cellularNetworkType: String? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val rssi: Int? = null,
    val cqi: Int? = null,
    val isLocationPermissionGranted: Boolean = false,
    val isPhoneStatePermissionGranted: Boolean = false
) {
    val wifiBandLabel: String?
        get() = when {
            wifiFrequencyMhz == null -> null
            wifiFrequencyMhz in 2400..2499 -> "2.4 GHz"
            wifiFrequencyMhz in 4900..5900 -> "5 GHz"
            wifiFrequencyMhz > 5925 -> "6 GHz (Wi-Fi 6E/7)"
            else -> "$wifiFrequencyMhz MHz"
        }

    val signalQualityDescription: String
        get() = when (signalLevel) {
            4 -> "Excellent"
            3 -> "Good"
            2 -> "Fair"
            1 -> "Poor"
            else -> if (signalDbm != null) "Weak" else "Unavailable"
        }
}
