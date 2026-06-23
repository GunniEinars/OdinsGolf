package com.odinsgolf.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

// Outdoor-readable dark palette. High contrast, one accent green.
val OdinGreen = Color(0xFF22C55E)
val OdinGreenDim = Color(0xFF15803D)
val OdinAmber = Color(0xFFF59E0B)
val OdinRed = Color(0xFFEF4444)
val OdinBg = Color(0xFF1C2026)
val OdinSurface = Color(0xFF24292F)
val OdinOnDim = Color(0xFF9CA3AF)

private val OdinColors = Colors(
    primary = OdinGreen,
    primaryVariant = OdinGreenDim,
    secondary = OdinAmber,
    secondaryVariant = OdinAmber,
    background = OdinBg,
    surface = OdinSurface,
    error = OdinRed,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.Black,
)

@Composable
fun OdinsGolfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = OdinColors, content = content)
}
