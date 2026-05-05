package com.jrgames.blutdruck.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrgames.blutdruck.data.backup.BackupConfig
import com.jrgames.blutdruck.ui.theme.BpBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: BackupConfig,
    isBackingUp: Boolean,
    lastBackupResult: String?,
    sessionCount: Int,
    onSaveConfig: (BackupConfig) -> Unit,
    onTriggerBackup: () -> Unit,
    onClearResult: () -> Unit,
    onBack: () -> Unit,
) {
    var url        by remember(config.url)          { mutableStateOf(config.url) }
    var username   by remember(config.username)     { mutableStateOf(config.username) }
    var password   by remember(config.password)     { mutableStateOf(config.password) }
    var remotePath by remember(config.remotePath)   { mutableStateOf(config.remotePath) }
    var showPass   by remember { mutableStateOf(false) }
    var dirty      by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // show snackbar when result arrives
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(lastBackupResult) {
        if (lastBackupResult != null) {
            snackbarHostState.showSnackbar(lastBackupResult, duration = SnackbarDuration.Long)
            onClearResult()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .imePadding()                      // Schiebt Inhalt über die Tastatur
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Info Card ──────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BpBlue.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("WebDAV-Backup", fontWeight = FontWeight.Bold, color = BpBlue, fontSize = 16.sp)
                    Text(
                        "Sichert alle $sessionCount Messungen als CSV-Datei auf Ihren NAS/Server. " +
                        "WebDAV wird von Synology, QNAP, TrueNAS und Nextcloud unterstützt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "💡 Synology: WebDAV-Server-Paket aktivieren · " +
                        "URL: http(s)://<NAS-IP>:5005 oder :5006 (HTTPS)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── WebDAV-Konfiguration ───────────────────────────────────────
            Text("Server-Konfiguration", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = url,
                onValueChange = { url = it; dirty = true },
                label = { Text("WebDAV-URL") },
                placeholder = { Text("http://192.168.1.100:5005") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; dirty = true },
                label = { Text("Benutzername") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; dirty = true },
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = remotePath,
                onValueChange = { remotePath = it; dirty = true },
                label = { Text("Verzeichnis auf dem NAS") },
                placeholder = { Text("/blutdruck/") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Speichern ─────────────────────────────────────────────────
            Button(
                onClick = {
                    onSaveConfig(BackupConfig(url.trim(), username, password, remotePath.trim()))
                    dirty = false
                },
                enabled = dirty,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Einstellungen speichern")
            }

            HorizontalDivider()

            // ── Manuelles Backup ──────────────────────────────────────────
            Text("Backup", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)

            Button(
                onClick = onTriggerBackup,
                enabled = !isBackingUp && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BpBlue),
            ) {
                if (isBackingUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Sicherung läuft…", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Jetzt auf NAS sichern ($sessionCount Einträge)",
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (url.isBlank()) {
                Text(
                    "⚠️ Bitte zuerst die WebDAV-URL konfigurieren und speichern.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

