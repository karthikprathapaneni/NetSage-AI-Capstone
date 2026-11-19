package com.netsage.ai

import android.app.Application
import com.netsage.ai.domain.decision.AdaptiveDecisionEngine
import com.netsage.ai.domain.repository.NetworkRepository
import com.netsage.ai.measurement.collector.NetworkTelemetryCollector
import com.netsage.ai.measurement.permission.PermissionManager
import com.netsage.ai.measurement.probe.ActiveProbeEngine
import com.netsage.ai.storage.database.NetSageDatabase
import com.netsage.ai.storage.repository.RoomNetworkRepository

class NetSageApplication : Application() {

    lateinit var database: NetSageDatabase
        private set

    lateinit var networkRepository: NetworkRepository
        private set

    lateinit var permissionManager: PermissionManager
        private set

    lateinit var telemetryCollector: NetworkTelemetryCollector
        private set

    lateinit var activeProbeEngine: ActiveProbeEngine
        private set

    lateinit var decisionEngine: AdaptiveDecisionEngine
        private set

    override fun onCreate() {
        super.onCreate()

        database = NetSageDatabase.getInstance(this)
        networkRepository = RoomNetworkRepository(database.networkObservationDao())
        permissionManager = PermissionManager(this)
        telemetryCollector = NetworkTelemetryCollector(this, permissionManager)
        activeProbeEngine = ActiveProbeEngine()
        decisionEngine = AdaptiveDecisionEngine()
    }
}

