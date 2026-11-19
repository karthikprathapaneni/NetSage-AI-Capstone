package com.netsage.ai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.netsage.ai.NetSageApplication
import com.netsage.ai.ui.theme.NetSageAITheme
import com.netsage.ai.ui.viewmodel.NetSageViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: NetSageViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as NetSageApplication
        val factory = NetSageViewModel.Factory(
            telemetryCollector = app.telemetryCollector,
            activeProbeEngine = app.activeProbeEngine,
            networkRepository = app.networkRepository,
            decisionEngine = app.decisionEngine,
            permissionManager = app.permissionManager
        )
        viewModel = ViewModelProvider(this, factory)[NetSageViewModel::class.java]

        setContent {
            NetSageAITheme {
                DashboardScreen(
                    viewModel = viewModel,
                    onRequestPermissions = { requestTelemetryPermissions() }
                )
            }
        }
    }

    private fun requestTelemetryPermissions() {
        val app = application as NetSageApplication
        permissionLauncher.launch(app.permissionManager.requiredTelemetryPermissions)
    }
}

