package com.jrgames.blutdruck.ui.screen

import android.graphics.Paint
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrgames.blutdruck.data.local.MeasurementSession
import com.jrgames.blutdruck.ui.theme.BpBlue
import com.jrgames.blutdruck.ui.theme.BpRed
import java.util.Calendar
import kotlin.math.*

// ── Hilfs-Datenklassen ────────────────────────────────────────────────────────

private data class Distribution(
    val label:  String,
    val mean:   Float,
    val stdDev: Float,
    val color:  Color,
    val n:      Int,
)

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

private fun gaussianPdf(x: Float, mean: Float, std: Float): Float {
    if (std <= 0f) return 0f
    val z = (x - mean) / std
    return (1f / (std * sqrt(2f * PI.toFloat()))) * exp(-0.5f * z * z)
}

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

private fun stdDev(values: List<Float>): Float {
    if (values.size < 2) return 1f
    val m = values.average().toFloat()
    return sqrt(values.map { (it - m).pow(2) }.average().toFloat()).coerceAtLeast(0.1f)
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

            // ── 1. Verteilung: Gaußkurven ──────────────────────────────────
            SectionHeader("Verteilung (Gauß-Kurve)")
            Text(
                "Blau = Linker Arm  ·  Rot = Rechter Arm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val links  = sessions.filter { it.arm == "LINKS" }
            val rechts = sessions.filter { it.arm == "RECHTS" }

            listOf(
                Triple("Systolisch (mmHg)",   sessions.map { it.avgSys },   links.map { it.avgSys }   to rechts.map { it.avgSys }),
                Triple("Diastolisch (mmHg)",  sessions.map { it.avgDia },   links.map { it.avgDia }   to rechts.map { it.avgDia }),
                Triple("Herzfrequenz (/min)", sessions.map { it.avgPulse }, links.map { it.avgPulse } to rechts.map { it.avgPulse }),
            ).forEach { (title, all, armPair) ->
                val (lVals, rVals) = armPair
                val dists = buildList {
                    if (lVals.size >= 2) add(Distribution("Links",  lVals.average().toFloat(), stdDev(lVals), BpBlue, lVals.size))
                    if (rVals.size >= 2) add(Distribution("Rechts", rVals.average().toFloat(), stdDev(rVals), BpRed,  rVals.size))
                    if (isEmpty() && all.size >= 2) add(Distribution("Gesamt", all.average().toFloat(), stdDev(all), BpBlue, all.size))
                }
                if (dists.isNotEmpty()) {
                    ChartCard(title = title, height = 180.dp) {
                        GaussianChart(distributions = dists, modifier = Modifier.fillMaxSize())
                    }
                }
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

// ── Gauß-Diagramm ─────────────────────────────────────────────────────────────

@Composable
private fun GaussianChart(distributions: List<Distribution>, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val lp = 42.dp.toPx(); val rp = 10.dp.toPx()
        val tp = 10.dp.toPx(); val bp = 28.dp.toPx()
        val cW = size.width - lp - rp
        val cH = size.height - tp - bp

        // X-Bereich: über alle Verteilungen μ ± 3σ
        val xMin = distributions.minOf { it.mean - 3.5f * it.stdDev }
        val xMax = distributions.maxOf { it.mean + 3.5f * it.stdDev }
        val xRange = (xMax - xMin).coerceAtLeast(1f)

        // Maximale Dichte für Y-Skalierung
        val steps = 300
        val yMax = distributions.maxOf { d ->
            gaussianPdf(d.mean, d.mean, d.stdDev)
        } * 1.15f

        fun xPx(v: Float) = lp + cW * (v - xMin) / xRange
        fun yPx(v: Float) = tp + cH * (1f - v / yMax)

        // Gitter
        val gridColor = Color(0xFFDDDDDD)
        val xPaint = Paint().apply { textSize = 9.sp.toPx(); color = android.graphics.Color.GRAY; textAlign = Paint.Align.CENTER }
        val xStep = (xRange / 5).let { s -> listOf(1f,2f,5f,10f,20f).firstOrNull { it >= s } ?: 20f }
        var xv = (xMin / xStep).toInt() * xStep
        while (xv <= xMax) {
            val xp = xPx(xv)
            drawLine(gridColor, Offset(xp, tp), Offset(xp, tp + cH), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("${xv.toInt()}", xp, size.height - 4.dp.toPx(), xPaint)
            xv += xStep
        }

        // Kurven zeichnen
        distributions.forEach { dist ->
            val fillColor = dist.color.copy(alpha = 0.18f)
            val lineColor = dist.color

            val fillPath = Path()
            var first = true
            for (i in 0..steps) {
                val x = xMin + xRange * i / steps
                val y = gaussianPdf(x, dist.mean, dist.stdDev)
                val px = xPx(x); val py = yPx(y)
                if (first) { fillPath.moveTo(px, tp + cH); fillPath.lineTo(px, py); first = false }
                else fillPath.lineTo(px, py)
            }
            fillPath.lineTo(xPx(xMax), tp + cH)
            fillPath.close()
            drawPath(fillPath, fillColor)

            val linePath = Path()
            first = true
            for (i in 0..steps) {
                val x = xMin + xRange * i / steps
                val y = gaussianPdf(x, dist.mean, dist.stdDev)
                val px = xPx(x); val py = yPx(y)
                if (first) { linePath.moveTo(px, py); first = false } else linePath.lineTo(px, py)
            }
            drawPath(linePath, lineColor, style = Stroke(width = 2.dp.toPx()))

            // Mittellinie
            val peakY = yPx(gaussianPdf(dist.mean, dist.mean, dist.stdDev))
            drawLine(lineColor.copy(alpha = 0.6f),
                Offset(xPx(dist.mean), tp + cH),
                Offset(xPx(dist.mean), peakY),
                strokeWidth = 1.5.dp.toPx())

            // μ-Label an der Kurvenspitze mit Hintergrund
            val lblText = "μ=${dist.mean.toInt()}  n=${dist.n}"
            val lbl = Paint().apply {
                textSize  = 10.sp.toPx()
                color     = lineColor.toArgb()
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val textW  = lbl.measureText(lblText)
            val textH  = lbl.textSize
            val lblX   = xPx(dist.mean).coerceIn(lp + textW / 2 + 4.dp.toPx(), lp + cW - textW / 2 - 4.dp.toPx())
            val lblY   = (peakY - 6.dp.toPx()).coerceAtLeast(tp + textH)
            val bgPaint = Paint().apply { color = android.graphics.Color.argb(210, 255, 255, 255) }
            drawContext.canvas.nativeCanvas.drawRoundRect(
                android.graphics.RectF(lblX - textW / 2 - 4.dp.toPx(), lblY - textH,
                    lblX + textW / 2 + 4.dp.toPx(), lblY + 3.dp.toPx()),
                4.dp.toPx(), 4.dp.toPx(), bgPaint)
            drawContext.canvas.nativeCanvas.drawText(lblText, lblX, lblY, lbl)
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
    Canvas(modifier = modifier) {
        val lp = 60.dp.toPx(); val rp = 12.dp.toPx()
        val tp = 10.dp.toPx(); val bp = 20.dp.toPx()
        val cW = size.width - lp - rp
        val cH = size.height - tp - bp

        val allVals = groups.flatMap { (s, d) ->
            listOf(s?.min, s?.max, d?.min, d?.max).filterNotNull()
        }
        if (allVals.isEmpty()) return@Canvas
        val xMin = (allVals.min() - 5f).let { (it / 10).toInt() * 10f }
        val xMax = (allVals.max() + 5f).let { ((it / 10).toInt() + 1) * 10f }
        val xRange = (xMax - xMin).coerceAtLeast(1f)

        fun xPx(v: Float) = lp + cW * (v - xMin) / xRange

        val gridColor  = Color(0xFFE0E0E0)
        val gridPaint  = Paint().apply { textSize = 9.sp.toPx(); color = android.graphics.Color.GRAY; textAlign = Paint.Align.CENTER }
        val labelPaint = Paint().apply { textSize = 9.sp.toPx(); color = android.graphics.Color.DKGRAY; textAlign = Paint.Align.RIGHT }

        // X-Gitter
        val xStep = (xRange / 5).let { s -> listOf(5f,10f,20f).firstOrNull { it >= s } ?: 20f }
        var xv = (xMin / xStep).toInt() * xStep
        while (xv <= xMax) {
            val xp = xPx(xv)
            drawLine(gridColor, Offset(xp, tp), Offset(xp, tp + cH), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("${xv.toInt()}", xp, size.height - 4.dp.toPx(), gridPaint)
            xv += xStep
        }

        val rowH  = cH / groups.size
        val boxH  = (rowH * 0.28f).coerceAtLeast(6.dp.toPx())
        val gap   = boxH * 0.3f

        groups.forEachIndexed { i, (sysBox, diaBox) ->
            val rowCenter = tp + rowH * (i + 0.5f)

            // Y-Label (mehrzeilig unterstützen wir mit vereinfachtem Zeilenumbruch)
            val rawLabel = labels.getOrElse(i) { "" }
            val lines = rawLabel.split("\n")
            val lineH = 10.sp.toPx()
            val yLabelTop = rowCenter - lineH * (lines.size - 1) / 2f
            lines.forEachIndexed { li, line ->
                drawContext.canvas.nativeCanvas.drawText(line, lp - 4.dp.toPx(), yLabelTop + li * lineH + lineH / 2f, labelPaint)
            }

            // Sys (oben) + Dia (unten)
            for (bi in 0..1) {
                val (box, color) = if (bi == 0) (sysBox to BpRed) else (diaBox to BpBlue)
                if (box == null) continue
                val cy = rowCenter + (bi * 2 - 1) * (boxH / 2f + gap / 2f)

                // Whisker links
                drawLine(color, Offset(xPx(box.min), cy), Offset(xPx(box.q1), cy), strokeWidth = 1.5.dp.toPx())
                drawLine(color, Offset(xPx(box.min), cy - boxH * 0.3f), Offset(xPx(box.min), cy + boxH * 0.3f), strokeWidth = 1.5.dp.toPx())
                // Box
                val boxL = xPx(box.q1); val boxR = xPx(box.q3)
                drawRect(color.copy(alpha = 0.20f), Offset(boxL, cy - boxH / 2f), Size(boxR - boxL, boxH))
                drawRect(color, Offset(boxL, cy - boxH / 2f), Size(boxR - boxL, boxH), style = Stroke(1.5.dp.toPx()))
                // Median
                val medX = xPx(box.median)
                drawLine(color, Offset(medX, cy - boxH / 2f), Offset(medX, cy + boxH / 2f), strokeWidth = 2.5.dp.toPx())
                // Mean (Kreuz)
                val meanX = xPx(box.mean)
                drawCircle(color, 3.dp.toPx(), Offset(meanX, cy))
                // Whisker rechts
                drawLine(color, Offset(xPx(box.q3), cy), Offset(xPx(box.max), cy), strokeWidth = 1.5.dp.toPx())
                drawLine(color, Offset(xPx(box.max), cy - boxH * 0.3f), Offset(xPx(box.max), cy + boxH * 0.3f), strokeWidth = 1.5.dp.toPx())
            }
        }
    }
}





