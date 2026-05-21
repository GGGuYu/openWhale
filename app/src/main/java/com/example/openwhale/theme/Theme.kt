package com.example.openwhale.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = WhaleDarkAccent,
    onPrimary = WhaleInk,
    secondary = WhaleAccentSoft,
    background = WhaleDarkCanvas,
    surface = WhaleDarkSurface,
    surfaceVariant = WhaleInkSoft,
    onSurface = WhaleCanvas,
    onSurfaceVariant = WhaleAccentSoft,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = WhaleAccent,
    onPrimary = WhaleCanvas,
    secondary = WhaleInkSoft,
    tertiary = WhaleSuccess,
    background = WhaleCanvas,
    surface = Color(0xFFFFFFFF),
    surfaceVariant = WhaleSurface,
    onSurface = WhaleInk,
    onSurfaceVariant = WhaleInkSoft,
  )

@Composable
fun OpenWhaleTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
