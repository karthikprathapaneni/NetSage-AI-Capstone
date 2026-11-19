package com.netsage.ai.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.netsage.ai.storage.entity.NetworkObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkObservationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(observation: NetworkObservationEntity): Long

    @Query("SELECT * FROM network_observations ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentObservations(limit: Int): Flow<List<NetworkObservationEntity>>

    @Query("SELECT * FROM network_observations WHERE timestamp >= :startTimeMs ORDER BY timestamp ASC")
    fun getObservationsSince(startTimeMs: Long): Flow<List<NetworkObservationEntity>>

    @Query("SELECT COUNT(*) FROM network_observations")
    fun getObservationCount(): Flow<Int>

    @Query("SELECT AVG(rtt_ms) FROM network_observations WHERE timestamp >= :startTimeMs AND rtt_ms IS NOT NULL")
    suspend fun getAverageLatencySince(startTimeMs: Long): Double?

    @Query("DELETE FROM network_observations WHERE timestamp < :olderThanMs")
    suspend fun deleteOlderThan(olderThanMs: Long): Int

    @Query("DELETE FROM network_observations")
    suspend fun clearAll(): Int
}
