package com.jrgames.blutdruck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Blutdruck-Farbpalette ────────────────────────────────────────────────────
val BpRed        = Color(0xFFD32F2F)
val BpRedLight   = Color(0xFFFFEBEE)
val BpGreen      = Color(0xFF388E3C)
val BpGreenLight = Color(0xFFE8F5E9)
val BpAmber      = Color(0xFFF57C00)
val BpBlue       = Color(0xFF1565C0)
val BpPurple     = Color(0xFF7B1FA2)
val BpBlueLight  = Color(0xFFE3F2FD)
val BpSurface    = Color(0xFFF8FAFB)
val BpOnSurface  = Color(0xFF1C1B1F)
val BpOutline    = Color(0xFFCAC4D0)

private val LightColors = lightColorScheme(
    primary        = BpBlue,
    onPrimary      = Color.White,
    primaryContainer    = BpBlueLight,
    onPrimaryContainer  = BpBlue,
    secondary      = BpGreen,
    onSecondary    = Color.White,
    error          = BpRed,
    onError        = Color.White,
    background     = BpSurface,
    onBackground   = BpOnSurface,
    surface        = Color.White,
    onSurface      = BpOnSurface,
    surfaceVariant = Color(0xFFEFF1F4),
    outline        = BpOutline,
)

@Composable
fun BlutdruckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}

