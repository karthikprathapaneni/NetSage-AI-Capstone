package com.netsage.ai.measurement.probe

import com.netsage.ai.domain.model.LatencyMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class ActiveProbeEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
) {
    companion object {
        const val PROBE_URL = "https://www.google.com/generate_204"
        const val DNS_PROBE_HOST = "dns.google"
        const val THROUGHPUT_PROBE_URL = "https://speed.cloudflare.com/__down?bytes=150000" // 150KB sample
    }

    suspend fun executeLatencyProbe(): LatencyMetrics = withContext(Dispatchers.IO) {
        var dnsLookupMs: Long? = null
        var httpLatencyMs: Long? = null
        var rttMs: Long? = null
        var probeError: String? = null
        var isSuccess = false

        try {
            // Step 1: Real DNS Resolution Probe
            val dnsStart = System.nanoTime()
            val addresses = InetAddress.getAllByName(DNS_PROBE_HOST)
            val dnsEnd = System.nanoTime()
            if (addresses.isNotEmpty()) {
                dnsLookupMs = (dnsEnd - dnsStart) / 1_000_000L
            }

            // Step 2: Real HTTP 204 Latency Probe
            val request = Request.Builder()
                .url(PROBE_URL)
                .header("User-Agent", "NetSage-AI-Telemetry/1.0")
                .header("Cache-Control", "no-cache")
                .build()

            val httpStart = System.nanoTime()
            val response = client.newCall(request).execute()
            val httpEnd = System.nanoTime()

            response.use { resp ->
                if (resp.isSuccessful || resp.code == 204) {
                    httpLatencyMs = (httpEnd - httpStart) / 1_000_000L
                    rttMs = httpLatencyMs
                    isSuccess = true
                } else {
                    probeError = "HTTP ${resp.code} ${resp.message}"
                }
            }
        } catch (e: Exception) {
            probeError = e.message ?: e.javaClass.simpleName
            isSuccess = false
        }

        LatencyMetrics(
            rttMs = rttMs,
            dnsLookupMs = dnsLookupMs,
            httpLatencyMs = httpLatencyMs,
            targetHost = PROBE_URL,
            isProbeSuccessful = isSuccess,
            probeError = probeError
        )
    }

    suspend fun executeThroughputProbe(): Double? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(THROUGHPUT_PROBE_URL)
                .header("User-Agent", "NetSage-AI-Telemetry/1.0")
                .header("Cache-Control", "no-cache")
                .build()

            val startTime = System.nanoTime()
            val response = client.newCall(request).execute()
            response.use { resp ->
                val body = resp.body ?: return@withContext null
                val bytes = body.bytes() // reads the 150KB payload
                val elapsedNano = System.nanoTime() - startTime
                val elapsedSeconds = elapsedNano / 1_000_000_000.0
                if (elapsedSeconds > 0 && bytes.isNotEmpty()) {
                    val megabits = (bytes.size * 8.0) / 1_000_000.0
                    return@withContext (megabits / elapsedSeconds)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
