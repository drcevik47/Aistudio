package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CleanMinimalColorScheme = lightColorScheme(
    primary = MinimalPrimary,
    onPrimary = Color.White,
    primaryContainer = MinimalPrimaryLight,
    onPrimaryContainer = MinimalPrimaryDark,
    secondary = MinimalSecondary,
    onSecondary = Color.White,
    secondaryContainer = MinimalSecondaryLight,
    onSecondaryContainer = MinimalSecondary,
    tertiary = MinimalSuccess,
    onTertiary = Color.White,
    tertiaryContainer = MinimalSuccessLight,
    onTertiaryContainer = MinimalSuccessDark,
    background = MinimalBg,
    onBackground = MinimalTextPrimary,
    surface = MinimalSurface,
    onSurface = MinimalTextPrimary,
    surfaceVariant = MinimalSurfaceElevated,
    onSurfaceVariant = MinimalTextSecondary,
    outline = MinimalSurfaceBorder,
    outlineVariant = MinimalSurfaceBorderLight,
    error = MinimalError,
    onError = Color.White,
    errorContainer = MinimalErrorLight,
    onErrorContainer = MinimalErrorDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Clean Minimalism uses crisp light palette
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CleanMinimalColorScheme,
        typography = Typography,
        content = content
    )
}

