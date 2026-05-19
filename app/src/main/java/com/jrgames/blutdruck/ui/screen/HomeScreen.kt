package com.jrgames.blutdruck.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrgames.blutdruck.data.local.MeasurementSession
import com.jrgames.blutdruck.domain.BpClassifier
import com.jrgames.blutdruck.ui.theme.BpBlue
import com.jrgames.blutdruck.ui.theme.BpPurple

@Composable
fun HomeScreen(
    sessions: List<MeasurementSession> = emptyList(),
    onStartMeasure: () -> Unit,
    onHistory: () -> Unit,
    onChart: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Titel + Settings-Icon ──────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "Blutdruck\nTracker",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = BpBlue,
                    lineHeight = 40.sp,
                )
                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Einstellungen",
                        tint = BpBlue,
                    )
                }
            }

            // ── Durchschnitte mit Einordnung ──────────────────────────────
            if (sessions.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "Noch keine Messungen vorhanden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                AvgClassCard(sessions)
            }

            Spacer(Modifier.weight(1f))

            // ── Aktions-Buttons ──────────────────────────────────────────
            Button(
                onClick = onStartMeasure,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BpBlue),
            ) {
                Icon(Icons.Default.AddCircle, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Messung starten", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onHistory,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Verlauf")
                }
                OutlinedButton(
                    onClick = onChart,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.BarChart, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Diagramm")
                }
            }

            OutlinedButton(
                onClick = onStats,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.QueryStats, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Statistiken & Verteilung")
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Durchschnitte mit Einordnung ─────────────────────────────────────────────

@Composable
private fun AvgClassCard(sessions: List<MeasurementSession>) {
    val avgSys        = sessions.map { it.avgSys }.average().toFloat()
    val avgDia        = sessions.map { it.avgDia }.average().toFloat()
    val avgPulsedruck = sessions.map { it.avgPulsedruck }.average().toFloat()

    val sysCat = BpClassifier.classifySys(avgSys)
    val diaCat = BpClassifier.classifyDia(avgDia)
    val ppCat  = BpClassifier.classifyPulsedruck(avgPulsedruck)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Ø Blutdruck",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${sessions.size} Messung${if (sessions.size != 1) "en" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            AvgClassRow(
                label    = "Systolisch",
                value    = "${avgSys.toInt()} mmHg",
                catLabel = sysCat.label,
                bgColor  = BpClassifier.cardColor(sysCat),
                fgColor  = BpClassifier.textColor(sysCat),
            )
            AvgClassRow(
                label    = "Diastolisch",
                value    = "${avgDia.toInt()} mmHg",
                catLabel = diaCat.label,
                bgColor  = BpClassifier.cardColor(diaCat),
                fgColor  = BpClassifier.textColor(diaCat),
            )
            AvgClassRow(
                label    = "Pulsdruck",
                value    = "${avgPulsedruck.toInt()} mmHg",
                catLabel = ppCat.label,
                bgColor  = ppCat.cardColor,
                fgColor  = ppCat.textColor,
                labelColor = BpPurple,
            )
        }
    }
}

@Composable
private fun AvgClassRow(
    label:      String,
    value:      String,
    catLabel:   String,
    bgColor:    Color,
    fgColor:    Color,
    labelColor: Color? = null,
) {
    Surface(
        color    = bgColor,
        shape    = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    style  = MaterialTheme.typography.labelMedium,
                    color  = (labelColor ?: fgColor).copy(alpha = 0.75f),
                )
                Text(
                    value,
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = labelColor ?: fgColor,
                )
            }
            Surface(
                color = fgColor.copy(alpha = 0.13f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    catLabel,
                    modifier   = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style      = MaterialTheme.typography.labelMedium,
                    color      = fgColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
