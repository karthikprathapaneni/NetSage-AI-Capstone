package com.netsage.ai.storage.repository

import com.netsage.ai.domain.model.BandwidthMetrics
import com.netsage.ai.domain.model.LatencyMetrics
import com.netsage.ai.domain.model.MeasurementState
import com.netsage.ai.domain.model.NetworkObservation
import com.netsage.ai.domain.model.NetworkType
import com.netsage.ai.domain.model.SignalMetrics
import com.netsage.ai.domain.repository.NetworkRepository
import com.netsage.ai.storage.dao.NetworkObservationDao
import com.netsage.ai.storage.entity.NetworkObservationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomNetworkRepository(
    private val dao: NetworkObservationDao
) : NetworkRepository {

    override fun getRecentObservations(limit: Int): Flow<List<NetworkObservation>> {
        return dao.getRecentObservations(limit).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getObservationsSince(startTimeMs: Long): Flow<List<NetworkObservation>> {
        return dao.getObservationsSince(startTimeMs).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun saveObservation(observation: NetworkObservation): Long {
        return dao.insertObservation(observation.toEntity())
    }

    override suspend fun clearObservations(): Int {
        return dao.clearAll()
    }

    override fun getObservationCount(): Flow<Int> {
        return dao.getObservationCount()
    }

    companion object {
        fun NetworkObservationEntity.toDomainModel(): NetworkObservation {
            val netType = try {
                NetworkType.valueOf(networkType)
            } catch (e: Exception) {
                NetworkType.UNKNOWN
            }

            val state = try {
                MeasurementState.valueOf(measurementState)
            } catch (e: Exception) {
                MeasurementState.ACTIVE
            }

            return NetworkObservation(
                id = id,
                timestamp = timestamp,
                networkType = netType,
                transportTypeName = transportName,
                isConnected = isConnected,
                isValidated = isValidated,
                interfaceName = interfaceName,
                signalMetrics = SignalMetrics(
                    signalDbm = signalDbm,
                    signalLevel = signalLevel,
                    wifiSsid = wifiSsid,
                    wifiFrequencyMhz = wifiFrequencyMhz,
                    wifiLinkSpeedMbps = wifiLinkSpeedMbps,
                    cellularCarrier = cellularCarrier,
                    cellularNetworkType = cellularNetworkType
                ),
                latencyMetrics = LatencyMetrics(
                    rttMs = rttMs,
                    dnsLookupMs = dnsLookupMs,
                    httpLatencyMs = httpLatencyMs,
                    targetHost = targetHost ?: "dns.google"
                ),
                bandwidthMetrics = BandwidthMetrics(
                    downstreamEstimateKbps = downstreamKbps,
                    upstreamEstimateKbps = upstreamKbps,
                    activeThroughputMbps = activeThroughputMbps
                ),
                measurementState = state,
                errorMessage = errorMessage
            )
        }

        fun NetworkObservation.toEntity(): NetworkObservationEntity {
            return NetworkObservationEntity(
                id = id,
                timestamp = timestamp,
                networkType = networkType.name,
                transportName = transportTypeName,
                isConnected = isConnected,
                isValidated = isValidated,
                interfaceName = interfaceName,
                rttMs = latencyMetrics.rttMs,
                dnsLookupMs = latencyMetrics.dnsLookupMs,
                httpLatencyMs = latencyMetrics.httpLatencyMs,
                targetHost = latencyMetrics.targetHost,
                downstreamKbps = bandwidthMetrics.downstreamEstimateKbps,
                upstreamKbps = bandwidthMetrics.upstreamEstimateKbps,
                activeThroughputMbps = bandwidthMetrics.activeThroughputMbps,
                signalDbm = signalMetrics.signalDbm,
                signalLevel = signalMetrics.signalLevel,
                wifiSsid = signalMetrics.wifiSsid,
                wifiFrequencyMhz = signalMetrics.wifiFrequencyMhz,
                wifiLinkSpeedMbps = signalMetrics.wifiLinkSpeedMbps,
                cellularCarrier = signalMetrics.cellularCarrier,
                cellularNetworkType = signalMetrics.cellularNetworkType,
                measurementState = measurementState.name,
                errorMessage = errorMessage
            )
        }
    }
}
