package com.jrgames.blutdruck.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
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
import com.jrgames.blutdruck.ui.theme.BpGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    latestSession: MeasurementSession?,
    onStartMeasure: () -> Unit,
    onHistory: () -> Unit,
    onChart: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Titel ─────────────────────────────────────────────────────
            Text(
                "Blutdruck\nTracker",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = BpBlue,
                lineHeight = 40.sp,
            )

            // ── Letzter Messwert ─────────────────────────────────────────
            if (latestSession != null) {
                LatestCard(latestSession)
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Noch keine Messungen vorhanden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LatestCard(session: MeasurementSession) {
    val cat      = BpClassifier.classify(session.avgSys, session.avgDia)
    val cardCol  = BpClassifier.cardColor(cat)
    val textCol  = BpClassifier.textColor(cat)
    val dateFmt  = SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault())
    val dateStr  = dateFmt.format(Date(session.timestampMillis))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = cardCol),
        shape    = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Letzte Messung", style = MaterialTheme.typography.labelLarge,
                    color = textCol.copy(alpha = 0.75f))
                Text(dateStr, style = MaterialTheme.typography.labelSmall,
                    color = textCol.copy(alpha = 0.6f))
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${session.avgSys.toInt()} / ${session.avgDia.toInt()}",
                    fontSize = 42.sp, fontWeight = FontWeight.Bold, color = textCol,
                )
                Text("mmHg", style = MaterialTheme.typography.bodyMedium,
                    color = textCol.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 6.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Puls: ${session.avgPulse.toInt()} /min",
                    style = MaterialTheme.typography.bodyMedium, color = textCol)
                Text("Arm: ${if (session.arm == "LINKS") "Links" else "Rechts"} · ${session.evaluationLabel}",
                    style = MaterialTheme.typography.bodySmall, color = textCol.copy(alpha = 0.7f))
            }
            session.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    "💬 $note",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = textCol.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Surface(
                color = textCol.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    cat.label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = textCol,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

