package com.jrgames.blutdruck.ui.screen

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrgames.blutdruck.data.local.MeasurementSession
import com.jrgames.blutdruck.ui.theme.BpBlue
import com.jrgames.blutdruck.ui.theme.BpRed
import com.jrgames.blutdruck.ui.theme.BpAmber
import com.jrgames.blutdruck.ui.theme.BpGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ── Diagramm-Datenpunkt (pro Tag) ────────────────────────────────────────────

private data class ChartPoint(
    val dateMillis: Long,
    val avgSys:  Float, val minSys:  Float, val maxSys:  Float,
    val avgDia:  Float, val minDia:  Float, val maxDia:  Float,
    val avgPulse: Float,
    val count:    Int,
)

private fun buildChartPoints(sessions: List<MeasurementSession>): List<ChartPoint> {
    val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sessions
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

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    sessions: List<MeasurementSession>,
    onBack: () -> Unit,
) {
    val nowMs        = remember { System.currentTimeMillis() }
    val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000

    val allPoints = remember(sessions) { buildChartPoints(sessions) }

    // X-Datengrenzen (mind. 1 Tag Breite)
    val dataXMin = remember(allPoints) {
        allPoints.firstOrNull()?.dateMillis ?: (nowMs - thirtyDaysMs)
    }
    val dataXMax = remember(allPoints) {
        (allPoints.lastOrNull()?.dateMillis ?: nowMs) + 24 * 60 * 60 * 1000L
    }
    val oneDayMs = 24 * 60 * 60 * 1000L

    val autoYMin = remember(allPoints) {
        val v = allPoints.flatMap { listOf(it.minSys, it.maxSys, it.minDia, it.maxDia, it.avgPulse) }
        ((v.minOrNull() ?: 50f) - 10f).let { (it / 10).toInt() * 10f }
    }
    val autoYMax = remember(allPoints) {
        val v = allPoints.flatMap { listOf(it.minSys, it.maxSys, it.minDia, it.maxDia, it.avgPulse) }
        ((v.maxOrNull() ?: 200f) + 10f).let { ((it / 10).toInt() + 1) * 10f }
    }

    var xStartMs by remember { mutableLongStateOf(nowMs - thirtyDaysMs) }
    var xEndMs   by remember { mutableLongStateOf(nowMs) }
    var viewYMin by remember { mutableFloatStateOf(autoYMin) }
    var viewYMax by remember { mutableFloatStateOf(autoYMax) }

    // Y: 20–280 mmHg absolutes Limit
    val absYMin = 20f
    val absYMax = 280f

    fun clampViewport() {
        // Y: Bereich einhalten, mind. 20 Einheiten sichtbar
        val yRange = (viewYMax - viewYMin).coerceIn(20f, absYMax - absYMin)
        viewYMin = viewYMin.coerceIn(absYMin, absYMax - yRange)
        viewYMax = viewYMin + yRange

        // X: mind. 1 Tag, max. gesamter Datenbereich; immer innerhalb der Datengrenzen
        val xRange = (xEndMs - xStartMs).coerceAtLeast(oneDayMs)
        xStartMs  = xStartMs.coerceIn(dataXMin, dataXMax - xRange)
        xEndMs    = xStartMs + xRange
    }

    fun resetView() {
        xStartMs = nowMs - thirtyDaysMs
        xEndMs   = nowMs
        viewYMin = autoYMin
        viewYMax = autoYMax
    }

    val visiblePoints = remember(allPoints, xStartMs, xEndMs) {
        allPoints.filter { it.dateMillis in xStartMs..xEndMs }
    }
    val filteredSessions = remember(sessions, xStartMs, xEndMs) {
        sessions.filter { it.timestampMillis in xStartMs..xEndMs }
    }
    // Für Statistik immer sichtbare Sessions nutzen, Fallback auf alle
    val statSessions = if (filteredSessions.isNotEmpty()) filteredSessions else sessions

    var chartSize by remember { mutableStateOf(IntSize.Zero) }

    // Padding-Konstanten (dp) – müssen mit Canvas-Padding übereinstimmen
    val leftPadDp   = 44
    val rightPadDp  = 10
    val topPadDp    = 10
    val bottomPadDp = 32
    val innerPadDp  = 8   // .padding(8.dp) in der Box

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagramm") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Zurück") }
                },
                actions = {
                    IconButton(onClick = ::resetView) {
                        Icon(Icons.Default.Refresh, "Zurücksetzen")
                    }
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

            val labelFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            Text(
                "${labelFmt.format(Date(xStartMs))}  –  ${labelFmt.format(Date(xEndMs))}" +
                        "  ·  ${visiblePoints.size} Tage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Ziehen zum Verschieben  ·  Zwei Finger zum Zoomen",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            if (allPoints.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("Keine Messungen vorhanden.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                ChartLegend(hasRange = visiblePoints.any { it.count > 1 })
                Spacer(Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    shape    = RoundedCornerShape(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadDp.dp)
                            .onSizeChanged { chartSize = it }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)

                                    var prev1: Offset? = null
                                    var prev2: Offset? = null

                                    do {
                                        val event   = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break

                                        val cW = chartSize.width  - (leftPadDp + rightPadDp + innerPadDp * 2).dp.toPx()
                                        val cH = chartSize.height - (topPadDp  + bottomPadDp + innerPadDp * 2).dp.toPx()
                                        if (cW <= 0f || cH <= 0f) { prev1 = null; prev2 = null; break }

                                        val xRange   = (xEndMs - xStartMs).toFloat()
                                        val yRange   = viewYMax - viewYMin
                                        val msPerPx  = xRange / cW
                                        val valPerPx = yRange / cH

                                        if (pressed.size == 1) {
                                            val cur = pressed[0].position
                                            val p   = prev1
                                            if (p != null) {
                                                val dxMs = (-(cur.x - p.x) * msPerPx).toLong()
                                                val dyVal = (cur.y - p.y) * valPerPx
                                                xStartMs += dxMs
                                                xEndMs   += dxMs
                                                viewYMin += dyVal
                                                viewYMax += dyVal
                                                clampViewport()
                                            }
                                            prev1 = cur
                                            prev2 = null
                                            pressed[0].consume()
                                        } else if (pressed.size >= 2) {
                                            val cur1 = pressed[0].position
                                            val cur2 = pressed[1].position
                                            val p1   = prev1
                                            val p2   = prev2

                                            if (p1 != null && p2 != null) {
                                                val prevDx = abs(p2.x - p1.x).coerceAtLeast(1f)
                                                val prevDy = abs(p2.y - p1.y).coerceAtLeast(1f)
                                                val curDx  = abs(cur2.x - cur1.x).coerceAtLeast(1f)
                                                val curDy  = abs(cur2.y - cur1.y).coerceAtLeast(1f)

                                                // Zoom nur berechnen wenn Finger weit genug auseinander
                                                // und Änderung klein genug – verhindert Sprünge
                                                val rawZoomX = if (prevDx > 30f && curDx > 30f) prevDx / curDx else 1f
                                                val rawZoomY = if (prevDy > 30f && curDy > 30f) prevDy / curDy else 1f
                                                val zoomX = (1f + (rawZoomX - 1f) * 0.3f).coerceIn(0.95f, 1.05f)
                                                val zoomY = (1f + (rawZoomY - 1f) * 0.3f).coerceIn(0.95f, 1.05f)

                                                // Pinch-Zentrum in Datenwerten
                                                val cxPx   = (p1.x + p2.x) / 2f - (leftPadDp + innerPadDp).dp.toPx()
                                                val cyPx   = (p1.y + p2.y) / 2f - (topPadDp  + innerPadDp).dp.toPx()
                                                val cxMs   = xStartMs + (cxPx * msPerPx).toLong()
                                                val cyVal  = viewYMax  - cyPx * valPerPx

                                                val newXRange = (xRange * zoomX).coerceAtLeast(1000L * 60 * 60 * 24 * 1f)
                                                val newYRange = (yRange * zoomY).coerceAtLeast(20f)

                                                // Pan der Mittelpunkte
                                                val midDxMs  = (-((cur1.x + cur2.x) / 2f - (p1.x + p2.x) / 2f) * msPerPx).toLong()
                                                val midDyVal = ((cur1.y + cur2.y) / 2f - (p1.y + p2.y) / 2f) * valPerPx

                                                xStartMs = cxMs - (newXRange / 2).toLong() + midDxMs
                                                xEndMs   = cxMs + (newXRange / 2).toLong() + midDxMs
                                                viewYMin = cyVal - newYRange / 2 + midDyVal
                                                viewYMax = cyVal + newYRange / 2 + midDyVal
                                                clampViewport()
                                            }
                                            prev1 = cur1
                                            prev2 = cur2
                                            pressed.forEach { it.consume() }
                                        }
                                    } while (true)
                                }
                            },
                    ) {
                        BpLineChart(
                            points   = allPoints,
                            xStartMs = xStartMs,
                            xEndMs   = xEndMs,
                            yMin     = viewYMin,
                            yMax     = viewYMax,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                ChartStats(statSessions)
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
        if (hasRange) LegendRangeItem(color = BpRed.copy(alpha = 0.25f), label = "Tagesbereich")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(Modifier.size(width = 16.dp, height = 3.dp), color = color, shape = RoundedCornerShape(2.dp)) {}
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendRangeItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(Modifier.size(width = 6.dp, height = 14.dp), color = color, shape = RoundedCornerShape(3.dp)) {}
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Liniendiagramm (Canvas) mit Viewport ─────────────────────────────────────

@Composable
private fun BpLineChart(
    points:   List<ChartPoint>,
    xStartMs: Long,
    xEndMs:   Long,
    yMin:     Float,
    yMax:     Float,
    modifier: Modifier = Modifier,
) {
    val sysColor      = BpRed
    val diaColor      = BpBlue
    val pulseColor    = BpAmber
    val gridColor     = Color(0xFFDDDDDD)
    val refColor      = BpRed.copy(alpha = 0.35f)
    val sysRangeColor = BpRed.copy(alpha = 0.22f)
    val diaRangeColor = BpBlue.copy(alpha = 0.18f)

    // Punkte im sichtbaren Fenster + je 1 außerhalb für Linienfortsetzung
    val visible = remember(points, xStartMs, xEndMs) {
        val first = points.indexOfFirst { it.dateMillis >= xStartMs }.coerceAtLeast(0)
        val last  = points.indexOfLast  { it.dateMillis <= xEndMs  }
        if (last < 0 || first > last) emptyList()
        else points.subList((first - 1).coerceAtLeast(0), (last + 2).coerceAtMost(points.size))
    }

    Canvas(modifier = modifier) {
        val leftPad   = 44.dp.toPx()
        val rightPad  = 10.dp.toPx()
        val topPad    = 10.dp.toPx()
        val bottomPad = 32.dp.toPx()
        val cW = size.width  - leftPad - rightPad
        val cH = size.height - topPad  - bottomPad

        val safeYMin = if (yMax > yMin) yMin else yMin - 1f
        val safeYMax = if (yMax > yMin) yMax else yMin + 1f
        val xRange   = if (xEndMs > xStartMs) (xEndMs - xStartMs).toFloat() else 1f

        fun yPx(v: Float) = topPad  + cH * (1f - (v - safeYMin) / (safeYMax - safeYMin))
        fun xPx(t: Long)  = leftPad + cW * ((t.toFloat() - xStartMs) / xRange)

        // Y-Gitterlinien
        val textPaint = Paint().apply {
            textSize  = 9.sp.toPx()
            color     = android.graphics.Color.GRAY
            textAlign = Paint.Align.RIGHT
        }
        val ySpan    = safeYMax - safeYMin
        val gridStep = when { ySpan <= 40f -> 5f; ySpan <= 100f -> 10f; ySpan <= 200f -> 20f; else -> 40f }
        var yVal = (safeYMin / gridStep).toInt() * gridStep
        while (yVal <= safeYMax) {
            val yp = yPx(yVal)
            if (yp in topPad..(topPad + cH)) {
                drawLine(gridColor, Offset(leftPad, yp), Offset(size.width - rightPad, yp), strokeWidth = 1.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText("${yVal.toInt()}", leftPad - 4.dp.toPx(), yp + 4.dp.toPx(), textPaint)
            }
            yVal += gridStep
        }

        // Referenzlinie 140
        val ref140y = yPx(140f)
        if (ref140y in topPad..(topPad + cH)) {
            drawLine(
                color       = refColor,
                start       = Offset(leftPad, ref140y),
                end         = Offset(size.width - rightPad, ref140y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(8f, 4f)),
            )
        }

        // X-Achse
        val labelFmt = SimpleDateFormat("dd.MM", Locale.getDefault())
        val xPaint = Paint().apply {
            textSize  = 9.sp.toPx()
            color     = android.graphics.Color.GRAY
            textAlign = Paint.Align.CENTER
        }
        if (visible.size >= 2) {
            val step = maxOf(1, visible.size / 7)
            visible.indices.filter { it % step == 0 || it == visible.size - 1 }.forEach { i ->
                val xp = xPx(visible[i].dateMillis)
                if (xp in leftPad..(leftPad + cW)) {
                    drawLine(gridColor, Offset(xp, topPad), Offset(xp, topPad + cH + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(labelFmt.format(Date(visible[i].dateMillis)), xp, size.height - 6.dp.toPx(), xPaint)
                }
            }
        }

        // Range-Balken
        val barWidth = 6.dp.toPx()
        visible.filter { it.count > 1 }.forEach { pt ->
            val xp = xPx(pt.dateMillis)
            drawLine(sysRangeColor, Offset(xp, yPx(pt.maxSys)), Offset(xp, yPx(pt.minSys)), strokeWidth = barWidth, cap = StrokeCap.Round)
            drawLine(diaRangeColor, Offset(xp, yPx(pt.maxDia)), Offset(xp, yPx(pt.minDia)), strokeWidth = barWidth * 0.7f, cap = StrokeCap.Round)
        }

        // Serien
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
                val xp = xPx(pt.dateMillis)
                if (xp in (leftPad - 8.dp.toPx())..(leftPad + cW + 8.dp.toPx()))
                    drawCircle(color, 4.dp.toPx(), Offset(xp, yPx(getValue(pt))))
            }
        }

        plotSeries(visible, { it.avgSys   }, sysColor)
        plotSeries(visible, { it.avgDia   }, diaColor)
        plotSeries(visible, { it.avgPulse }, pulseColor)
    }
}

// ── Statistik-Kacheln ─────────────────────────────────────────────────────────

@Composable
private fun ChartStats(sessions: List<MeasurementSession>) {
    val allSys   = sessions.map { it.avgSys }
    val allDia   = sessions.map { it.avgDia }
    val allPulse = sessions.map { it.avgPulse }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatRowCard("Systolisch",  BpRed,   allSys.min(),   allSys.average().toFloat(),   allSys.max(),   "mmHg")
        StatRowCard("Diastolisch", BpBlue,  allDia.min(),   allDia.average().toFloat(),   allDia.max(),   "mmHg")
        StatRowCard("Puls",        BpAmber, allPulse.min(), allPulse.average().toFloat(), allPulse.max(), "/min")
    }
}

@Composable
private fun StatRowCard(label: String, color: Color, minVal: Float, avgVal: Float, maxVal: Float, unit: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.07f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.width(88.dp),
                style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
            listOf("Min" to minVal, "⌀" to avgVal, "Max" to maxVal).forEach { (tag, v) ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tag, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
                    Text("${"%.0f".format(v)} $unit",
                        style = MaterialTheme.typography.bodySmall, color = color,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
