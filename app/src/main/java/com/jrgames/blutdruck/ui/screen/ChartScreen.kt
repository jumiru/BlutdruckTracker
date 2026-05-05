package com.jrgames.blutdruck.ui.screen

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.StrokeCap
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

// ── Diagramm-Datenpunkt (pro Tag) ────────────────────────────────────────────

private data class ChartPoint(
    val dateMillis: Long,
    // Sys
    val avgSys:  Float,
    val minSys:  Float,
    val maxSys:  Float,
    // Dia
    val avgDia:  Float,
    val minDia:  Float,
    val maxDia:  Float,
    // Puls
    val avgPulse: Float,
    // Anzahl Sitzungen an diesem Tag (für Range-Balken-Entscheidung)
    val count:    Int,
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

    val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val chartPoints = remember(filtered) {
        filtered
            .groupBy { dayFmt.format(Date(it.timestampMillis)) }
            .map { (_, group) ->
                val sysList   = group.map { it.avgSys }
                val diaList   = group.map { it.avgDia }
                val pulseList = group.map { it.avgPulse }
                ChartPoint(
                    dateMillis = group.minOf { it.timestampMillis },
                    avgSys     = sysList.average().toFloat(),
                    minSys     = sysList.min(),
                    maxSys     = sysList.max(),
                    avgDia     = diaList.average().toFloat(),
                    minDia     = diaList.min(),
                    maxDia     = diaList.max(),
                    avgPulse   = pulseList.average().toFloat(),
                    count      = group.size,
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
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
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("Keine Messungen in diesem Zeitraum.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Legende
                ChartLegend(hasRange = chartPoints.any { it.count > 1 })
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

                // Statistik-Zusammenfassung (auf Basis aller gefilterten Sessions)
                ChartStats(filtered)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Legende ───────────────────────────────────────────────────────────────────

@Composable
private fun ChartLegend(hasRange: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(color = BpRed,   label = "Systolisch")
        LegendItem(color = BpBlue,  label = "Diastolisch")
        LegendItem(color = BpAmber, label = "Puls")
        if (hasRange) {
            LegendRangeItem(color = BpRed.copy(alpha = 0.25f), label = "Tagesbereich")
        }
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

@Composable
private fun LegendRangeItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(Modifier.size(width = 6.dp, height = 14.dp), color = color, shape = RoundedCornerShape(3.dp)) {}
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
    val sysColor      = BpRed
    val diaColor      = BpBlue
    val pulseColor    = BpAmber
    val gridColor     = Color(0xFFDDDDDD)
    val refColor      = BpRed.copy(alpha = 0.35f)
    val sysRangeColor = BpRed.copy(alpha = 0.22f)
    val diaRangeColor = BpBlue.copy(alpha = 0.18f)

    Canvas(modifier = modifier) {
        val leftPad   = 44.dp.toPx()
        val rightPad  = 10.dp.toPx()
        val topPad    = 10.dp.toPx()
        val bottomPad = 32.dp.toPx()

        val cW = size.width  - leftPad - rightPad
        val cH = size.height - topPad  - bottomPad

        // Y-Bereich: alle Werte inkl. Min/Max
        val allValues = points.flatMap {
            listOf(it.minSys, it.maxSys, it.minDia, it.maxDia, it.avgPulse)
        }
        val rawMin = (allValues.minOrNull() ?: 50f) - 10f
        val rawMax = (allValues.maxOrNull() ?: 200f) + 10f
        val yMin = (rawMin / 10).toInt() * 10f
        val yMax = ((rawMax / 10).toInt() + 1) * 10f

        val xMinMs = points.first().dateMillis.toFloat()
        val xMaxMs = points.last().dateMillis.toFloat()
        val xRange = if (xMaxMs > xMinMs) xMaxMs - xMinMs else 1f

        fun yPx(v: Float) = topPad + cH * (1f - (v - yMin) / (yMax - yMin))
        fun xPx(t: Long)  = leftPad + cW * ((t.toFloat() - xMinMs) / xRange)

        // ── Gitterlinien + Y-Labels ───────────────────────────────────
        val textPaint = Paint().apply {
            textSize  = 9.sp.toPx()
            color     = android.graphics.Color.GRAY
            textAlign = Paint.Align.RIGHT
        }
        var yVal = yMin
        while (yVal <= yMax) {
            val yp = yPx(yVal)
            drawLine(gridColor, Offset(leftPad, yp), Offset(size.width - rightPad, yp), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("${yVal.toInt()}", leftPad - 4.dp.toPx(), yp + 4.dp.toPx(), textPaint)
            yVal += 20f
        }

        // ── Referenzlinie 140 mmHg ────────────────────────────────────
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
        drawLine(
            color       = refColor,
            start       = Offset(leftPad, yPx(140f)),
            end         = Offset(size.width - rightPad, yPx(140f)),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect  = dash,
        )

        // ── X-Achse ───────────────────────────────────────────────────
        val labelFmt = SimpleDateFormat("dd.MM", Locale.getDefault())
        val xPaint = Paint().apply {
            textSize  = 9.sp.toPx()
            color     = android.graphics.Color.GRAY
            textAlign = Paint.Align.CENTER
        }
        val step = maxOf(1, points.size / 7)
        points.indices.filter { it % step == 0 || it == points.size - 1 }.forEach { i ->
            val p  = points[i]
            val xp = xPx(p.dateMillis)
            drawLine(gridColor, Offset(xp, yPx(yMax)), Offset(xp, topPad + cH + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(labelFmt.format(Date(p.dateMillis)), xp, size.height - 6.dp.toPx(), xPaint)
        }

        // ── Range-Balken (Min–Max pro Tag, wenn mehrere Sitzungen) ────
        val barWidth = 6.dp.toPx()
        points.filter { it.count > 1 }.forEach { pt ->
            val xp = xPx(pt.dateMillis)
            // Sys-Bereich
            drawLine(
                color       = sysRangeColor,
                start       = Offset(xp, yPx(pt.maxSys)),
                end         = Offset(xp, yPx(pt.minSys)),
                strokeWidth = barWidth,
                cap         = StrokeCap.Round,
            )
            // Dia-Bereich
            drawLine(
                color       = diaRangeColor,
                start       = Offset(xp, yPx(pt.maxDia)),
                end         = Offset(xp, yPx(pt.minDia)),
                strokeWidth = barWidth * 0.7f,
                cap         = StrokeCap.Round,
            )
        }

        // ── Linien + Punkte ───────────────────────────────────────────
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
private fun ChartStats(sessions: List<MeasurementSession>) {
    // Echte Min/Avg/Max aus allen Einzelsitzungen (nicht aus Tagesdurchschnitten!)
    val allSys   = sessions.map { it.avgSys }
    val allDia   = sessions.map { it.avgDia }
    val allPulse = sessions.map { it.avgPulse }

    val minSys   = allSys.min()
    val avgSys   = allSys.average().toFloat()
    val maxSys   = allSys.max()

    val minDia   = allDia.min()
    val avgDia   = allDia.average().toFloat()
    val maxDia   = allDia.max()

    val avgPulse = allPulse.average().toFloat()
    val minPulse = allPulse.min()
    val maxPulse = allPulse.max()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Sys-Zeile
        StatRowCard(
            label    = "Systolisch",
            color    = BpRed,
            minVal   = minSys,
            avgVal   = avgSys,
            maxVal   = maxSys,
            unit     = "mmHg",
        )
        // Dia-Zeile
        StatRowCard(
            label    = "Diastolisch",
            color    = BpBlue,
            minVal   = minDia,
            avgVal   = avgDia,
            maxVal   = maxDia,
            unit     = "mmHg",
        )
        // Puls-Zeile
        StatRowCard(
            label    = "Puls",
            color    = BpAmber,
            minVal   = minPulse,
            avgVal   = avgPulse,
            maxVal   = maxPulse,
            unit     = "/min",
        )
    }
}

@Composable
private fun StatRowCard(
    label:  String,
    color:  Color,
    minVal: Float,
    avgVal: Float,
    maxVal: Float,
    unit:   String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.07f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Label
            Text(
                label,
                modifier = Modifier.width(88.dp),
                style    = MaterialTheme.typography.labelMedium,
                color    = color,
                fontWeight = FontWeight.SemiBold,
            )
            // Min / ⌀ / Max
            listOf("Min" to minVal, "⌀" to avgVal, "Max" to maxVal).forEach { (tag, v) ->
                Column(
                    modifier            = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(tag,
                        style  = MaterialTheme.typography.labelSmall,
                        color  = color.copy(alpha = 0.7f))
                    Text(
                        "${"%.0f".format(v)} $unit",
                        style      = MaterialTheme.typography.bodySmall,
                        color      = color,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp,
                    )
                }
            }
        }
    }
}
