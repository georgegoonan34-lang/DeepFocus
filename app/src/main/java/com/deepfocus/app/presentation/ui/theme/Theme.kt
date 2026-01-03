package com.deepfocus.app.presentation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Pure black and white - no distractions
private val BlackWhiteColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color.Black,
    onPrimaryContainer = Color.White,
    secondary = Color.White,
    onSecondary = Color.Black,
    secondaryContainer = Color.Black,
    onSecondaryContainer = Color.White,
    tertiary = Color.White,
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFCCCCCC),
    error = Color(0xFFCC0000),
    onError = Color.White,
)

@Composable
fun DeepFocusTheme(
    content: @Composable () -> Unit
) {
    // Always use the black/white theme - no light mode, no distractions
    MaterialTheme(
        colorScheme = BlackWhiteColorScheme,
        typography = Typography,
        content = content
    )
}
