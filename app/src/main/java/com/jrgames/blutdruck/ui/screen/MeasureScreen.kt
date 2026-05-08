package com.jrgames.blutdruck.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrgames.blutdruck.data.local.MeasurementSession
import com.jrgames.blutdruck.ui.theme.BpBlue
import com.jrgames.blutdruck.ui.theme.BpGreen
import kotlinx.coroutines.delay

// ── Zustandsmodell ───────────────────────────────────────────────────────────

private enum class Arm { LINKS, RECHTS }

private data class BpReading(val sys: Int, val dia: Int, val pulse: Int)

private sealed class Phase {
    object ArmSelect                    : Phase()
    data class Input(val index: Int)    : Phase()  // 0, 1, 2
    data class Countdown(val next: Int) : Phase()  // Pause vor Messung 2 oder 3
    object Summary                      : Phase()
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureScreen(
    noteSuggestions: List<String> = emptyList(),
    onSave: (MeasurementSession) -> Unit,
    onBack: () -> Unit,
) {
    var phase    by remember { mutableStateOf<Phase>(Phase.ArmSelect) }
    var arm      by remember { mutableStateOf(Arm.LINKS) }
    val readings = remember { mutableStateListOf<BpReading>() }
    var noteText by remember { mutableStateOf("") }

    var secondsLeft by remember { mutableIntStateOf(30) }

    // Bildschirm während der Messung wach halten; nach Summary wieder freigeben
    val view = LocalView.current
    val keepScreenOn = phase !is Phase.Summary
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(phase) {
        if (phase is Phase.Countdown) {
            secondsLeft = 30
            while (secondsLeft > 0) {
                delay(1_000L)
                secondsLeft--
            }
            phase = Phase.Input((phase as Phase.Countdown).next)
        }
    }

    // Helper: Session aus aktuellen readings bauen und speichern
    fun buildAndSave() {
        onSave(
            MeasurementSession(
                arm    = arm.name,
                sys1   = readings[0].sys,   dia1   = readings[0].dia,   pulse1 = readings[0].pulse,
                sys2   = readings.getOrNull(1)?.sys,
                dia2   = readings.getOrNull(1)?.dia,
                pulse2 = readings.getOrNull(1)?.pulse,
                sys3   = readings.getOrNull(2)?.sys,
                dia3   = readings.getOrNull(2)?.dia,
                pulse3 = readings.getOrNull(2)?.pulse,
                note   = noteText.trim().ifBlank { null },
            )
        )
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blutdruckmessung") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 24.dp),
        ) {
            when (val p = phase) {
                Phase.ArmSelect -> ArmSelectStep(arm, onSelect = { arm = it; phase = Phase.Input(0) })

                is Phase.Input -> InputStep(
                    index     = p.index,
                    onConfirm = { r ->
                        readings.add(r)
                        phase = if (p.index < 2) Phase.Countdown(p.index + 1) else Phase.Summary
                    },
                    onFinishNow = { r ->
                        readings.add(r)
                        phase = Phase.Summary
                    },
                )

                is Phase.Countdown -> CountdownStep(
                    secondsLeft = secondsLeft,
                    nextIndex   = p.next,
                    onSkip      = { phase = Phase.Input(p.next) },
                )

                Phase.Summary -> SummaryStep(
                    arm             = arm,
                    readings        = readings,
                    noteText        = noteText,
                    noteSuggestions = noteSuggestions,
                    onNoteChange    = { noteText = it },
                    onSave          = { buildAndSave() },
                    onDiscard       = onBack,
                )
            }
        }
    }
}

// ── Schritt 0: Arm-Auswahl ───────────────────────────────────────────────────

@Composable
private fun ArmSelectStep(selected: Arm, onSelect: (Arm) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Welchen Arm messen Sie?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)

        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(Arm.LINKS, Arm.RECHTS).forEach { arm ->
                val isSelected = arm == selected
                OutlinedButton(
                    onClick = { onSelect(arm) },
                    modifier = Modifier.size(width = 140.dp, height = 60.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) BpBlue else Color.Transparent,
                        contentColor   = if (isSelected) Color.White else BpBlue,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder,
                ) {
                    Text(
                        if (arm == Arm.LINKS) "Linker Arm" else "Rechter Arm",
                        fontWeight = FontWeight.SemiBold,
                        textAlign  = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Tippen Sie auf einen Arm, um fortzufahren.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}

// ── Schritt 1-3: Eingabe ─────────────────────────────────────────────────────

private fun inRange(text: String, range: IntRange) =
    text.toIntOrNull()?.let { it in range } == true

@Composable
private fun InputStep(
    index:       Int,
    onConfirm:   (BpReading) -> Unit,
    onFinishNow: (BpReading) -> Unit,
) {
    var sysText   by remember(index) { mutableStateOf("") }
    var diaText   by remember(index) { mutableStateOf("") }
    var pulseText by remember(index) { mutableStateOf("") }

    val isValid = inRange(sysText, 60..250) &&
                  inRange(diaText, 30..150) &&
                  inRange(pulseText, 30..220)

    val reading = if (isValid)
        BpReading(sysText.toInt(), diaText.toInt(), pulseText.toInt()) else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Step-Indikator
        StepIndicator(current = index)
        Spacer(Modifier.height(20.dp))

        Text(
            "Messung ${index + 1} von 3",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(20.dp))

        // Eingabefelder
        BpTextField(label = "Systolisch (mmHg)",  value = sysText,   onValue = { sysText   = it }, range = 60..250)
        Spacer(Modifier.height(12.dp))
        BpTextField(label = "Diastolisch (mmHg)", value = diaText,   onValue = { diaText   = it }, range = 30..150)
        Spacer(Modifier.height(12.dp))
        BpTextField(label = "Herzfrequenz (/min)", value = pulseText, onValue = { pulseText = it }, range = 30..220)

        Spacer(Modifier.height(20.dp))

        // Beide Buttons nebeneinander
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Sekundär-Aktion: jetzt abschließen
            OutlinedButton(
                onClick  = { reading?.let(onFinishNow) },
                enabled  = isValid,
                modifier = Modifier.weight(1f).height(54.dp),
                shape    = RoundedCornerShape(14.dp),
            ) {
                Text(
                    when (index) {
                        0    -> "Mit 1 abschließen"
                        1    -> "Mit 2 abschließen"
                        else -> "Mit 3 abschließen"
                    },
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }

            // Primär-Aktion: Weiter oder Fertig
            Button(
                onClick  = { reading?.let(onConfirm) },
                enabled  = isValid,
                modifier = Modifier.weight(1f).height(54.dp),
                shape    = RoundedCornerShape(14.dp),
            ) {
                Text(
                    if (index < 2) "Weiter" else "Fertig",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (index > 0) {
            Spacer(Modifier.height(8.dp))
            Text("Bitte 1–2 Minuten ruhig sitzen, bevor Sie messen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BpTextField(label: String, value: String, onValue: (String) -> Unit, range: IntRange) {
    val isError = value.isNotEmpty() && (value.toIntOrNull() == null || value.toInt() !in range)
    OutlinedTextField(
        value           = value,
        onValueChange   = { if (it.length <= 3) onValue(it.filter { c -> c.isDigit() }) },
        label           = { Text(label) },
        isError         = isError,
        supportingText  = if (isError) {{ Text("Gültig: $range") }} else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine      = true,
        modifier        = Modifier.fillMaxWidth(),
    )
}

// ── Countdown ────────────────────────────────────────────────────────────────

@Composable
private fun CountdownStep(secondsLeft: Int, nextIndex: Int, onSkip: () -> Unit) {
    val progress by animateFloatAsState(
        targetValue   = secondsLeft / 30f,
        animationSpec = tween(durationMillis = 900),
        label         = "timer",
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StepIndicator(current = nextIndex - 1)
        Spacer(Modifier.height(32.dp))

        Text("30 Sekunden Pause",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Bitte ruhig sitzen, Arm entspannt auf dem Tisch lassen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)

        Spacer(Modifier.height(40.dp))

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress    = { progress },
                modifier    = Modifier.size(140.dp),
                strokeWidth = 8.dp,
                color       = BpBlue,
                trackColor  = MaterialTheme.colorScheme.surfaceVariant,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$secondsLeft", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = BpBlue)
                Text("Sekunden", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Nächste Messung: Messung ${nextIndex + 1} von 3",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onSkip) {
            Text("Pause überspringen", style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ── Zusammenfassung ───────────────────────────────────────────────────────────

@Composable
private fun SummaryStep(
    arm:             Arm,
    readings:        List<BpReading>,
    noteText:        String,
    noteSuggestions: List<String>,
    onNoteChange:    (String) -> Unit,
    onSave:          () -> Unit,
    onDiscard:       () -> Unit,
) {
    // Auswertung analog MeasurementSession.evaluatedReadings
    val evaluated = when (readings.size) {
        1    -> readings.take(1)
        2    -> readings.take(2)
        else -> readings.takeLast(2)
    }
    val evalLabel = when (readings.size) {
        1    -> "M1"
        2    -> "∅ M1+M2"
        else -> "∅ M2+M3"
    }
    val avgSys   = evaluated.map { it.sys }.average().toInt()
    val avgDia   = evaluated.map { it.dia }.average().toInt()
    val avgPulse = evaluated.map { it.pulse }.average().toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Messungen abgeschlossen",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)

        Text("Arm: ${if (arm == Arm.LINKS) "Linker Arm" else "Rechter Arm"}  ·  ${readings.size} Messung${if (readings.size > 1) "en" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Tabelle der Messungen
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReadingHeader()
                HorizontalDivider()
                readings.forEachIndexed { i, r ->
                    ReadingRow(index = i + 1, r = r, isHighlighted = true)
                }
            }
        }

        // Auswertungswert
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = BpBlue.copy(alpha = 0.08f)),
            shape    = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Auswertungswert  $evalLabel",
                    style = MaterialTheme.typography.labelLarge,
                    color = BpBlue, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$avgSys / $avgDia",
                        fontSize = 34.sp, fontWeight = FontWeight.Bold, color = BpBlue)
                    Text("mmHg", style = MaterialTheme.typography.bodyMedium,
                        color = BpBlue.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
                }
                Text("Puls: $avgPulse /min",
                    style = MaterialTheme.typography.bodyMedium, color = BpBlue)
            }
        }

        // Kommentarfeld mit Dropdown-Vorschlägen
        NoteField(value = noteText, suggestions = noteSuggestions, onValueChange = onNoteChange)

        Spacer(Modifier.height(4.dp))

        Button(
            onClick  = onSave,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = BpGreen),
        ) {
            Icon(Icons.Default.Check, null)
            Spacer(Modifier.width(8.dp))
            Text("Speichern", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick  = onDiscard,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = RoundedCornerShape(14.dp),
        ) { Text("Verwerfen") }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Kommentar-Feld mit Vorschlägen ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteField(
    value:         String,
    suggestions:   List<String>,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    // Vorschläge filtern: alle wenn leer, sonst nach Query
    val filtered = remember(value, suggestions) {
        val q = value.trim()
        if (q.isBlank()) suggestions
        else suggestions.filter { it.contains(q, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded          = expanded && filtered.isNotEmpty(),
        onExpandedChange  = { expanded = it },
    ) {
        OutlinedTextField(
            value         = value,
            onValueChange = { onValueChange(it); expanded = true },
            label         = { Text("Hinweis / Kommentar (optional)") },
            placeholder   = { Text("z. B. nach dem Kaffee, nach dem Sport …") },
            modifier      = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            trailingIcon  = {
                if (filtered.isNotEmpty())
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && filtered.isNotEmpty())
            },
            minLines      = 2,
        )

        ExposedDropdownMenu(
            expanded          = expanded && filtered.isNotEmpty(),
            onDismissRequest  = { expanded = false },
        ) {
            filtered.take(8).forEach { suggestion ->
                DropdownMenuItem(
                    text    = { Text(suggestion) },
                    onClick = { onValueChange(suggestion); expanded = false },
                )
            }
        }
    }
}

// ── Hilfskomposables ─────────────────────────────────────────────────────────

@Composable
private fun ReadingHeader() {
    Row(Modifier.fillMaxWidth()) {
        Text("M",    Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Sys",  Modifier.weight(1f),   style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text("Dia",  Modifier.weight(1f),   style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text("Puls", Modifier.weight(1f),   style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ReadingRow(index: Int, r: BpReading, isHighlighted: Boolean) {
    val textColor = if (isHighlighted) BpBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$index",    Modifier.width(28.dp), fontWeight = FontWeight.Bold, color = textColor)
        Text("${r.sys}",  Modifier.weight(1f),   textAlign = TextAlign.Center, color = textColor)
        Text("${r.dia}",  Modifier.weight(1f),   textAlign = TextAlign.Center, color = textColor)
        Text("${r.pulse}",Modifier.weight(1f),   textAlign = TextAlign.Center, color = textColor)
    }
}


@Composable
private fun StepIndicator(current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val done   = i < current
            val active = i == current
            Surface(
                modifier = Modifier.size(if (active) 12.dp else 10.dp),
                shape    = CircleShape,
                color    = when {
                    done   -> BpGreen
                    active -> BpBlue
                    else   -> MaterialTheme.colorScheme.surfaceVariant
                },
            ) {}
        }
    }
}

