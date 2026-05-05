package com.jrgames.blutdruck.data.backup

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "backup_settings")

data class BackupConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val remotePath: String = "/blutdruck/",
)

class BackupPreferences(private val context: Context) {

    companion object {
        private val KEY_URL      = stringPreferencesKey("webdav_url")
        private val KEY_USER     = stringPreferencesKey("webdav_user")
        private val KEY_PASS     = stringPreferencesKey("webdav_pass")
        private val KEY_PATH     = stringPreferencesKey("webdav_path")
    }

    val config: Flow<BackupConfig> = context.dataStore.data.map { prefs ->
        BackupConfig(
            url          = prefs[KEY_URL]  ?: "",
            username     = prefs[KEY_USER] ?: "",
            password     = prefs[KEY_PASS] ?: "",
            remotePath   = prefs[KEY_PATH] ?: "/blutdruck/",
        )
    }

    suspend fun save(config: BackupConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_URL]  = config.url
            prefs[KEY_USER] = config.username
            prefs[KEY_PASS] = config.password
            prefs[KEY_PATH] = config.remotePath
        }
    }
}

