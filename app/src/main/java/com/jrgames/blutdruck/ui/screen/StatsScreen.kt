package com.jrgames.blutdruck.ui.screen

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrgames.blutdruck.data.local.MeasurementSession
import com.jrgames.blutdruck.ui.theme.BpBlue
import com.jrgames.blutdruck.ui.theme.BpRed
import java.util.Calendar

// ── Hilfs-Datenklassen ────────────────────────────────────────────────────────

private data class BoxStats(
    val label:  String,
    val min:    Float,
    val q1:     Float,
    val median: Float,
    val q3:     Float,
    val max:    Float,
    val mean:   Float,
    val n:      Int,
)

// ── Hilfs-Funktionen ──────────────────────────────────────────────────────────

private fun percentile(sorted: List<Float>, p: Float): Float {
    if (sorted.isEmpty()) return 0f
    val idx = p * (sorted.size - 1)
    val lo  = sorted[idx.toInt()]
    val hi  = sorted[(idx.toInt() + 1).coerceAtMost(sorted.size - 1)]
    return lo + (idx - idx.toInt()) * (hi - lo)
}

private fun boxStats(label: String, values: List<Float>): BoxStats? {
    if (values.size < 2) return null
    val s = values.sorted()
    return BoxStats(
        label  = label,
        min    = s.first(),
        q1     = percentile(s, 0.25f),
        median = percentile(s, 0.50f),
        q3     = percentile(s, 0.75f),
        max    = s.last(),
        mean   = values.average().toFloat(),
        n      = values.size,
    )
}

private fun hourOfDay(ms: Long): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = ms
    return cal.get(Calendar.HOUR_OF_DAY)
}

private fun dayOfWeek(ms: Long): Int {          // 2=Mo … 7=Sa, 1=So → wir mappen auf 0–6 (Mo=0)
    val cal = Calendar.getInstance()
    cal.timeInMillis = ms
    return (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7  // Mo=0 … So=6
}

private val DAY_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private val TIME_LABELS = listOf("Nachts\n0–6 h", "Morgens\n6–12 h", "Mittags\n12–18 h", "Abends\n18–24 h")

private fun timeSlot(hour: Int) = when (hour) {
    in 0..5   -> 0  // Nacht
    in 6..11  -> 1  // Morgens
    in 12..17 -> 2  // Mittags
    else      -> 3  // Abends
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    sessions: List<MeasurementSession>,
    onBack:   () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiken") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Zurück") }
                },
            )
        },
    ) { padding ->
        if (sessions.size < 3) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Mindestens 3 Messungen für Statistiken nötig.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── 1. Verteilung: Histogramm ──────────────────────────────────
            SectionHeader("Verteilung")
            Text(
                "Dunkel = Linker Arm  ·  Hell = Rechter Arm  ·  Linie = Mittelwert",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val linksS  = sessions.filter { it.arm == "LINKS" }
            val rechtsS = sessions.filter { it.arm == "RECHTS" }

            ChartCard(title = "Systolisch (mmHg)", height = 180.dp) {
                HistogramChart(
                    series1  = linksS.map { it.avgSys },
                    color1   = ArmLinksColor,
                    series2  = rechtsS.map { it.avgSys },
                    color2   = ArmRechtsColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ChartCard(title = "Diastolisch (mmHg)", height = 180.dp) {
                HistogramChart(
                    series1  = linksS.map { it.avgDia },
                    color1   = ArmLinksDiaColor,
                    series2  = rechtsS.map { it.avgDia },
                    color2   = ArmRechtsDiaColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ── 2. BoxPlot: Tageszeit ─────────────────────────────────────
            SectionHeader("Blutdruck nach Tageszeit")
            Text(
                "Rot = Sys  ·  Blau = Dia",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val byTime = (0..3).map { slot ->
                val s = sessions.filter { timeSlot(hourOfDay(it.timestampMillis)) == slot }
                val sysBox   = boxStats(TIME_LABELS[slot], s.map { it.avgSys })
                val diaBox   = boxStats(TIME_LABELS[slot], s.map { it.avgDia })
                sysBox to diaBox
            }
            ChartCard(title = "", height = 260.dp) {
                BoxPlotChart(
                    groups   = byTime,
                    labels   = TIME_LABELS,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ── 3. BoxPlot: Wochentag ─────────────────────────────────────
            SectionHeader("Blutdruck nach Wochentag")
            Text(
                "Rot = Sys  ·  Blau = Dia",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val byDay = (0..6).map { dow ->
                val s = sessions.filter { dayOfWeek(it.timestampMillis) == dow }
                val sysBox = boxStats(DAY_LABELS[dow], s.map { it.avgSys })
                val diaBox = boxStats(DAY_LABELS[dow], s.map { it.avgDia })
                sysBox to diaBox
            }
            ChartCard(title = "", height = 340.dp) {
                BoxPlotChart(
                    groups   = byDay,
                    labels   = DAY_LABELS,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Hilfs-Composables ────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style      = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ChartCard(title: String, height: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            if (title.isNotBlank()) {
                Text(title, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            Box(Modifier.fillMaxWidth().height(height)) {
                content()
            }
        }
    }
}

// ── Histogramm ────────────────────────────────────────────────────────────────

private val ArmLinksColor    = Color(0xFF1565C0)   // dunkelblau  (Sys Links)
private val ArmRechtsColor   = Color(0xFF90CAF9)   // hellblau    (Sys Rechts)
private val ArmLinksDiaColor = Color(0xFFB71C1C)   // dunkelrot   (Dia Links)
private val ArmRechtsDiaColor= Color(0xFFEF9A9A)   // hellrot     (Dia Rechts)

@Composable
private fun HistogramChart(
    series1:  List<Float>,
    color1:   Color,
    series2:  List<Float>,
    color2:   Color,
    modifier: Modifier,
) {
    if (series1.isEmpty() && series2.isEmpty()) return

    Canvas(modifier = modifier) {
        val lp = 32.dp.toPx(); val rp = 8.dp.toPx()
        val tp = 10.dp.toPx(); val bp = 22.dp.toPx()
        val cW = size.width - lp - rp
        val cH = size.height - tp - bp

        val binWidth = 5f
        val allVals  = series1 + series2
        val dataMin  = (allVals.min() / binWidth).toInt() * binWidth
        val dataMax  = ((allVals.max() / binWidth).toInt() + 1) * binWidth
        val binCount = ((dataMax - dataMin) / binWidth).toInt().coerceAtLeast(1)

        fun binIdx(v: Float) = ((v - dataMin) / binWidth).toInt().coerceIn(0, binCount - 1)

        val counts1  = IntArray(binCount).also { arr -> series1.forEach { arr[binIdx(it)]++ } }
        val counts2  = IntArray(binCount).also { arr -> series2.forEach { arr[binIdx(it)]++ } }
        val maxCount = (counts1.max().coerceAtLeast(counts2.max())).coerceAtLeast(1)

        fun xPx(v: Float)  = lp + cW * (v - dataMin) / (dataMax - dataMin)
        fun barTop(n: Int) = tp + cH * (1f - n.toFloat() / maxCount)

        // Y-Gitter
        val gridColor = Color(0xFFE0E0E0)
        val yStep = when {
            maxCount <= 5  -> 1
            maxCount <= 15 -> 2
            maxCount <= 30 -> 5
            else           -> 10
        }
        val yPaint = Paint().apply { textSize = 9.sp.toPx(); color = android.graphics.Color.GRAY; textAlign = Paint.Align.RIGHT }
        var yv = 0
        while (yv <= maxCount) {
            val yp = tp + cH * (1f - yv.toFloat() / maxCount)
            drawLine(gridColor, Offset(lp, yp), Offset(lp + cW, yp), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("$yv", lp - 3.dp.toPx(), yp + 3.dp.toPx(), yPaint)
            yv += yStep
        }

        // Baseline
        drawLine(Color(0xFFAAAAAA), Offset(lp, tp + cH), Offset(lp + cW, tp + cH), strokeWidth = 1.dp.toPx())

        // Balken
        val slotW = cW / binCount
        val gap   = (slotW * 0.06f).coerceAtLeast(1.dp.toPx())
        val barW  = ((slotW - gap * 3f) / 2f).coerceAtLeast(1.dp.toPx())

        for (i in 0 until binCount) {
            val slotLeft = lp + slotW * i

            if (counts1[i] > 0) {
                val h = cH - (barTop(counts1[i]) - tp)
                drawRect(color1, Offset(slotLeft + gap, barTop(counts1[i])), Size(barW, h))
            }
            if (counts2[i] > 0) {
                val h = cH - (barTop(counts2[i]) - tp)
                drawRect(color2, Offset(slotLeft + gap * 2f + barW, barTop(counts2[i])), Size(barW, h))
            }
        }

        // Mittelwert-Linien
        val meanLabelPaint = Paint().apply { textSize = 9.sp.toPx(); isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val meanBgPaint    = Paint().apply { color = android.graphics.Color.argb(210, 255, 255, 255) }
        listOf(series1 to color1, series2 to color2).forEachIndexed { idx, (series, color) ->
            if (series.isEmpty()) return@forEachIndexed
            val mean  = series.average().toFloat()
            val meanX = xPx(mean).coerceIn(lp + 1f, lp + cW - 1f)
            drawLine(color, Offset(meanX, tp), Offset(meanX, tp + cH), strokeWidth = 1.5.dp.toPx())
            meanLabelPaint.color = color.toArgb()
            val text  = "∅${mean.toInt()}"
            val textW = meanLabelPaint.measureText(text)
            val textH = meanLabelPaint.textSize
            val lx    = meanX.coerceIn(lp + textW / 2 + 2.dp.toPx(), lp + cW - textW / 2 - 2.dp.toPx())
            val ly    = tp + textH + idx * (textH + 2.dp.toPx())
            drawContext.canvas.nativeCanvas.drawRoundRect(
                android.graphics.RectF(lx - textW / 2 - 2.dp.toPx(), ly - textH, lx + textW / 2 + 2.dp.toPx(), ly + 2.dp.toPx()),
                2.dp.toPx(), 2.dp.toPx(), meanBgPaint)
            drawContext.canvas.nativeCanvas.drawText(text, lx, ly, meanLabelPaint)
        }

        // X-Achse
        val xPaint = Paint().apply { textSize = 9.sp.toPx(); color = android.graphics.Color.GRAY; textAlign = Paint.Align.CENTER }
        val xStep  = if (binCount <= 12) 1 else 2
        for (i in 0..binCount step xStep) {
            val xp = xPx(dataMin + i * binWidth)
            drawContext.canvas.nativeCanvas.drawText("${(dataMin + i * binWidth).toInt()}", xp, size.height - 3.dp.toPx(), xPaint)
        }
    }
}

// ── BoxPlot-Diagramm (horizontal) ─────────────────────────────────────────────

@Composable
private fun BoxPlotChart(
    groups:   List<Pair<BoxStats?, BoxStats?>>,
    labels:   List<String>,
    modifier: Modifier,
) {
    var selectedIdx by remember { mutableIntStateOf(-1) }
    var canvasSize  by remember { mutableStateOf(IntSize.Zero) }

    // Padding-Konstanten (müssen mit Canvas übereinstimmen)
    val lpDp = 60f; val rpDp = 12f; val tpDp = 10f; val bpDp = 20f

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(groups.size) {
                    detectTapGestures { offset ->
                        val tpPx = tpDp.dp.toPx()
                        val bpPx = bpDp.dp.toPx()
                        val cH   = canvasSize.height - tpPx - bpPx
                        val rowH = if (groups.isNotEmpty()) cH / groups.size else 1f
                        val relY = offset.y - tpPx
                        val idx  = (relY / rowH).toInt().coerceIn(0, groups.size - 1)
                        selectedIdx = if (selectedIdx == idx) -1 else idx
                    }
                },
        ) {
            val lp = lpDp.dp.toPx(); val rp = rpDp.dp.toPx()
            val tp = tpDp.dp.toPx(); val bp = bpDp.dp.toPx()
            val cW = size.width - lp - rp
            val cH = size.height - tp - bp

            val allVals = groups.flatMap { (s, d) ->
                listOf(s?.min, s?.max, d?.min, d?.max).filterNotNull()
            }
            if (allVals.isEmpty()) return@Canvas
            val xMin   = (allVals.min() - 5f).let { (it / 10).toInt() * 10f }
            val xMax   = (allVals.max() + 5f).let { ((it / 10).toInt() + 1) * 10f }
            val xRange = (xMax - xMin).coerceAtLeast(1f)

            fun xPx(v: Float) = lp + cW * (v - xMin) / xRange

            val gridColor  = Color(0xFFE0E0E0)
            val gridPaint  = Paint().apply { textSize = 9.sp.toPx(); color = android.graphics.Color.GRAY; textAlign = Paint.Align.CENTER }
            val labelPaint = Paint().apply { textSize = 9.sp.toPx(); color = android.graphics.Color.DKGRAY; textAlign = Paint.Align.RIGHT }

            // X-Gitter
            val xStep = (xRange / 5).let { s -> listOf(5f, 10f, 20f).firstOrNull { it >= s } ?: 20f }
            var xv = (xMin / xStep).toInt() * xStep
            while (xv <= xMax) {
                val xp = xPx(xv)
                drawLine(gridColor, Offset(xp, tp), Offset(xp, tp + cH), strokeWidth = 1.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText("${xv.toInt()}", xp, size.height - 4.dp.toPx(), gridPaint)
                xv += xStep
            }

            val rowH = cH / groups.size
            val boxH = (rowH * 0.28f).coerceAtLeast(6.dp.toPx())
            val gap  = boxH * 0.3f

            groups.forEachIndexed { i, (sysBox, diaBox) ->
                val rowCenter = tp + rowH * (i + 0.5f)

                // Hintergrund-Highlight für ausgewählte Zeile
                if (i == selectedIdx) {
                    drawRect(
                        color   = Color(0x11000000),
                        topLeft = Offset(lp, tp + rowH * i),
                        size    = Size(cW, rowH),
                    )
                }

                // Y-Label (mehrzeilig)
                val rawLabel  = labels.getOrElse(i) { "" }
                val lines     = rawLabel.split("\n")
                val lineH     = 10.sp.toPx()
                val yLabelTop = rowCenter - lineH * (lines.size - 1) / 2f
                lines.forEachIndexed { li, line ->
                    drawContext.canvas.nativeCanvas.drawText(
                        line, lp - 4.dp.toPx(), yLabelTop + li * lineH + lineH / 2f, labelPaint)
                }

                // Sys (oben) + Dia (unten)
                for (bi in 0..1) {
                    val (box, color) = if (bi == 0) (sysBox to BpRed) else (diaBox to BpBlue)
                    if (box == null) continue
                    val cy = rowCenter + (bi * 2 - 1) * (boxH / 2f + gap / 2f)

                    drawLine(color, Offset(xPx(box.min), cy), Offset(xPx(box.q1), cy), strokeWidth = 1.5.dp.toPx())
                    drawLine(color, Offset(xPx(box.min), cy - boxH * 0.3f), Offset(xPx(box.min), cy + boxH * 0.3f), strokeWidth = 1.5.dp.toPx())
                    val boxL = xPx(box.q1); val boxR = xPx(box.q3)
                    drawRect(color.copy(alpha = 0.20f), Offset(boxL, cy - boxH / 2f), Size(boxR - boxL, boxH))
                    drawRect(color, Offset(boxL, cy - boxH / 2f), Size(boxR - boxL, boxH), style = Stroke(1.5.dp.toPx()))
                    val medX = xPx(box.median)
                    drawLine(color, Offset(medX, cy - boxH / 2f), Offset(medX, cy + boxH / 2f), strokeWidth = 2.5.dp.toPx())
                    drawCircle(color, 3.dp.toPx(), Offset(xPx(box.mean), cy))
                    drawLine(color, Offset(xPx(box.q3), cy), Offset(xPx(box.max), cy), strokeWidth = 1.5.dp.toPx())
                    drawLine(color, Offset(xPx(box.max), cy - boxH * 0.3f), Offset(xPx(box.max), cy + boxH * 0.3f), strokeWidth = 1.5.dp.toPx())
                }
            }
        }

        // ── Tooltip-Overlay ───────────────────────────────────────────────────
        if (selectedIdx >= 0 && selectedIdx < groups.size) {
            val (sysBox, diaBox) = groups[selectedIdx]
            val label = labels.getOrElse(selectedIdx) { "" }.replace("\n", " ")
            if (sysBox != null || diaBox != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .fillMaxWidth(0.92f),
                ) {
                    Card(
                        shape     = RoundedCornerShape(12.dp),
                        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                label,
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSurface,
                            )
                            // Header
                            Row(Modifier.fillMaxWidth()) {
                                Text("", Modifier.width(36.dp))
                                listOf("Min", "Q1", "Median", "Ø", "Q3", "Max", "n").forEach { h ->
                                    Text(h, Modifier.weight(1f),
                                        style     = MaterialTheme.typography.labelSmall,
                                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines  = 1,
                                    )
                                }
                            }
                            sysBox?.let { BoxTooltipRow("Sys", it, BpRed) }
                            diaBox?.let { BoxTooltipRow("Dia", it, BpBlue) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxTooltipRow(label: String, box: BoxStats, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(36.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        listOf(box.min, box.q1, box.median, box.mean, box.q3, box.max).forEach { v ->
            Text("%.0f".format(v), Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = color)
        }
        Text("${box.n}", Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.65f))
    }
}





