package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = DarkAccentPurple,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkAccentContainer,
    onPrimaryContainer = DarkAccentPurple,
    secondary = PurpleGrey80,
    onSecondary = DarkOnPrimary,
    secondaryContainer = DarkCardBg,
    onSecondaryContainer = DarkTextSec,
    tertiary = Pink80,
    onTertiary = Color(0xFF492532),
    tertiaryContainer = DarkAccentContainer,
    onTertiaryContainer = DarkAccentPurple,
    background = DarkBg,
    onBackground = DarkTextMain,
    surface = DarkBg,
    onSurface = DarkTextMain,
    surfaceVariant = DarkCardBg,
    onSurfaceVariant = DarkTextSec,
    outline = DarkBorder
  )

private val LightColorScheme = DarkColorScheme // Elegant Dark is forced to keep design consistency

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Forced elegant dark
  dynamicColor: Boolean = false, // Disable system dynamic tint to respect custom Elegant Dark canvas design
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
