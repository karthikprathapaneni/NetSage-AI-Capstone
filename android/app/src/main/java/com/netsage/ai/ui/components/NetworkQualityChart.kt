package com.netsage.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netsage.ai.domain.model.NetworkObservation
import com.netsage.ai.ui.theme.CyberCyan
import com.netsage.ai.ui.theme.ElectricGreen
import com.netsage.ai.ui.theme.NeonPurple

enum class ChartMetric(val label: String, val unit: String, val color: Color) {
    LATENCY("Latency", "ms", CyberCyan),
    SIGNAL("Signal", "dBm", ElectricGreen),
    THROUGHPUT("Throughput", "Mbps", NeonPurple)
}

@Composable
fun NetworkQualityChart(
    observations: List<NetworkObservation>,
    modifier: Modifier = Modifier
) {
    var selectedMetric by remember { mutableStateOf(ChartMetric.LATENCY) }

    val chronologicalPoints = remember(observations) {
        observations.reversed()
    }

    val values: List<Float> = remember(chronologicalPoints, selectedMetric) {
        chronologicalPoints.mapNotNull { obs ->
            when (selectedMetric) {
                ChartMetric.LATENCY -> obs.effectiveLatencyMs?.toFloat()
                ChartMetric.SIGNAL -> obs.signalMetrics.signalDbm?.toFloat()
                ChartMetric.THROUGHPUT -> {
                    obs.bandwidthMetrics.activeThroughputMbps?.toFloat()
                        ?: obs.bandwidthMetrics.downstreamEstimateKbps?.let { it / 1000f }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Historical Quality Trend",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${values.size} data points recorded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChartMetric.values().forEach { metric ->
                    FilterChip(
                        selected = selectedMetric == metric,
                        onClick = { selectedMetric = metric },
                        label = {
                            Text(
                                text = metric.label,
                                fontSize = 11.sp,
                                fontWeight = if (selectedMetric == metric) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = metric.color.copy(alpha = 0.2f),
                            selectedLabelColor = metric.color
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (values.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Awaiting sufficient telemetry points (min 2 required)...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val minVal = values.minOrNull() ?: 0f
            val maxVal = values.maxOrNull() ?: 100f
            val avgVal = values.average().toFloat()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatBadge(
                    label = "Min",
                    value = String.format("%.1f %s", minVal, selectedMetric.unit)
                )
                StatBadge(
                    label = "Average",
                    value = String.format("%.1f %s", avgVal, selectedMetric.unit),
                    highlightColor = selectedMetric.color
                )
                StatBadge(
                    label = "Max",
                    value = String.format("%.1f %s", maxVal, selectedMetric.unit)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val chartColor = selectedMetric.color
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                val width = size.width
                val height = size.height

                val gridLines = 4
                for (i in 0..gridLines) {
                    val y = height * (i.toFloat() / gridLines)
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.15f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                val range = if (maxVal == minVal) 1f else (maxVal - minVal)
                val stepX = width / (values.size - 1)

                val points = values.mapIndexed { index, v ->
                    val x = index * stepX
                    val normalized = (v - minVal) / range
                    val y = height - (normalized * (height - 20f)) - 10f
                    Offset(x, y)
                }

                val fillPath = Path().apply {
                    moveTo(points.first().x, height)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, height)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            chartColor.copy(alpha = 0.35f),
                            chartColor.copy(alpha = 0.02f)
                        ),
                        startY = 0f,
                        endY = height
                    )
                )

                val strokePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }

                drawPath(
                    path = strokePath,
                    color = chartColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                points.forEach { pt ->
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = chartColor,
                        radius = 2.dp.toPx(),
                        center = pt
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBadge(
    label: String,
    value: String,
    highlightColor: Color? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = highlightColor ?: MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

