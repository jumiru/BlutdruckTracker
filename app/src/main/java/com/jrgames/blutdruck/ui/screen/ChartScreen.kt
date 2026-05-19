package com.jrgames.blutdruck.ui.screen

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrgames.blutdruck.data.local.MeasurementSession
import com.jrgames.blutdruck.ui.theme.BpAmber
import com.jrgames.blutdruck.ui.theme.BpBlue
import com.jrgames.blutdruck.ui.theme.BpGreen
import com.jrgames.blutdruck.ui.theme.BpPurple
import com.jrgames.blutdruck.ui.theme.BpRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ── Serien-Sichtbarkeit ───────────────────────────────────────────────────────

private data class SeriesVisibility(
    val sys:        Boolean = true,
    val dia:        Boolean = true,
    val pulse:      Boolean = true,
    val pulsedruck: Boolean = true,
)

// ── Diagramm-Datenpunkt (pro Tag) ────────────────────────────────────────────

private data class ChartPoint(
    val dateMillis: Long,
    val avgSys:  Float, val minSys:  Float, val maxSys:  Float,
    val avgDia:  Float, val minDia:  Float, val maxDia:  Float,
    val avgPulse: Float,
    val avgPulsedruck: Float,
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
            val avgSys = sysList.average().toFloat()
            val avgDia = diaList.average().toFloat()
            ChartPoint(
                dateMillis    = group.minOf { it.timestampMillis },
                avgSys        = avgSys,
                minSys        = sysList.min(),
                maxSys        = sysList.max(),
                avgDia        = avgDia,
                minDia        = diaList.min(),
                maxDia        = diaList.max(),
                avgPulse      = pulseList.average().toFloat(),
                avgPulsedruck = avgSys - avgDia,
                count         = group.size,
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

    // If data spans less than 30 days, show the full data range; otherwise last 30 days
    val defaultXStart = remember(allPoints) {
        if (dataXMax - dataXMin < thirtyDaysMs) dataXMin else nowMs - thirtyDaysMs
    }
    val defaultXEnd = remember(allPoints) {
        if (dataXMax - dataXMin < thirtyDaysMs) dataXMax else nowMs
    }

    var xStartMs by remember { mutableLongStateOf(defaultXStart) }
    var xEndMs   by remember { mutableLongStateOf(defaultXEnd) }

    fun clampViewport() {
        // X: mind. 1 Tag; immer innerhalb der Datengrenzen
        val xRange    = (xEndMs - xStartMs).coerceAtLeast(oneDayMs)
        val xClampMax = (dataXMax - xRange).coerceAtLeast(dataXMin)
        xStartMs = xStartMs.coerceIn(dataXMin, xClampMax)
        xEndMs   = xStartMs + xRange
    }

    fun resetView() {
        xStartMs = defaultXStart
        xEndMs   = defaultXEnd
    }

    val visiblePoints = remember(allPoints, xStartMs, xEndMs) {
        allPoints.filter { it.dateMillis in xStartMs..xEndMs }
    }
    val filteredSessions = remember(sessions, xStartMs, xEndMs) {
        sessions.filter { it.timestampMillis in xStartMs..xEndMs }
    }
    // Für Statistik immer sichtbare Sessions nutzen, Fallback auf alle
    val statSessions = if (filteredSessions.isNotEmpty()) filteredSessions else sessions

    var chartSize    by remember { mutableStateOf(IntSize.Zero) }
    var selectedPoint by remember(allPoints) { mutableStateOf<ChartPoint?>(null) }
    var visibility   by remember { mutableStateOf(SeriesVisibility()) }

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
                ChartLegend(
                    hasRange   = visiblePoints.any { it.count > 1 },
                    visibility = visibility,
                    onToggle   = { visibility = it },
                )
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
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val downPos = down.position
                                    var totalMov = 0f

                                    var prev1: Offset? = null
                                    var prev2: Offset? = null

                                    do {
                                        val event   = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break

                                        val cW = chartSize.width - (leftPadDp + rightPadDp + innerPadDp * 2).dp.toPx()
                                        if (cW <= 0f) { prev1 = null; prev2 = null; break }

                                        val xRange  = (xEndMs - xStartMs).toFloat()
                                        val msPerPx = xRange / cW

                                        if (pressed.size == 1) {
                                            val cur = pressed[0].position
                                            totalMov += (cur - (prev1 ?: downPos)).getDistance()
                                            val p   = prev1
                                            if (p != null && totalMov > 15f) {
                                                xStartMs += (-(cur.x - p.x) * msPerPx).toLong()
                                                xEndMs   += (-(cur.x - p.x) * msPerPx).toLong()
                                                clampViewport()
                                            }
                                            prev1 = cur
                                            prev2 = null
                                            pressed[0].consume()
                                        } else if (pressed.size >= 2) {
                                            totalMov = Float.MAX_VALUE  // kein Tap bei Pinch
                                            val cur1 = pressed[0].position
                                            val cur2 = pressed[1].position
                                            val p1   = prev1
                                            val p2   = prev2

                                            if (p1 != null && p2 != null) {
                                                val prevDx = abs(p2.x - p1.x).coerceAtLeast(1f)
                                                val curDx  = abs(cur2.x - cur1.x).coerceAtLeast(1f)
                                                val rawZoom = if (prevDx > 30f && curDx > 30f) prevDx / curDx else 1f
                                                val zoom    = (1f + (rawZoom - 1f) * 0.6f).coerceIn(0.88f, 1.12f)

                                                val cxPx  = (p1.x + p2.x) / 2f - (leftPadDp + innerPadDp).dp.toPx()
                                                val cxMs  = xStartMs + (cxPx * msPerPx).toLong()

                                                val newXRange = (xRange * zoom).coerceAtLeast(oneDayMs.toFloat())
                                                val midDxMs = (-((cur1.x + cur2.x) / 2f - (p1.x + p2.x) / 2f) * msPerPx).toLong()

                                                xStartMs = cxMs - (newXRange / 2).toLong() + midDxMs
                                                xEndMs   = cxMs + (newXRange / 2).toLong() + midDxMs
                                                clampViewport()
                                            }
                                            prev1 = cur1
                                            prev2 = cur2
                                            pressed.forEach { it.consume() }
                                        }
                                    } while (true)

                                    // Tap-Erkennung: wenig Bewegung → nächsten Punkt suchen
                                    if (totalMov < 15f) {
                                        val lp = (leftPadDp + innerPadDp).dp.toPx()
                                        val cW = chartSize.width - (leftPadDp + rightPadDp + innerPadDp * 2).dp.toPx()
                                        if (cW > 0f) {
                                            val fracX   = ((downPos.x - lp) / cW).coerceIn(0f, 1f)
                                            val tappedMs = xStartMs + (fracX * (xEndMs - xStartMs)).toLong()
                                            val nearest  = allPoints.minByOrNull { abs(it.dateMillis - tappedMs) }
                                            selectedPoint = if (nearest == selectedPoint) null else nearest
                                        }
                                    }
                                }
                            },
                    ) {
                        BpLineChart(
                            points        = allPoints,
                            xStartMs      = xStartMs,
                            xEndMs        = xEndMs,
                            selectedPoint = selectedPoint,
                            visibility    = visibility,
                            modifier      = Modifier.fillMaxSize(),
                        )

                        // ── Tooltip-Overlay ───────────────────────────────
                        selectedPoint?.let { pt ->
                            val density = LocalDensity.current
                            val cW = with(density) {
                                (chartSize.width - (leftPadDp + rightPadDp + innerPadDp * 2).dp.toPx())
                            }
                            val lp = with(density) { (leftPadDp + innerPadDp).dp.toPx() }
                            val fracX = if (xEndMs > xStartMs)
                                ((pt.dateMillis - xStartMs).toFloat() / (xEndMs - xStartMs)).coerceIn(0f, 1f)
                            else 0f
                            val pointXPx = lp + cW * fracX
                            val pointXDp = with(density) { pointXPx.toDp() }
                            val tooltipWidthDp = 160.dp
                            val tooltipXDp = (pointXDp - tooltipWidthDp / 2)
                                .coerceIn(0.dp, with(density) { chartSize.width.toDp() } - tooltipWidthDp)

                            val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
                            Box(
                                modifier = Modifier
                                    .offset(x = tooltipXDp, y = 4.dp)
                                    .width(tooltipWidthDp),
                            ) {
                                Card(
                                    shape  = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(
                                            dateFmt.format(Date(pt.dateMillis)),
                                            style      = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (visibility.sys)        TooltipRow("Sys",  "${pt.avgSys.toInt()} mmHg",       BpRed)
                                        if (visibility.dia)        TooltipRow("Dia",  "${pt.avgDia.toInt()} mmHg",       BpBlue)
                                        if (visibility.pulse)      TooltipRow("Puls", "${pt.avgPulse.toInt()} /min",     BpAmber)
                                        if (visibility.pulsedruck) TooltipRow("PP",   "${pt.avgPulsedruck.toInt()} mmHg", BpPurple)
                                        if (pt.count > 1)
                                            Text(
                                                "${pt.count} Messungen",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            )
                                    }
                                }
                            }
                        }
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
private fun ChartLegend(
    hasRange:   Boolean,
    visibility: SeriesVisibility,
    onToggle:   (SeriesVisibility) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendItem(BpRed,    "Systolisch",  visibility.sys)        { onToggle(visibility.copy(sys        = !visibility.sys)) }
            LegendItem(BpBlue,   "Diastolisch", visibility.dia)        { onToggle(visibility.copy(dia        = !visibility.dia)) }
            LegendItem(BpAmber,  "Puls",        visibility.pulse)      { onToggle(visibility.copy(pulse      = !visibility.pulse)) }
            LegendItem(BpPurple, "Pulsdruck",   visibility.pulsedruck) { onToggle(visibility.copy(pulsedruck = !visibility.pulsedruck)) }
        }
        if (hasRange) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendRangeItem(color = BpRed.copy(alpha = 0.25f), label = "Tagesbereich")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier              = Modifier.clickable(onClick = onClick),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            Modifier.size(width = 16.dp, height = 3.dp),
            color = if (active) color else color.copy(alpha = 0.25f),
            shape = RoundedCornerShape(2.dp),
        ) {}
        Text(
            label,
            style          = MaterialTheme.typography.labelSmall,
            color          = if (active) MaterialTheme.colorScheme.onSurfaceVariant
                             else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            textDecoration = if (active) TextDecoration.None else TextDecoration.LineThrough,
        )
    }
}

@Composable
private fun LegendRangeItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(Modifier.size(width = 6.dp, height = 14.dp), color = color, shape = RoundedCornerShape(3.dp)) {}
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Tooltip-Zeile ─────────────────────────────────────────────────────────────

@Composable
private fun TooltipRow(label: String, value: String, color: Color) {
    Row(
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier             = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(28.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

// ── Liniendiagramm (Canvas) mit Viewport ─────────────────────────────────────

@Composable
private fun BpLineChart(
    points:        List<ChartPoint>,
    xStartMs:      Long,
    xEndMs:        Long,
    selectedPoint: ChartPoint? = null,
    visibility:    SeriesVisibility = SeriesVisibility(),
    modifier:      Modifier = Modifier,
) {
    val sysColor          = BpRed
    val diaColor          = BpBlue
    val pulseColor        = BpAmber
    val pulsedruckColor   = BpPurple
    val gridColor         = Color(0xFFDDDDDD)
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

    // Y-Bereich automatisch aus sichtbaren Punkten (Fallback: alle Punkte)
    val displayPoints = if (visible.isNotEmpty()) visible else points
    val yMin: Float
    val yMax: Float
    if (displayPoints.isEmpty()) {
        yMin = 50f; yMax = 200f
    } else {
        val allVals = buildList {
            displayPoints.forEach { pt ->
                if (visibility.sys)        { add(pt.minSys); add(pt.maxSys) }
                if (visibility.dia)        { add(pt.minDia); add(pt.maxDia) }
                if (visibility.pulse)      add(pt.avgPulse)
                if (visibility.pulsedruck) add(pt.avgPulsedruck)
            }
        }
        if (allVals.isEmpty()) { yMin = 50f; yMax = 200f }
        else {
            yMin = ((allVals.min() - 10f) / 10).toInt() * 10f
            yMax = (((allVals.max() + 10f) / 10).toInt() + 1) * 10f
        }
    }

    Canvas(modifier = modifier) {
        val leftPad   = 44.dp.toPx()
        val rightPad  = 10.dp.toPx()
        val topPad    = 10.dp.toPx()
        val bottomPad = 32.dp.toPx()
        val cW = size.width  - leftPad - rightPad
        val cH = size.height - topPad  - bottomPad

        val safeYMin = yMin
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
            // Mindestabstand zwischen zwei Labels: gemessene Textbreite + 60 % Puffer
            val minLabelGap = xPaint.measureText("00.00") * 1.6f
            var lastLabelX  = leftPad - minLabelGap  // ersten Punkt immer erlauben
            visible.forEach { pt ->
                val xp = xPx(pt.dateMillis)
                if (xp in leftPad..(leftPad + cW) && xp - lastLabelX >= minLabelGap) {
                    drawLine(gridColor, Offset(xp, topPad), Offset(xp, topPad + cH + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(labelFmt.format(Date(pt.dateMillis)), xp, size.height - 6.dp.toPx(), xPaint)
                    lastLabelX = xp
                }
            }
        }

        // Range-Balken
        val barWidth = 6.dp.toPx()
        visible.filter { it.count > 1 }.forEach { pt ->
            val xp = xPx(pt.dateMillis)
            if (visibility.sys) drawLine(sysRangeColor, Offset(xp, yPx(pt.maxSys)), Offset(xp, yPx(pt.minSys)), strokeWidth = barWidth, cap = StrokeCap.Round)
            if (visibility.dia) drawLine(diaRangeColor, Offset(xp, yPx(pt.maxDia)), Offset(xp, yPx(pt.minDia)), strokeWidth = barWidth * 0.7f, cap = StrokeCap.Round)
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

        if (visibility.sys)        plotSeries(visible, { it.avgSys        }, sysColor)
        if (visibility.dia)        plotSeries(visible, { it.avgDia        }, diaColor)
        if (visibility.pulse)      plotSeries(visible, { it.avgPulse      }, pulseColor)
        if (visibility.pulsedruck) plotSeries(visible, { it.avgPulsedruck }, pulsedruckColor)

        // Ausgewählten Punkt hervorheben
        selectedPoint?.let { pt ->
            val xp = xPx(pt.dateMillis)
            if (xp in (leftPad - 12.dp.toPx())..(leftPad + cW + 12.dp.toPx())) {
                listOf(
                    Triple(pt.avgSys,        sysColor,        visibility.sys),
                    Triple(pt.avgDia,        diaColor,        visibility.dia),
                    Triple(pt.avgPulse,      pulseColor,      visibility.pulse),
                    Triple(pt.avgPulsedruck, pulsedruckColor, visibility.pulsedruck),
                ).filter { it.third }.forEach { (v, col, _) ->
                    val yp = yPx(v)
                    drawCircle(col.copy(alpha = 0.25f), 10.dp.toPx(), Offset(xp, yp))
                    drawCircle(col, 5.dp.toPx(), Offset(xp, yp))
                    drawCircle(Color.White, 2.5.dp.toPx(), Offset(xp, yp))
                }
                // Senkrechte Linie
                drawLine(Color(0x66000000), Offset(xp, topPad), Offset(xp, topPad + cH), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f)))
            }
        }
    }
}

// ── Statistik-Kacheln ─────────────────────────────────────────────────────────

@Composable
private fun ChartStats(sessions: List<MeasurementSession>) {
    val allSys        = sessions.map { it.avgSys }
    val allDia        = sessions.map { it.avgDia }
    val allPulse      = sessions.map { it.avgPulse }
    val allPulsedruck = sessions.map { it.avgPulsedruck }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatRowCard("Systolisch",  BpRed,    allSys.min(),        allSys.average().toFloat(),        allSys.max(),        "mmHg")
        StatRowCard("Diastolisch", BpBlue,   allDia.min(),        allDia.average().toFloat(),        allDia.max(),        "mmHg")
        StatRowCard("Puls",        BpAmber,  allPulse.min(),      allPulse.average().toFloat(),      allPulse.max(),      "/min")
        StatRowCard("Pulsdruck",   BpPurple, allPulsedruck.min(), allPulsedruck.average().toFloat(), allPulsedruck.max(), "mmHg")
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
