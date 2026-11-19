package com.netsage.ai.domain.repository

import com.netsage.ai.domain.model.NetworkObservation
import kotlinx.coroutines.flow.Flow

interface NetworkRepository {
    fun getRecentObservations(limit: Int = 100): Flow<List<NetworkObservation>>
    fun getObservationsSince(startTimeMs: Long): Flow<List<NetworkObservation>>
    suspend fun saveObservation(observation: NetworkObservation): Long
    suspend fun clearObservations(): Int
    fun getObservationCount(): Flow<Int>
}
