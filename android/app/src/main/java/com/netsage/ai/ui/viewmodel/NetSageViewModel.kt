package com.netsage.ai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.netsage.ai.domain.decision.AdaptiveDecisionEngine
import com.netsage.ai.domain.model.AdaptiveAction
import com.netsage.ai.domain.model.BandwidthMetrics
import com.netsage.ai.domain.model.LatencyMetrics
import com.netsage.ai.domain.model.MeasurementState
import com.netsage.ai.domain.model.NetworkObservation
import com.netsage.ai.domain.model.NetworkType
import com.netsage.ai.domain.model.PredictionDecision
import com.netsage.ai.domain.repository.NetworkRepository
import com.netsage.ai.measurement.collector.NetworkTelemetryCollector
import com.netsage.ai.measurement.permission.PermissionManager
import com.netsage.ai.measurement.probe.ActiveProbeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetSageViewModel(
    private val telemetryCollector: NetworkTelemetryCollector,
    private val activeProbeEngine: ActiveProbeEngine,
    private val networkRepository: NetworkRepository,
    private val decisionEngine: AdaptiveDecisionEngine,
    private val permissionManager: PermissionManager
) : ViewModel() {

    private val _currentObservation = MutableStateFlow(
        NetworkObservation(
            measurementState = MeasurementState.INITIALIZING
        )
    )
    val currentObservation: StateFlow<NetworkObservation> = _currentObservation.asStateFlow()

    private val _predictionDecision = MutableStateFlow(
        PredictionDecision(
            qualityScore = 0,
            stabilityIndex = 50,
            action = AdaptiveAction.INSUFFICIENT_DATA,
            confidence = 0f,
            latencyTrendDescription = "Initializing prediction engine...",
            signalTrendDescription = "Awaiting initial observation..."
        )
    )
    val predictionDecision: StateFlow<PredictionDecision> = _predictionDecision.asStateFlow()

    val recentObservations: StateFlow<List<NetworkObservation>> =
        networkRepository.getRecentObservations(limit = 40)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _isProbing = MutableStateFlow(false)
    val isProbing: StateFlow<Boolean> = _isProbing.asStateFlow()

    private val _isThroughputProbing = MutableStateFlow(false)
    val isThroughputProbing: StateFlow<Boolean> = _isThroughputProbing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _hasPermissions = MutableStateFlow(permissionManager.hasAllTelemetryPermissions())
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    init {
        startTelemetryObservation()
        checkPermissions()
    }

    private fun startTelemetryObservation() {
        viewModelScope.launch {
            telemetryCollector.observeNetworkTelemetry().collect { observation ->
                val history = recentObservations.value
                val decision = decisionEngine.evaluate(observation, history)

                _currentObservation.value = observation
                _predictionDecision.value = decision

                // Auto-persist valid observations into the Room database
                if (observation.isConnected) {
                    saveObservationToDb(observation)
                }
            }
        }
    }

    fun triggerActiveProbe() {
        if (_isProbing.value) return

        viewModelScope.launch {
            _isProbing.value = true
            _currentObservation.value = _currentObservation.value.copy(
                measurementState = MeasurementState.PROBING
            )

            try {
                val latencyMetrics = activeProbeEngine.executeLatencyProbe()
                val current = _currentObservation.value

                val updatedObservation = current.copy(
                    latencyMetrics = latencyMetrics,
                    measurementState = if (latencyMetrics.isProbeSuccessful) {
                        MeasurementState.ACTIVE
                    } else {
                        MeasurementState.ERROR
                    },
                    errorMessage = latencyMetrics.probeError
                )

                _currentObservation.value = updatedObservation
                val history = recentObservations.value
                _predictionDecision.value = decisionEngine.evaluate(updatedObservation, history)

                // Save probe result to database
                saveObservationToDb(updatedObservation)

                if (!latencyMetrics.isProbeSuccessful && latencyMetrics.probeError != null) {
                    _errorMessage.value = "Active probe failed: ${latencyMetrics.probeError}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Probe error: ${e.message}"
            } finally {
                _isProbing.value = false
            }
        }
    }

    fun triggerThroughputProbe() {
        if (_isThroughputProbing.value) return

        viewModelScope.launch {
            _isThroughputProbing.value = true
            try {
                val throughputMbps = activeProbeEngine.executeThroughputProbe()
                val current = _currentObservation.value
                val updatedBandwidth = current.bandwidthMetrics.copy(
                    activeThroughputMbps = throughputMbps
                )

                val updatedObservation = current.copy(
                    bandwidthMetrics = updatedBandwidth
                )

                _currentObservation.value = updatedObservation
                val history = recentObservations.value
                _predictionDecision.value = decisionEngine.evaluate(updatedObservation, history)

                saveObservationToDb(updatedObservation)
            } catch (e: Exception) {
                _errorMessage.value = "Throughput test error: ${e.message}"
            } finally {
                _isThroughputProbing.value = false
            }
        }
    }

    fun refreshTelemetry() {
        viewModelScope.launch {
            val observation = telemetryCollector.collectCurrentObservation()
            val history = recentObservations.value
            _currentObservation.value = observation
            _predictionDecision.value = decisionEngine.evaluate(observation, history)
        }
    }

    fun checkPermissions() {
        _hasPermissions.value = permissionManager.hasAllTelemetryPermissions()
        refreshTelemetry()
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepository.clearObservations()
        }
    }

    private suspend fun saveObservationToDb(observation: NetworkObservation) {
        withContext(Dispatchers.IO) {
            try {
                networkRepository.saveObservation(observation)
            } catch (e: Exception) {
                // Ignore DB insertion errors silently to prevent UI interruptions
            }
        }
    }

    class Factory(
        private val telemetryCollector: NetworkTelemetryCollector,
        private val activeProbeEngine: ActiveProbeEngine,
        private val networkRepository: NetworkRepository,
        private val decisionEngine: AdaptiveDecisionEngine,
        private val permissionManager: PermissionManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NetSageViewModel::class.java)) {
                return NetSageViewModel(
                    telemetryCollector,
                    activeProbeEngine,
                    networkRepository,
                    decisionEngine,
                    permissionManager
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

