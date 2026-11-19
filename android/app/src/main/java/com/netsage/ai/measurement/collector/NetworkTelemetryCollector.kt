package com.netsage.ai.measurement.collector

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import com.netsage.ai.domain.model.BandwidthMetrics
import com.netsage.ai.domain.model.LatencyMetrics
import com.netsage.ai.domain.model.MeasurementState
import com.netsage.ai.domain.model.NetworkObservation
import com.netsage.ai.domain.model.NetworkType
import com.netsage.ai.domain.model.SignalMetrics
import com.netsage.ai.measurement.permission.PermissionManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkTelemetryCollector(
    private val context: Context,
    private val permissionManager: PermissionManager
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    fun observeNetworkTelemetry(): Flow<NetworkObservation> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(collectCurrentObservation(network))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(collectCurrentObservation(network, networkCapabilities))
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties
            ) {
                trySend(collectCurrentObservation(network, linkProperties = linkProperties))
            }

            override fun onLost(network: Network) {
                trySend(createOfflineObservation())
            }

            override fun onUnavailable() {
                trySend(createOfflineObservation())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Emit initial observation immediately
        trySend(collectCurrentObservation(connectivityManager.activeNetwork))

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                // Ignore callback unregistration exceptions if already unregistered
            }
        }
    }.distinctUntilChanged()

    fun collectCurrentObservation(
        activeNetwork: Network? = connectivityManager.activeNetwork,
        existingCapabilities: NetworkCapabilities? = null,
        linkProperties: LinkProperties? = null
    ): NetworkObservation {
        if (activeNetwork == null) {
            return createOfflineObservation()
        }

        val capabilities = existingCapabilities
            ?: connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return createOfflineObservation()

        val props = linkProperties ?: connectivityManager.getLinkProperties(activeNetwork)

        val networkType = determineNetworkType(capabilities)
        val isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val downstreamKbps = capabilities.linkDownstreamBandwidthKbps.takeIf { it > 0 }
        val upstreamKbps = capabilities.linkUpstreamBandwidthKbps.takeIf { it > 0 }

        val signalMetrics = collectSignalMetrics(capabilities, networkType)

        val bandwidthMetrics = BandwidthMetrics(
            downstreamEstimateKbps = downstreamKbps,
            upstreamEstimateKbps = upstreamKbps,
            activeThroughputMbps = null,
            isMetered = isMetered,
            isMultipathCapable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)
        )

        val hasTelemetryPermissions = permissionManager.hasAllTelemetryPermissions()
        val state = when {
            !isConnected -> MeasurementState.OFFLINE
            hasTelemetryPermissions -> MeasurementState.ACTIVE
            else -> MeasurementState.PARTIAL_TELEMETRY
        }

        return NetworkObservation(
            id = 0,
            timestamp = System.currentTimeMillis(),
            networkType = networkType,
            transportTypeName = networkType.displayName,
            isConnected = isConnected,
            isValidated = isValidated,
            interfaceName = props?.interfaceName,
            signalMetrics = signalMetrics,
            latencyMetrics = LatencyMetrics(),
            bandwidthMetrics = bandwidthMetrics,
            measurementState = state,
            errorMessage = null
        )
    }

    private fun determineNetworkType(capabilities: NetworkCapabilities): NetworkType {
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                determineCellularGeneration()
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            else -> NetworkType.UNKNOWN
        }
    }

    @SuppressLint("MissingPermission")
    private fun determineCellularGeneration(): NetworkType {
        if (telephonyManager == null) return NetworkType.CELLULAR_UNKNOWN
        if (!permissionManager.isPhoneStateGranted()) return NetworkType.CELLULAR_UNKNOWN

        return try {
            val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                telephonyManager.dataNetworkType
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.networkType
            }

            when (networkType) {
                TelephonyManager.NETWORK_TYPE_NR -> NetworkType.CELLULAR_5G
                TelephonyManager.NETWORK_TYPE_LTE -> NetworkType.CELLULAR_4G
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> NetworkType.CELLULAR_3G
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_CDMA -> NetworkType.CELLULAR_2G
                else -> NetworkType.CELLULAR_UNKNOWN
            }
        } catch (e: SecurityException) {
            NetworkType.CELLULAR_UNKNOWN
        }
    }

    private fun collectSignalMetrics(
        capabilities: NetworkCapabilities,
        networkType: NetworkType
    ): SignalMetrics {
        val isLocationGranted = permissionManager.isFineLocationGranted()
        val isPhoneStateGranted = permissionManager.isPhoneStateGranted()

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                collectWifiSignalMetrics(capabilities, isLocationGranted, isPhoneStateGranted)
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                collectCellularSignalMetrics(isLocationGranted, isPhoneStateGranted)
            }
            else -> SignalMetrics(
                isLocationPermissionGranted = isLocationGranted,
                isPhoneStatePermissionGranted = isPhoneStateGranted
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun collectWifiSignalMetrics(
        capabilities: NetworkCapabilities,
        isLocationGranted: Boolean,
        isPhoneStateGranted: Boolean
    ): SignalMetrics {
        val wifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (capabilities.transportInfo as? WifiInfo) ?: wifiManager?.connectionInfo
        } else {
            wifiManager?.connectionInfo
        }

        if (wifiInfo == null) {
            return SignalMetrics(
                isLocationPermissionGranted = isLocationGranted,
                isPhoneStatePermissionGranted = isPhoneStateGranted
            )
        }

        val rssi = wifiInfo.rssi.takeIf { it != -127 && it != 0 }
        val level = if (rssi != null && wifiManager != null) {
            WifiManager.calculateSignalLevel(rssi, 5)
        } else 0

        val ssid = if (isLocationGranted) {
            wifiInfo.ssid?.trim('\"')?.takeIf { it != "<unknown ssid>" }
        } else null

        val bssid = if (isLocationGranted) {
            wifiInfo.bssid?.takeIf { it != "02:00:00:00:00:00" }
        } else null

        val freq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wifiInfo.frequency.takeIf { it > 0 }
        } else null

        val linkSpeed = wifiInfo.linkSpeed.takeIf { it > 0 }

        return SignalMetrics(
            signalDbm = rssi,
            signalLevel = level,
            wifiSsid = ssid,
            wifiBssid = bssid,
            wifiFrequencyMhz = freq,
            wifiLinkSpeedMbps = linkSpeed,
            cellularCarrier = null,
            cellularNetworkType = null,
            isLocationPermissionGranted = isLocationGranted,
            isPhoneStatePermissionGranted = isPhoneStateGranted
        )
    }

    @SuppressLint("MissingPermission")
    private fun collectCellularSignalMetrics(
        isLocationGranted: Boolean,
        isPhoneStateGranted: Boolean
    ): SignalMetrics {
        val carrier = telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() }
        var signalDbm: Int? = null
        var signalLevel = 0
        var rsrp: Int? = null
        var rsrq: Int? = null
        var networkTypeStr: String? = null

        if (telephonyManager != null && isPhoneStateGranted) {
            try {
                val dataNetType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    telephonyManager.dataNetworkType
                } else {
                    @Suppress("DEPRECATION")
                    telephonyManager.networkType
                }
                networkTypeStr = formatCellularType(dataNetType)
            } catch (e: SecurityException) {
                // Handled gracefully
            }
        }

        if (telephonyManager != null && isLocationGranted) {
            try {
                val cellInfos: List<CellInfo>? = telephonyManager.allCellInfo
                val activeCell = cellInfos?.firstOrNull { it.isRegistered }

                if (activeCell != null) {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && activeCell is CellInfoNr -> {
                            val nrSignal = activeCell.cellSignalStrength
                            signalDbm = nrSignal.dbm
                            signalLevel = nrSignal.level
                        }
                        activeCell is CellInfoLte -> {
                            val lteSignal = activeCell.cellSignalStrength
                            signalDbm = lteSignal.dbm
                            signalLevel = lteSignal.level
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                rsrp = lteSignal.rsrp
                                rsrq = lteSignal.rsrq
                            }
                        }
                        activeCell is CellInfoWcdma -> {
                            signalDbm = activeCell.cellSignalStrength.dbm
                            signalLevel = activeCell.cellSignalStrength.level
                        }
                        activeCell is CellInfoGsm -> {
                            signalDbm = activeCell.cellSignalStrength.dbm
                            signalLevel = activeCell.cellSignalStrength.level
                        }
                    }
                }
            } catch (e: SecurityException) {
                // Handled gracefully
            }
        }

        return SignalMetrics(
            signalDbm = signalDbm,
            signalLevel = signalLevel,
            wifiSsid = null,
            wifiBssid = null,
            wifiFrequencyMhz = null,
            wifiLinkSpeedMbps = null,
            cellularCarrier = carrier,
            cellularNetworkType = networkTypeStr,
            rsrp = rsrp,
            rsrq = rsrq,
            isLocationPermissionGranted = isLocationGranted,
            isPhoneStatePermissionGranted = isPhoneStateGranted
        )
    }

    private fun formatCellularType(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            else -> "Cellular"
        }
    }

    private fun createOfflineObservation(): NetworkObservation {
        return NetworkObservation(
            id = 0,
            timestamp = System.currentTimeMillis(),
            networkType = NetworkType.NONE,
            transportTypeName = "Offline",
            isConnected = false,
            isValidated = false,
            interfaceName = null,
            signalMetrics = SignalMetrics(),
            latencyMetrics = LatencyMetrics(),
            bandwidthMetrics = BandwidthMetrics(),
            measurementState = MeasurementState.OFFLINE,
            errorMessage = "No active network interface detected."
        )
    }
}

