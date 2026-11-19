package com.netsage.ai.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "network_observations",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["network_type"])
    ]
)
data class NetworkObservationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "network_type")
    val networkType: String,

    @ColumnInfo(name = "transport_name")
    val transportName: String,

    @ColumnInfo(name = "is_connected")
    val isConnected: Boolean,

    @ColumnInfo(name = "is_validated")
    val isValidated: Boolean,

    @ColumnInfo(name = "interface_name")
    val interfaceName: String?,

    // Latency Telemetry
    @ColumnInfo(name = "rtt_ms")
    val rttMs: Long?,

    @ColumnInfo(name = "dns_lookup_ms")
    val dnsLookupMs: Long?,

    @ColumnInfo(name = "http_latency_ms")
    val httpLatencyMs: Long?,

    @ColumnInfo(name = "target_host")
    val targetHost: String?,

    // Bandwidth Telemetry
    @ColumnInfo(name = "downstream_kbps")
    val downstreamKbps: Int?,

    @ColumnInfo(name = "upstream_kbps")
    val upstreamKbps: Int?,

    @ColumnInfo(name = "active_throughput_mbps")
    val activeThroughputMbps: Double?,

    // Signal & Physical Layer
    @ColumnInfo(name = "signal_dbm")
    val signalDbm: Int?,

    @ColumnInfo(name = "signal_level")
    val signalLevel: Int,

    @ColumnInfo(name = "wifi_ssid")
    val wifiSsid: String?,

    @ColumnInfo(name = "wifi_frequency_mhz")
    val wifiFrequencyMhz: Int?,

    @ColumnInfo(name = "wifi_link_speed_mbps")
    val wifiLinkSpeedMbps: Int?,

    @ColumnInfo(name = "cellular_carrier")
    val cellularCarrier: String?,

    @ColumnInfo(name = "cellular_network_type")
    val cellularNetworkType: String?,

    @ColumnInfo(name = "measurement_state")
    val measurementState: String,

    @ColumnInfo(name = "error_message")
    val errorMessage: String?
)
