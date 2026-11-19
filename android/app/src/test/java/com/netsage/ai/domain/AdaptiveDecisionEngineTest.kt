package com.netsage.ai.domain

import com.netsage.ai.domain.decision.AdaptiveDecisionEngine
import com.netsage.ai.domain.model.AdaptiveAction
import com.netsage.ai.domain.model.BandwidthMetrics
import com.netsage.ai.domain.model.LatencyMetrics
import com.netsage.ai.domain.model.MeasurementState
import com.netsage.ai.domain.model.NetworkObservation
import com.netsage.ai.domain.model.NetworkType
import com.netsage.ai.domain.model.SignalMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdaptiveDecisionEngineTest {

    private lateinit var engine: AdaptiveDecisionEngine

    @Before
    fun setUp() {
        engine = AdaptiveDecisionEngine()
    }

    @Test
    fun evaluate_whenOffline_returnsZeroScoreAndPoorCoverageAction() {
        val offlineObs = NetworkObservation(
            networkType = NetworkType.NONE,
            isConnected = false,
            measurementState = MeasurementState.OFFLINE
        )

        val decision = engine.evaluate(offlineObs)

        assertEquals(0, decision.qualityScore)
        assertEquals(0, decision.stabilityIndex)
        assertEquals(AdaptiveAction.POOR_COVERAGE_ALERT, decision.action)
        assertTrue(decision.confidence >= 0.90f)
    }

    @Test
    fun evaluate_whenOptimalWifi_returnsHighQualityScoreAndMaintainLink() {
        val goodObs = NetworkObservation(
            networkType = NetworkType.WIFI,
            isConnected = true,
            isValidated = true,
            latencyMetrics = LatencyMetrics(rttMs = 22, dnsLookupMs = 12, isProbeSuccessful = true),
            signalMetrics = SignalMetrics(signalDbm = -54, signalLevel = 4),
            bandwidthMetrics = BandwidthMetrics(activeThroughputMbps = 65.0, downstreamEstimateKbps = 80000)
        )

        val decision = engine.evaluate(goodObs, history = listOf(goodObs, goodObs))

        assertTrue("Quality score should be >= 75 for optimal link, got ${decision.qualityScore}", decision.qualityScore >= 75)
        assertEquals(AdaptiveAction.MAINTAIN_CURRENT, decision.action)
        assertTrue(decision.stabilityIndex >= 70)
    }

    @Test
    fun evaluate_whenWifiDegradingWithCriticalLatency_triggersCellularHandoff() {
        val poorWifi = NetworkObservation(
            networkType = NetworkType.WIFI,
            isConnected = true,
            isValidated = true,
            latencyMetrics = LatencyMetrics(rttMs = 380, dnsLookupMs = 150, isProbeSuccessful = true),
            signalMetrics = SignalMetrics(signalDbm = -88, signalLevel = 1),
            bandwidthMetrics = BandwidthMetrics(downstreamEstimateKbps = 2000)
        )

        val history = listOf(poorWifi, poorWifi.copy(signalMetrics = SignalMetrics(signalDbm = -85)))
        val decision = engine.evaluate(poorWifi, history = history)

        val isHandoffAction = decision.action == AdaptiveAction.SWITCH_TO_CELLULAR || decision.action == AdaptiveAction.PREPARE_WIFI_HANDOFF
        assertTrue("Action should be handoff-related, got ${decision.action}", isHandoffAction)
    }

    @Test
    fun extractFeatureVector_producesNormalizedBounds() {
        val obs = NetworkObservation(
            networkType = NetworkType.WIFI,
            isConnected = true,
            latencyMetrics = LatencyMetrics(rttMs = 50, dnsLookupMs = 20),
            signalMetrics = SignalMetrics(signalDbm = -65, signalLevel = 3),
            bandwidthMetrics = BandwidthMetrics(downstreamEstimateKbps = 50000)
        )

        val vector = engine.extractFeatureVector(obs, stabilityIndex = 85)

        assertEquals(8, vector.size)
        for (i in vector.indices) {
            assertTrue("Feature index $i with value ${vector[i]} must be in 0.0..1.0 range", vector[i] in 0.0f..1.0f)
        }
    }
}

