package com.jrgames.blutdruck

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jrgames.blutdruck.ui.screen.ChartScreen
import com.jrgames.blutdruck.ui.screen.HistoryScreen
import com.jrgames.blutdruck.ui.screen.HomeScreen
import com.jrgames.blutdruck.ui.screen.MeasureScreen
import com.jrgames.blutdruck.ui.screen.SettingsScreen
import com.jrgames.blutdruck.ui.screen.StatsScreen
import com.jrgames.blutdruck.ui.theme.BlutdruckTheme
import com.jrgames.blutdruck.ui.viewmodel.BlutdruckViewModel

private object Routes {
    const val HOME     = "home"
    const val MEASURE  = "measure"
    const val HISTORY  = "history"
    const val CHART    = "chart"
    const val STATS    = "stats"
    const val SETTINGS = "settings"
}

@Composable
fun BlutdruckApp(viewModel: BlutdruckViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    // Nach erfolgreichem Speichern zurück zur Startseite
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            viewModel.clearSaveSuccess()
            navController.popBackStack(Routes.HOME, inclusive = false)
        }
    }

    BlutdruckTheme {
        NavHost(navController = navController, startDestination = Routes.HOME) {

            composable(Routes.HOME) {
                HomeScreen(
                    sessions       = uiState.sessions,
                    onStartMeasure = { navController.navigate(Routes.MEASURE) },
                    onHistory      = { navController.navigate(Routes.HISTORY) },
                    onChart        = { navController.navigate(Routes.CHART) },
                    onStats        = { navController.navigate(Routes.STATS) },
                    onSettings     = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(Routes.MEASURE) {
                MeasureScreen(
                    noteSuggestions = uiState.noteSuggestions,
                    onSave          = viewModel::saveMeasurement,
                    onBack          = { navController.popBackStack() },
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    sessions = uiState.sessions,
                    onDelete = viewModel::deleteSession,
                    onBack   = { navController.popBackStack() },
                )
            }

            composable(Routes.CHART) {
                ChartScreen(
                    sessions = uiState.sessions,
                    onBack   = { navController.popBackStack() },
                )
            }

            composable(Routes.STATS) {
                StatsScreen(
                    sessions = uiState.sessions,
                    onBack   = { navController.popBackStack() },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    config           = uiState.backupConfig,
                    isBackingUp      = uiState.isBackingUp,
                    lastBackupResult = uiState.lastBackupResult,
                    sessionCount     = uiState.sessions.size,
                    onSaveConfig     = viewModel::saveBackupConfig,
                    onTriggerBackup  = viewModel::triggerBackup,
                    onClearResult    = viewModel::clearBackupResult,
                    onBack           = { navController.popBackStack() },
                )
            }
        }
    }
}

