package com.netsage.ai.domain.decision

import com.netsage.ai.domain.ml.MlQualityClassifier
import com.netsage.ai.domain.model.AdaptiveAction
import com.netsage.ai.domain.model.NetworkObservation
import com.netsage.ai.domain.model.NetworkType
import com.netsage.ai.domain.model.PredictionDecision
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AdaptiveDecisionEngine(
    private val mlClassifier: MlQualityClassifier = MlQualityClassifier()
) {

    fun evaluate(
        current: NetworkObservation,
        history: List<NetworkObservation> = emptyList()
    ): PredictionDecision {
        if (!current.isConnected || current.networkType == NetworkType.NONE) {
            val emptyVector = FloatArray(8) { 0.0f }
            return PredictionDecision(
                qualityScore = 0,
                stabilityIndex = 0,
                action = AdaptiveAction.POOR_COVERAGE_ALERT,
                confidence = 0.95f,
                latencyTrendDescription = "No active network route available.",
                signalTrendDescription = "Transceiver disconnected.",
                isMlModelReady = true,
                mlModelStatus = "ML Offline: Route Disconnected",
                mlPredictedClass = "POOR",
                mlConfidence = 0.99f,
                isMlAssisted = true,
                featureVector = emptyVector
            )
        }

        if (history.isEmpty()) {
            val quickQuality = computeSingleObservationQuality(current)
            val features = extractFeatureVector(current, 65)
            val mlResult = mlClassifier.predict(features)

            return PredictionDecision(
                qualityScore = quickQuality,
                stabilityIndex = 65,
                action = AdaptiveAction.INSUFFICIENT_DATA,
                confidence = 0.40f,
                latencyTrendDescription = "Calibrating baseline telemetry...",
                signalTrendDescription = "Observing signal parameters...",
                isMlModelReady = true,
                mlModelStatus = "ML Warmup: ${mlResult.predictedLabel} (${(mlResult.confidence * 100).toInt()}% conf)",
                mlPredictedClass = mlResult.predictedLabel,
                mlConfidence = mlResult.confidence,
                isMlAssisted = true,
                featureVector = features
            )
        }

        val qualityScore = computeQualityScore(current, history)
        val stabilityIndex = computeStabilityIndex(history)
        val features = extractFeatureVector(current, stabilityIndex)
        val mlResult = mlClassifier.predict(features)

        var action = determineAdaptiveAction(current, history, qualityScore, stabilityIndex)

        // ML-assisted proactive guardrail:
        // If ML model detects near-future or immediate QoS degradation (class 0 POOR with high confidence)
        // and rule-based action is still MAINTAIN_CURRENT, escalate recommendation.
        if (mlResult.predictedClass == 0 && mlResult.confidence > 0.75f && action == AdaptiveAction.MAINTAIN_CURRENT) {
            action = if (current.networkType == NetworkType.WIFI) {
                AdaptiveAction.PREPARE_WIFI_HANDOFF
            } else {
                AdaptiveAction.POOR_COVERAGE_ALERT
            }
        }

        val confidence = computeConfidence(history)
        val latencyTrend = describeLatencyTrend(current, history)
        val signalTrend = describeSignalTrend(current, history)

        return PredictionDecision(
            qualityScore = qualityScore,
            stabilityIndex = stabilityIndex,
            action = action,
            confidence = confidence,
            latencyTrendDescription = latencyTrend,
            signalTrendDescription = signalTrend,
            isMlModelReady = true,
            mlModelStatus = "ML-Assisted: ${mlResult.predictedLabel} (${(mlResult.confidence * 100).toInt()}% conf, ${mlResult.inferenceLatencyUs}µs)",
            mlPredictedClass = mlResult.predictedLabel,
            mlConfidence = mlResult.confidence,
            isMlAssisted = true,
            featureVector = features
        )
    }

    private fun computeSingleObservationQuality(current: NetworkObservation): Int {
        var score = 0

        val latency = current.effectiveLatencyMs
        score += when {
            latency == null -> 20
            latency < 30 -> 35
            latency < 70 -> 28
            latency < 130 -> 20
            latency < 250 -> 12
            else -> 4
        }

        score += when (current.signalMetrics.signalLevel) {
            4 -> 35
            3 -> 28
            2 -> 20
            1 -> 10
            else -> if (current.signalMetrics.signalDbm != null) 12 else 18
        }

        val activeThroughput = current.bandwidthMetrics.activeThroughputMbps
        val downstreamEst = current.bandwidthMetrics.downstreamEstimateKbps
        score += when {
            activeThroughput != null && activeThroughput > 50.0 -> 20
            activeThroughput != null && activeThroughput > 15.0 -> 16
            activeThroughput != null && activeThroughput > 5.0 -> 12
            downstreamEst != null && downstreamEst > 50_000 -> 18
            downstreamEst != null && downstreamEst > 10_000 -> 14
            downstreamEst != null && downstreamEst > 2_000 -> 10
            else -> 8
        }

        if (current.isValidated) {
            score += 10
        }

        return score.coerceIn(0, 100)
    }

    private fun computeQualityScore(
        current: NetworkObservation,
        history: List<NetworkObservation>
    ): Int {
        val window = (listOf(current) + history).take(10)
        val sampleScores = window.map { computeSingleObservationQuality(it) }
        val weightedAvg = if (sampleScores.isNotEmpty()) {
            val histAvg = sampleScores.drop(1).takeIf { it.isNotEmpty() }?.average() ?: sampleScores.first().toDouble()
            (sampleScores.first() * 0.40) + (histAvg * 0.60)
        } else {
            computeSingleObservationQuality(current).toDouble()
        }
        return weightedAvg.roundToInt().coerceIn(0, 100)
    }

    private fun computeStabilityIndex(history: List<NetworkObservation>): Int {
        if (history.size < 2) return 70

        val validLatencies = history.mapNotNull { it.effectiveLatencyMs?.toDouble() }
        val latencyVariance = if (validLatencies.size >= 2) {
            val mean = validLatencies.average()
            val sumSq = validLatencies.sumOf { (it - mean) * (it - mean) }
            sqrt(sumSq / validLatencies.size)
        } else {
            10.0
        }

        val validSignals = history.mapNotNull { it.signalMetrics.signalDbm?.toDouble() }
        val signalVariance = if (validSignals.size >= 2) {
            val mean = validSignals.average()
            val sumSq = validSignals.sumOf { (it - mean) * (it - mean) }
            sqrt(sumSq / validSignals.size)
        } else {
            2.0
        }

        val latencyPenalty = (latencyVariance / 4.0).coerceAtMost(50.0)
        val signalPenalty = (signalVariance * 3.0).coerceAtMost(30.0)

        val rawStability = 100.0 - latencyPenalty - signalPenalty
        return rawStability.roundToInt().coerceIn(10, 100)
    }

    private fun determineAdaptiveAction(
        current: NetworkObservation,
        history: List<NetworkObservation>,
        qualityScore: Int,
        stabilityIndex: Int
    ): AdaptiveAction {
        val isWifi = current.networkType == NetworkType.WIFI
        val isCellular = current.networkType in listOf(
            NetworkType.CELLULAR_5G,
            NetworkType.CELLULAR_4G,
            NetworkType.CELLULAR_3G,
            NetworkType.CELLULAR_2G,
            NetworkType.CELLULAR_UNKNOWN
        )

        val signalDbm = current.signalMetrics.signalDbm
        val latency = current.effectiveLatencyMs

        if (isWifi) {
            val isRssiCritical = signalDbm != null && signalDbm < -84
            val isLatencyCritical = latency != null && latency > 320
            val isJitterHigh = stabilityIndex < 40

            if (isRssiCritical && (isLatencyCritical || isJitterHigh)) {
                return AdaptiveAction.SWITCH_TO_CELLULAR
            }
            if (isRssiCritical || (latency != null && latency > 200)) {
                return AdaptiveAction.PREPARE_WIFI_HANDOFF
            }
        }

        if (isCellular) {
            if (current.bandwidthMetrics.isMetered && qualityScore < 45) {
                return AdaptiveAction.THROTTLE_BACKGROUND_TASKS
            }
            if (signalDbm != null && signalDbm < -115) {
                return AdaptiveAction.POOR_COVERAGE_ALERT
            }
        }

        if (qualityScore >= 65 && stabilityIndex >= 55) {
            return AdaptiveAction.MAINTAIN_CURRENT
        }

        if (qualityScore < 35) {
            return AdaptiveAction.POOR_COVERAGE_ALERT
        }

        return AdaptiveAction.MAINTAIN_CURRENT
    }

    private fun computeConfidence(history: List<NetworkObservation>): Float {
        val sampleFactor = (history.size / 15.0f).coerceAtMost(1.0f)
        val baseConfidence = 0.50f + (sampleFactor * 0.45f)
        return (baseConfidence * 100).roundToInt() / 100.0f
    }

    private fun describeLatencyTrend(
        current: NetworkObservation,
        history: List<NetworkObservation>
    ): String {
        val currentLatency = current.effectiveLatencyMs
            ?: return "Latency probe not yet executed."

        val historicalLatencies = history.mapNotNull { it.effectiveLatencyMs }
        if (historicalLatencies.isEmpty()) {
            return "Current: ${currentLatency}ms (Initial measurement)"
        }

        val avg = historicalLatencies.average().roundToInt()
        val diff = currentLatency - avg
        return when {
            diff > 35 -> "Latency elevated: ${currentLatency}ms (+${diff}ms vs avg)"
            diff < -25 -> "Latency improved: ${currentLatency}ms (${diff}ms vs avg)"
            else -> "Latency stable: ${currentLatency}ms (Avg ${avg}ms)"
        }
    }

    private fun describeSignalTrend(
        current: NetworkObservation,
        history: List<NetworkObservation>
    ): String {
        val dbm = current.signalMetrics.signalDbm
        val level = current.signalMetrics.signalLevel
        val qualityDesc = current.signalMetrics.signalQualityDescription

        if (dbm == null) {
            return if (level > 0) "Signal: $qualityDesc ($level/4 bars)" else "Signal telemetry restricted (Grant permissions)."
        }

        val pastSignals = history.mapNotNull { it.signalMetrics.signalDbm }
        if (pastSignals.isEmpty()) {
            return "$qualityDesc ($dbm dBm, $level/4 bars)"
        }

        val pastAvg = pastSignals.average().roundToInt()
        val delta = dbm - pastAvg
        return when {
            delta >= 6 -> "$qualityDesc ($dbm dBm, improving +${delta}dB)"
            delta <= -6 -> "$qualityDesc ($dbm dBm, degrading ${delta}dB)"
            else -> "$qualityDesc ($dbm dBm, stable)"
        }
    }

    fun extractFeatureVector(
        observation: NetworkObservation,
        stabilityIndex: Int
    ): FloatArray {
        val normRtt = ((observation.effectiveLatencyMs ?: 150L).toFloat() / 500f).coerceIn(0f, 1f)
        val normDns = ((observation.latencyMetrics.dnsLookupMs ?: 30L).toFloat() / 200f).coerceIn(0f, 1f)
        val rawDbm = observation.signalMetrics.signalDbm ?: -85
        val normSignal = ((rawDbm + 120).toFloat() / 80f).coerceIn(0f, 1f)
        val normLevel = observation.signalMetrics.signalLevel.toFloat() / 4f
        val rawKbps = observation.bandwidthMetrics.downstreamEstimateKbps ?: 20_000
        val normBw = (rawKbps.toFloat() / 150_000f).coerceIn(0f, 1f)
        val normNetType = when (observation.networkType) {
            NetworkType.WIFI -> 0.2f
            NetworkType.CELLULAR_5G -> 0.4f
            NetworkType.CELLULAR_4G -> 0.6f
            NetworkType.CELLULAR_3G -> 0.8f
            else -> 1.0f
        }
        val normStability = stabilityIndex.toFloat() / 100f
        val isMetered = if (observation.bandwidthMetrics.isMetered) 1f else 0f

        return floatArrayOf(
            normRtt,
            normDns,
            normSignal,
            normLevel,
            normBw,
            normNetType,
            normStability,
            isMetered
        )
    }
}
