package com.netsage.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiBad
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netsage.ai.domain.model.AdaptiveAction
import com.netsage.ai.domain.model.MeasurementState
import com.netsage.ai.domain.model.NetworkObservation
import com.netsage.ai.domain.model.NetworkType
import com.netsage.ai.domain.model.PredictionDecision
import com.netsage.ai.ui.components.NetworkQualityChart
import com.netsage.ai.ui.theme.AlertAmber
import com.netsage.ai.ui.theme.CyberCyan
import com.netsage.ai.ui.theme.DangerRed
import com.netsage.ai.ui.theme.ElectricGreen
import com.netsage.ai.ui.theme.NeonPurple
import com.netsage.ai.ui.viewmodel.NetSageViewModel

@Composable
fun DashboardScreen(
    viewModel: NetSageViewModel,
    onRequestPermissions: () -> Unit
) {
    val observation by viewModel.currentObservation.collectAsState()
    val decision by viewModel.predictionDecision.collectAsState()
    val history by viewModel.recentObservations.collectAsState()
    val isProbing by viewModel.isProbing.collectAsState()
    val isThroughputProbing by viewModel.isThroughputProbing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val hasPermissions by viewModel.hasPermissions.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar
            HeaderSection(
                observation = observation,
                onRefresh = { viewModel.refreshTelemetry() }
            )

            // Permission Banner (if elevated permissions missing)
            if (!hasPermissions) {
                PermissionBanner(onRequestPermissions = onRequestPermissions)
            }

            // Error Banner (if error occurred)
            errorMessage?.let { error ->
                ErrorBanner(
                    message = error,
                    onDismiss = { viewModel.dismissError() }
                )
            }

            // Network Hero Card
            NetworkHeroCard(observation = observation)

            // Decision Support & Prediction Card
            DecisionSupportCard(decision = decision)

            // Historical Canvas Chart
            NetworkQualityChart(observations = history)

            // Real-Time Telemetry Grid
            TelemetryGrid(observation = observation)

            // Action Controls Deck
            ControlsDeck(
                isProbing = isProbing,
                isThroughputProbing = isThroughputProbing,
                onTriggerProbe = { viewModel.triggerActiveProbe() },
                onTriggerThroughput = { viewModel.triggerThroughputProbe() },
                onClearHistory = { viewModel.clearHistory() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeaderSection(
    observation: NetworkObservation,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NS",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column {
                Text(
                    text = "NetSage AI",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Mobile Computing Telemetry",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statusColor = when (observation.measurementState) {
                MeasurementState.ACTIVE -> ElectricGreen
                MeasurementState.PROBING -> CyberCyan
                MeasurementState.PARTIAL_TELEMETRY -> AlertAmber
                MeasurementState.OFFLINE, MeasurementState.ERROR -> DangerRed
                MeasurementState.INITIALIZING -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, statusColor.copy(alpha = 0.4f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = observation.measurementState.statusLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Telemetry",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun NetworkHeroCard(observation: NetworkObservation) {
    val isOnline = observation.isConnected && observation.networkType != NetworkType.NONE
    val cardColor = if (isOnline) MaterialTheme.colorScheme.surface else DangerRed.copy(alpha = 0.08f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardColor, RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (isOnline) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else DangerRed.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            )
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val icon: ImageVector = when (observation.networkType) {
                    NetworkType.WIFI -> Icons.Default.SignalWifi4Bar
                    NetworkType.CELLULAR_5G, NetworkType.CELLULAR_4G -> Icons.Default.CellTower
                    NetworkType.NONE -> Icons.Default.SignalWifiBad
                    else -> Icons.Default.NetworkCheck
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isOnline) CyberCyan else DangerRed,
                    modifier = Modifier.size(28.dp)
                )

                Column {
                    Text(
                        text = observation.transportTypeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = observation.signalMetrics.wifiSsid
                            ?: observation.signalMetrics.cellularCarrier
                            ?: if (isOnline) "Connected Route" else "Disconnected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .background(
                        color = (if (observation.isValidated) ElectricGreen else AlertAmber).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = (if (observation.isValidated) ElectricGreen else AlertAmber).copy(alpha = 0.4f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (observation.isValidated) "Validated" else if (isOnline) "Unvalidated" else "Offline",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (observation.isValidated) ElectricGreen else AlertAmber
                )
            }
        }

        observation.interfaceName?.let { ifName ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Interface: $ifName | MTU: 1500",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DecisionSupportCard(decision: PredictionDecision) {
    val qScore = decision.qualityScore
    val scoreColor = when {
        qScore >= 70 -> ElectricGreen
        qScore >= 40 -> AlertAmber
        else -> DangerRed
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Adaptive Decision Support",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .background(NeonPurple.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (decision.isMlAssisted) "ML-Assisted" else "Heuristic Engine",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonPurple
                )
            }
        }

        // Scores Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Quality Score
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(text = "QUALITY SCORE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "$qScore/100",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = scoreColor
                    )
                    Text(
                        text = when {
                            qScore >= 70 -> "Optimal Link"
                            qScore >= 40 -> "Acceptable"
                            else -> "Degraded Link"
                        },
                        fontSize = 11.sp,
                        color = scoreColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Stability Index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(text = "STABILITY INDEX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${decision.stabilityIndex}%",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = CyberCyan
                    )
                    Text(
                        text = if (decision.stabilityIndex > 70) "Low Jitter" else "High Variance",
                        fontSize = 11.sp,
                        color = CyberCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Action Recommendation Box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(scoreColor.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .border(1.dp, scoreColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(scoreColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = decision.action.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black
                    )
                }
                Text(
                    text = "Conf: ${(decision.confidence * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = decision.action.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = decision.action.rationale,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        // Trends & ML Hook
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrendTile(title = "Latency Trend", desc = decision.latencyTrendDescription, modifier = Modifier.weight(1f))
            TrendTile(title = "Signal Trend", desc = decision.signalTrendDescription, modifier = Modifier.weight(1f))
        }

        decision.mlModelStatus.let { status ->
            Text(
                text = "Model: $status",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = NeonPurple
            )
        }
    }
}

@Composable
private fun TrendTile(
    title: String,
    desc: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(text = title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
        }
    }
}

@Composable
private fun TelemetryGrid(observation: NetworkObservation) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Real-Time Telemetry Measurements",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Latency
            MetricCard(
                title = "RTT / HTTP Latency",
                value = observation.effectiveLatencyMs?.let { "${it} ms" } ?: "--",
                subtitle = "DNS: ${observation.latencyMetrics.dnsLookupMs?.let { "${it}ms" } ?: "--"}",
                modifier = Modifier.weight(1f)
            )

            // Throughput
            MetricCard(
                title = "Downlink Throughput",
                value = observation.bandwidthMetrics.activeThroughputMbps?.let { String.format("%.1f Mbps", it) }
                    ?: observation.bandwidthMetrics.downstreamEstimateKbps?.let { String.format("%.1f Mbps", it / 1000f) }
                    ?: "--",
                subtitle = "Est: ${observation.bandwidthMetrics.downstreamEstimateKbps?.let { "${it/1000} Mbps" } ?: "--"}",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Signal
            MetricCard(
                title = "RF Signal Quality",
                value = observation.signalMetrics.signalDbm?.let { "${it} dBm" } ?: "--",
                subtitle = "${observation.signalMetrics.signalLevel}/4 Bars (${observation.signalMetrics.signalQualityDescription})",
                modifier = Modifier.weight(1f)
            )

            // Uplink
            MetricCard(
                title = "Uplink Capacity",
                value = observation.bandwidthMetrics.upstreamEstimateKbps?.let { String.format("%.1f Mbps", it / 1000f) } ?: "--",
                subtitle = if (observation.bandwidthMetrics.isMetered) "Metered Connection" else "Unmetered",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionBanner(onRequestPermissions: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlertAmber.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .border(1.dp, AlertAmber.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = AlertAmber)
                Column {
                    Text(text = "Telemetry Permissions Required", style = MaterialTheme.typography.titleMedium, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AlertAmber)
                    Text(text = "Grant Location & Phone State to unlock Wi-Fi SSID, frequency & cell tower signal.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = AlertAmber, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DangerRed.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .border(1.dp, DangerRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = DangerRed)
                Text(text = message, fontSize = 12.sp, color = DangerRed)
            }
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Dismiss", tint = DangerRed)
            }
        }
    }
}

@Composable
private fun ControlsDeck(
    isProbing: Boolean,
    isThroughputProbing: Boolean,
    onTriggerProbe: () -> Unit,
    onTriggerThroughput: () -> Unit,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "Active Telemetry Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onTriggerProbe,
                enabled = !isProbing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black)
            ) {
                if (isProbing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(text = if (isProbing) "Probing..." else "Latency Probe", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onTriggerThroughput,
                enabled = !isThroughputProbing,
                modifier = Modifier.weight(1f)
            ) {
                if (isThroughputProbing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(text = if (isThroughputProbing) "Testing..." else "Throughput Probe", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        OutlinedButton(
            onClick = onClearHistory,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
        ) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Clear Historical Observations", fontSize = 12.sp)
        }
    }
}

