package com.jrgames.blutdruck.ui.screen

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrgames.blutdruck.data.local.MeasurementSession
import com.jrgames.blutdruck.ui.theme.BpBlue
import com.jrgames.blutdruck.ui.theme.BpRed
import com.jrgames.blutdruck.ui.theme.BpAmber
import com.jrgames.blutdruck.ui.theme.BpGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Diagramm-Datenpunkt ───────────────────────────────────────────────────────

private data class ChartPoint(
    val dateMillis: Long,
    val avgSys:     Float,
    val avgDia:     Float,
    val avgPulse:   Float,
)

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    sessions: List<MeasurementSession>,
    onBack: () -> Unit,
) {
    var period by remember { mutableStateOf(HistoryPeriod.WEEK) }
    var offset by remember { mutableIntStateOf(0) }
    LaunchedEffect(period) { offset = 0 }

    val filtered = remember(sessions, period, offset) {
        val range = historyPeriodRange(period, offset)
        if (range == null) sessions else sessions.filter { it.timestampMillis in range.first until range.second }
    }

    // Aggregiere nach Tag: Durchschnitt aller Sitzungen des Tages
    val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val chartPoints = remember(filtered) {
        filtered
            .groupBy { dayFmt.format(Date(it.timestampMillis)) }
            .map { (_, group) ->
                ChartPoint(
                    dateMillis = group.minOf { it.timestampMillis },
                    avgSys     = group.map { it.avgSys }.average().toFloat(),
                    avgDia     = group.map { it.avgDia }.average().toFloat(),
                    avgPulse   = group.map { it.avgPulse }.average().toFloat(),
                )
            }
            .sortedBy { it.dateMillis }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagramm") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Zurück") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Periode-Tabs
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryPeriod.entries.forEach { p ->
                    FilterChip(
                        selected = p == period,
                        onClick  = { period = p },
                        label    = { Text(p.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Navigation
            if (period != HistoryPeriod.ALL) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { offset-- }) {
                        Icon(Icons.Default.ChevronLeft, "Zurück")
                    }
                    Text(historyPeriodLabel(period, offset),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (offset == 0) BpBlue else MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = { if (offset < 0) offset++ }, enabled = offset < 0) {
                        Icon(Icons.Default.ChevronRight, "Vorwärts")
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
            }

            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            if (chartPoints.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Keine Messungen in diesem Zeitraum.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Legende
                ChartLegend()
                Spacer(Modifier.height(12.dp))

                // Diagramm
                Card(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    shape    = RoundedCornerShape(14.dp),
                ) {
                    Box(Modifier.fillMaxSize().padding(8.dp)) {
                        BpLineChart(
                            points   = chartPoints,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Statistik-Zusammenfassung
                if (chartPoints.isNotEmpty()) {
                    ChartStats(chartPoints)
                }
            }
        }
    }
}

// ── Legende ───────────────────────────────────────────────────────────────────

@Composable
private fun ChartLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        LegendItem(color = BpRed,   label = "Systolisch")
        LegendItem(color = BpBlue,  label = "Diastolisch")
        LegendItem(color = BpAmber, label = "Puls")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(Modifier.size(width = 16.dp, height = 3.dp), color = color, shape = RoundedCornerShape(2.dp)) {}
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Liniendiagramm (Canvas) ───────────────────────────────────────────────────

@Composable
private fun BpLineChart(
    points:   List<ChartPoint>,
    modifier: Modifier = Modifier,
) {
    val sysColor   = BpRed
    val diaColor   = BpBlue
    val pulseColor = BpAmber
    val gridColor  = Color(0xFFDDDDDD)
    val refColor   = BpRed.copy(alpha = 0.35f)  // Grenzlinie 140 mmHg

    Canvas(modifier = modifier) {
        val leftPad   = 44.dp.toPx()
        val rightPad  = 10.dp.toPx()
        val topPad    = 10.dp.toPx()
        val bottomPad = 32.dp.toPx()

        val cW = size.width  - leftPad - rightPad
        val cH = size.height - topPad  - bottomPad

        // Y-Bereich dynamisch, mindestens 50..200
        val allValues = points.flatMap { listOf(it.avgSys, it.avgDia, it.avgPulse) }
        val rawMin = (allValues.minOrNull() ?: 50f) - 10f
        val rawMax = (allValues.maxOrNull() ?: 200f) + 10f
        val yMin = (rawMin / 10).toInt() * 10f
        val yMax = ((rawMax / 10).toInt() + 1) * 10f

        // X-Bereich: erster bis letzter Tag
        val xMinMs = points.first().dateMillis.toFloat()
        val xMaxMs = points.last().dateMillis.toFloat()
        val xRange = if (xMaxMs > xMinMs) xMaxMs - xMinMs else 1f

        fun yPx(v: Float) = topPad + cH * (1f - (v - yMin) / (yMax - yMin))
        fun xPx(t: Long)  = leftPad + cW * ((t.toFloat() - xMinMs) / xRange)

        // ── Horizontale Gitterlinien und Y-Labels ─────────────────────
        val textPaint = Paint().apply {
            textSize  = 9.sp.toPx()
            color     = android.graphics.Color.GRAY
            textAlign = Paint.Align.RIGHT
        }

        var yVal = yMin
        while (yVal <= yMax) {
            val yp = yPx(yVal)
            drawLine(gridColor, Offset(leftPad, yp), Offset(size.width - rightPad, yp), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(
                "${yVal.toInt()}", leftPad - 4.dp.toPx(), yp + 4.dp.toPx(), textPaint)
            yVal += 20f
        }

        // ── Referenzlinien (140 = Hypertonie Grad 1; 120 = obere Normal-Grenze) ──
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
        drawLine(
            color       = refColor,
            start       = Offset(leftPad, yPx(140f)),
            end         = Offset(size.width - rightPad, yPx(140f)),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect  = dash,
        )

        // ── X-Achse mit Datumslabels ──────────────────────────────────
        val labelFmt = SimpleDateFormat("dd.MM", Locale.getDefault())
        val xPaint = Paint().apply {
            textSize  = 9.sp.toPx()
            color     = android.graphics.Color.GRAY
            textAlign = Paint.Align.CENTER
        }
        // Maximal 7 Labels
        val step = maxOf(1, points.size / 7)
        points.indices.filter { it % step == 0 || it == points.size - 1 }.forEach { i ->
            val p  = points[i]
            val xp = xPx(p.dateMillis)
            drawLine(gridColor, Offset(xp, yPx(yMax)), Offset(xp, topPad + cH + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(
                labelFmt.format(Date(p.dateMillis)),
                xp, size.height - 6.dp.toPx(), xPaint)
        }

        // ── Linien + Punkte für Sys, Dia, Puls ───────────────────
        // Lokale Hilfsfunktion (anderer Name, damit drawLine aus DrawScope nicht überschattet wird)
        fun plotSeries(pts: List<ChartPoint>, getValue: (ChartPoint) -> Float, color: Color) {
            if (pts.size >= 2) {
                val path = Path()
                pts.forEachIndexed { i, pt ->
                    val x = xPx(pt.dateMillis); val y = yPx(getValue(pt))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
            }
            pts.forEach { pt ->
                drawCircle(color, 4.dp.toPx(), Offset(xPx(pt.dateMillis), yPx(getValue(pt))))
            }
        }

        plotSeries(points, { it.avgSys   }, sysColor)
        plotSeries(points, { it.avgDia   }, diaColor)
        plotSeries(points, { it.avgPulse }, pulseColor)
    }
}

// ── Statistik-Kacheln ─────────────────────────────────────────────────────────

@Composable
private fun ChartStats(points: List<ChartPoint>) {
    val avgSys   = points.map { it.avgSys }.average()
    val avgDia   = points.map { it.avgDia }.average()
    val avgPulse = points.map { it.avgPulse }.average()
    val maxSys   = points.maxOf { it.avgSys }
    val minSys   = points.minOf { it.avgSys }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("⌀ Systolisch",   "${"%.0f".format(avgSys)} mmHg",  BpRed,   Modifier.weight(1f))
        StatCard("⌀ Diastolisch",  "${"%.0f".format(avgDia)} mmHg",  BpBlue,  Modifier.weight(1f))
        StatCard("⌀ Puls",         "${"%.0f".format(avgPulse)} /min", BpAmber, Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("Max Sys", "${"%.0f".format(maxSys)} mmHg", BpRed.copy(alpha = 0.7f), Modifier.weight(1f))
        StatCard("Min Sys", "${"%.0f".format(minSys)} mmHg", BpGreen,                   Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = color, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.bodyMedium,
                color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}


