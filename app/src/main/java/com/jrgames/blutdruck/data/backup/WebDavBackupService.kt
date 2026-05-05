package com.jrgames.blutdruck.data.backup

import com.jrgames.blutdruck.data.local.MeasurementSession
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class BackupResult {
    data class Success(val fileName: String) : BackupResult()
    data class Error(val message: String) : BackupResult()
}

object WebDavBackupService {

    private fun buildClient(config: BackupConfig): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .authenticator(object : Authenticator {
                override fun authenticate(route: Route?, response: Response): Request? {
                    if (response.request.header("Authorization") != null) return null
                    return response.request.newBuilder()
                        .header("Authorization", Credentials.basic(config.username, config.password))
                        .build()
                }
            })
            .build()

    fun backup(config: BackupConfig, sessions: List<MeasurementSession>): BackupResult {
        if (config.url.isBlank()) return BackupResult.Error("WebDAV-URL ist nicht konfiguriert.")
        if (config.username.isBlank()) return BackupResult.Error("Benutzername ist nicht konfiguriert.")

        val client   = buildClient(config)
        val csv      = buildCsv(sessions)
        val fileName = "blutdruck_backup_${
            SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(Date())
        }.csv"

        val remotePath = config.remotePath.trimEnd('/') + "/" + fileName
        val baseUrl    = config.url.trimEnd('/')
        val fullUrl    = baseUrl + remotePath

        return try {
            val dirUrl = baseUrl + "/" + config.remotePath.trim('/')
            mkColIfNeeded(dirUrl, config, client)

            val body    = csv.toRequestBody("text/csv; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(fullUrl)
                .header("Authorization", Credentials.basic(config.username, config.password))
                .put(body)
                .build()

            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful || response.code == 201 || response.code == 204 ->
                        BackupResult.Success(fileName)
                    response.code == 401 ->
                        BackupResult.Error(
                            "Anmeldung fehlgeschlagen (401).\n" +
                            "Bitte Benutzername und Passwort prüfen.\n" +
                            "Auth-Typ: ${response.header("WWW-Authenticate") ?: "unbekannt"}"
                        )
                    response.code == 403 ->
                        BackupResult.Error("Zugriff verweigert (403). Benutzer hat keine Schreibrechte auf: ${config.remotePath}")
                    response.code == 404 ->
                        BackupResult.Error("Pfad nicht gefunden (404). URL prüfen: $fullUrl")
                    else ->
                        BackupResult.Error("HTTP ${response.code}: ${response.message}\nURL: $fullUrl")
                }
            }
        } catch (e: Exception) {
            BackupResult.Error("Verbindungsfehler: ${e.message}\nURL: $fullUrl")
        }
    }

    private fun mkColIfNeeded(dirUrl: String, config: BackupConfig, client: OkHttpClient) {
        try {
            val request = Request.Builder()
                .url(dirUrl)
                .header("Authorization", Credentials.basic(config.username, config.password))
                .method("MKCOL", null)
                .build()
            client.newCall(request).execute().close()
        } catch (_: Exception) { }
    }

    private fun buildCsv(sessions: List<MeasurementSession>): String {
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val sb  = StringBuilder()
        sb.appendLine("Datum;Uhrzeit;Arm;Sys1;Dia1;Puls1;Sys2;Dia2;Puls2;Sys3;Dia3;Puls3;Avg_Sys;Avg_Dia;Avg_Puls;Auswertung;Hinweis")
        for (s in sessions.sortedBy { it.timestampMillis }) {
            val dt = fmt.format(Date(s.timestampMillis)).split(" ")
            sb.appendLine(listOf(
                dt[0], dt[1], s.arm,
                s.sys1, s.dia1, s.pulse1,
                s.sys2 ?: "", s.dia2 ?: "", s.pulse2 ?: "",
                s.sys3 ?: "", s.dia3 ?: "", s.pulse3 ?: "",
                "%.1f".format(s.avgSys),
                "%.1f".format(s.avgDia),
                "%.1f".format(s.avgPulse),
                s.evaluationLabel,
                (s.note ?: "").replace(";", ","),
            ).joinToString(";"))
        }
        return sb.toString()
    }
}
