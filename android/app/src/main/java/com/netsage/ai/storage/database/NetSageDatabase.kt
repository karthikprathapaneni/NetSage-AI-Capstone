package com.netsage.ai.storage.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.netsage.ai.storage.dao.NetworkObservationDao
import com.netsage.ai.storage.entity.NetworkObservationEntity

@Database(
    entities = [NetworkObservationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NetSageDatabase : RoomDatabase() {

    abstract fun networkObservationDao(): NetworkObservationDao

    companion object {
        private const val DATABASE_NAME = "netsage_observations.db"

        @Volatile
        private var INSTANCE: NetSageDatabase? = null

        fun getInstance(context: Context): NetSageDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): NetSageDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                NetSageDatabase::class.java,
                DATABASE_NAME
            )
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}
