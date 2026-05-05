package com.jrgames.blutdruck.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrgames.blutdruck.data.local.MeasurementSession
import com.jrgames.blutdruck.domain.BpClassifier
import com.jrgames.blutdruck.ui.theme.BpBlue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Zeitraum-Modell (geteilt mit ChartScreen) ────────────────────────────────

enum class HistoryPeriod(val label: String) {
    WEEK("Woche"), MONTH("Monat"), ALL("Alle")
}

fun historyPeriodRange(period: HistoryPeriod, offset: Int): Pair<Long, Long>? {
    if (period == HistoryPeriod.ALL) return null
    val cal = Calendar.getInstance()
    return when (period) {
        HistoryPeriod.WEEK -> {
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.WEEK_OF_YEAR, offset)
            val from = cal.timeInMillis
            cal.add(Calendar.WEEK_OF_YEAR, 1)
            from to cal.timeInMillis
        }
        HistoryPeriod.MONTH -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.MONTH, offset)
            val from = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            from to cal.timeInMillis
        }
        HistoryPeriod.ALL -> null
    }
}

fun historyPeriodLabel(period: HistoryPeriod, offset: Int): String {
    if (period == HistoryPeriod.ALL) return "Alle Messungen"
    if (offset == 0) return when (period) {
        HistoryPeriod.WEEK  -> "Diese Woche"
        HistoryPeriod.MONTH -> "Dieser Monat"
        else -> ""
    }
    val range = historyPeriodRange(period, offset) ?: return ""
    val cal = Calendar.getInstance().apply { timeInMillis = range.first }
    return when (period) {
        HistoryPeriod.WEEK  -> {
            val df  = SimpleDateFormat("dd.MM.", Locale.getDefault())
            val end = Calendar.getInstance().apply { timeInMillis = range.first; add(Calendar.DAY_OF_YEAR, 6) }
            "KW${cal.get(Calendar.WEEK_OF_YEAR)} (${df.format(cal.time)}–${df.format(end.time)})"
        }
        HistoryPeriod.MONTH -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
        else -> ""
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sessions: List<MeasurementSession>,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var period    by remember { mutableStateOf(HistoryPeriod.WEEK) }
    var offset    by remember { mutableIntStateOf(0) }
    var tableMode by remember { mutableStateOf(false) }
    LaunchedEffect(period) { offset = 0 }

    val filtered = remember(sessions, period, offset) {
        val range = historyPeriodRange(period, offset)
        if (range == null) sessions
        else sessions.filter { it.timestampMillis in range.first until range.second }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verlauf") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Zurück") }
                },
                actions = {
                    IconButton(onClick = { tableMode = !tableMode }) {
                        Icon(
                            if (tableMode) Icons.Default.ViewAgenda else Icons.Default.TableRows,
                            contentDescription = if (tableMode) "Detailansicht" else "Tabellenansicht",
                        )
                    }
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
                    IconButton(
                        onClick  = { if (offset < 0) offset++ },
                        enabled  = offset < 0,
                    ) {
                        Icon(Icons.Default.ChevronRight, "Vorwärts")
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
            }

            HorizontalDivider()
            Spacer(Modifier.height(4.dp))

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Keine Messungen in diesem Zeitraum.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            } else if (tableMode) {
                CompactTable(sessions = filtered, onDelete = onDelete)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding      = PaddingValues(vertical = 8.dp),
                ) {
                    items(filtered, key = { it.id }) { session ->
                        SessionCard(session = session, onDelete = { onDelete(session.id) })
                    }
                }
            }
        }
    }
}

// ── Kompakte Tabellenansicht ─────────────────────────────────────────────────

@Composable
private fun CompactTable(
    sessions: List<MeasurementSession>,
    onDelete: (Long) -> Unit,
) {
    val dateFmt  = SimpleDateFormat("dd.MM.yy  HH:mm", Locale.getDefault())
    var detailSession by remember { mutableStateOf<MeasurementSession?>(null) }

    detailSession?.let { s ->
        SessionDetailDialog(session = s, onDismiss = { detailSession = null }, onDelete = {
            onDelete(s.id); detailSession = null
        })
    }

    // Tabellen-Header
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text("Datum / Zeit", Modifier.weight(2.2f), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Text("Sys", Modifier.weight(1f), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            Text("Dia", Modifier.weight(1f), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            Text("Puls", Modifier.weight(1f), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            Text("Kat.", Modifier.weight(1f), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(2.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding      = PaddingValues(bottom = 16.dp),
        ) {
            items(sessions, key = { it.id }) { s ->
                val cat     = BpClassifier.classify(s.avgSys, s.avgDia)
                val bgColor = BpClassifier.cardColor(cat)
                val txColor = BpClassifier.textColor(cat)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor, RoundedCornerShape(8.dp))
                        .clickable { detailSession = s }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dateFmt.format(Date(s.timestampMillis)),
                        Modifier.weight(2.2f), fontSize = 12.sp, color = txColor)
                    Text("${s.avgSys.toInt()}", Modifier.weight(1f),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = txColor, textAlign = TextAlign.Center)
                    Text("${s.avgDia.toInt()}", Modifier.weight(1f),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = txColor, textAlign = TextAlign.Center)
                    Text("${s.avgPulse.toInt()}", Modifier.weight(1f),
                        fontSize = 12.sp, color = txColor, textAlign = TextAlign.Center)
                    Surface(color = txColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f)) {
                        Text(cat.shortLabel,
                            Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            fontSize = 10.sp, color = txColor,
                            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// ── Detail-Popup ─────────────────────────────────────────────────────────────

@Composable
private fun SessionDetailDialog(
    session:   MeasurementSession,
    onDismiss: () -> Unit,
    onDelete:  () -> Unit,
) {
    val cat     = BpClassifier.classify(session.avgSys, session.avgDia)
    val bgColor = BpClassifier.cardColor(cat)
    val txColor = BpClassifier.textColor(cat)
    val dateFmt = SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault())
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Messung löschen?") },
            confirmButton = {
                TextButton(onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = bgColor,
        title = {
            Column {
                Text(dateFmt.format(Date(session.timestampMillis)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = txColor)
                Text("Arm: ${if (session.arm == "LINKS") "Linker Arm" else "Rechter Arm"}  ·  ${session.measurementCount} Messung${if (session.measurementCount > 1) "en" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = txColor.copy(alpha = 0.7f))
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Einzel-Messungen
                DetailReadingTable(session = session, txColor = txColor)

                HorizontalDivider(color = txColor.copy(alpha = 0.2f))

                // Auswertungswert
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(txColor.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${session.evaluationLabel}:", style = MaterialTheme.typography.labelMedium,
                        color = txColor, fontWeight = FontWeight.SemiBold)
                    Text("${session.avgSys.toInt()} / ${session.avgDia.toInt()} mmHg  ·  ${session.avgPulse.toInt()} /min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = txColor, fontWeight = FontWeight.Bold)
                }

                // Kategorie
                Surface(color = txColor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(cat.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = txColor, fontWeight = FontWeight.SemiBold)
                }

                // Hinweis
                session.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text("💬 $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = txColor.copy(alpha = 0.85f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen", color = txColor) }
        },
        dismissButton = {
            TextButton(onClick = { confirmDelete = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Löschen")
            }
        },
    )
}

@Composable
private fun DetailReadingTable(session: MeasurementSession, txColor: Color) {
    data class R(val sys: Int, val dia: Int, val pulse: Int)
    val readings = buildList {
        add(R(session.sys1, session.dia1, session.pulse1))
        val s2 = session.sys2; val d2 = session.dia2; val p2 = session.pulse2
        if (s2 != null && d2 != null && p2 != null) add(R(s2, d2, p2))
        val s3 = session.sys3; val d3 = session.dia3; val p3 = session.pulse3
        if (s3 != null && d3 != null && p3 != null) add(R(s3, d3, p3))
    }
    val evalIndices: Set<Int> = when (readings.size) {
        1 -> setOf(0); 2 -> setOf(0, 1); else -> setOf(1, 2)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("M", Modifier.width(24.dp), fontSize = 11.sp, color = txColor.copy(alpha = 0.6f))
            listOf("Sys", "Dia", "Puls").forEach { label ->
                Text(label, Modifier.weight(1f), fontSize = 11.sp,
                    color = txColor.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            }
        }
        readings.forEachIndexed { i, r ->
            val hl = i in evalIndices
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", Modifier.width(24.dp),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (hl) txColor else txColor.copy(alpha = 0.55f))
                listOf(r.sys, r.dia, r.pulse).forEach { v ->
                    Text("$v", Modifier.weight(1f), textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = if (hl) txColor else txColor.copy(alpha = 0.55f))
                }
            }
        }
    }
}

// ── Session-Karte (Detailansicht) ─────────────────────────────────────────────

@Composable
private fun SessionCard(session: MeasurementSession, onDelete: () -> Unit) {
    val cat     = BpClassifier.classify(session.avgSys, session.avgDia)
    val bgColor = BpClassifier.cardColor(cat)
    val txColor = BpClassifier.textColor(cat)
    val dateFmt = SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault())

    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Messung löschen?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = bgColor),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // Kopfzeile
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(dateFmt.format(Date(session.timestampMillis)),
                        style = MaterialTheme.typography.labelMedium, color = txColor)
                    Text("Arm: ${if (session.arm == "LINKS") "Links" else "Rechts"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = txColor.copy(alpha = 0.7f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = txColor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                        Text(cat.shortLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = txColor, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, "Löschen",
                            modifier = Modifier.size(18.dp),
                            tint = txColor.copy(alpha = 0.6f))
                    }
                }
            }

            HorizontalDivider(color = txColor.copy(alpha = 0.15f))

            // Mess-Tabelle
            ReadingTable(session = session, txColor = txColor)

            // Auswertungswert
            Row(
                Modifier.fillMaxWidth()
                    .background(txColor.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${session.evaluationLabel}:", style = MaterialTheme.typography.labelMedium,
                    color = txColor, fontWeight = FontWeight.SemiBold)
                Text(
                    "${session.avgSys.toInt()} / ${session.avgDia.toInt()} mmHg  ·  ${session.avgPulse.toInt()} /min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = txColor, fontWeight = FontWeight.Bold,
                )
            }

            // Hinweis / Kommentar
            session.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    "💬 $note",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = txColor.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ReadingTable(session: MeasurementSession, txColor: Color) {
    data class Row(val sys: Int, val dia: Int, val pulse: Int)

    val readings = buildList {
        add(Row(session.sys1, session.dia1, session.pulse1))
        val s2 = session.sys2; val d2 = session.dia2; val p2 = session.pulse2
        if (s2 != null && d2 != null && p2 != null) add(Row(s2, d2, p2))
        val s3 = session.sys3; val d3 = session.dia3; val p3 = session.pulse3
        if (s3 != null && d3 != null && p3 != null) add(Row(s3, d3, p3))
    }

    val evalIndices: Set<Int> = when (readings.size) {
        1    -> setOf(0)
        2    -> setOf(0, 1)
        else -> setOf(1, 2)
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("M", Modifier.width(24.dp), fontSize = 11.sp, color = txColor.copy(alpha = 0.6f))
            listOf("Sys", "Dia", "Puls").forEach { label ->
                Text(label, Modifier.weight(1f), fontSize = 11.sp,
                    color = txColor.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            }
        }
        readings.forEachIndexed { i, r ->
            val highlight = i in evalIndices
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", Modifier.width(24.dp),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (highlight) txColor else txColor.copy(alpha = 0.55f))
                listOf(r.sys, r.dia, r.pulse).forEach { v ->
                    Text("$v", Modifier.weight(1f), textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = if (highlight) txColor else txColor.copy(alpha = 0.55f))
                }
            }
        }
    }
}
