package com.netsage.ai.domain.model

enum class NetworkType(val displayName: String, val shortName: String) {
    WIFI("Wi-Fi", "WIFI"),
    CELLULAR_5G("Cellular 5G (NR)", "5G"),
    CELLULAR_4G("Cellular 4G (LTE)", "4G/LTE"),
    CELLULAR_3G("Cellular 3G (HSPA/UMTS)", "3G"),
    CELLULAR_2G("Cellular 2G (EDGE/GPRS)", "2G"),
    CELLULAR_UNKNOWN("Cellular", "CELL"),
    ETHERNET("Ethernet", "ETH"),
    VPN("Virtual Private Network", "VPN"),
    NONE("Offline / Disconnected", "OFFLINE"),
    UNKNOWN("Unknown Network", "UNKNOWN")
}
