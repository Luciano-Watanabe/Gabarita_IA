package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryTealLight,         // Color(0xFFD0BCFF)
    secondary = PrimaryTeal,            // Color(0xFF6750A4)
    tertiary = LightAccent,
    background = DarkBackground,        // Color(0xFF141218)
    surface = DarkSurface,              // Color(0xFF1C1B1F)
    surfaceVariant = DarkSurfaceVariant,// Color(0xFF49454F)
    outline = TextSecondaryDark,
    onPrimary = Color(0xFF381E72),
    onSecondary = Color.White,
    onBackground = TextPrimaryDark,     // Color(0xFFE6E1E5)
    onSurface = TextPrimaryDark,
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryTeal,              // Color(0xFF6750A4)
    secondary = PrimaryTealLight,       // Color(0xFFD0BCFF)
    tertiary = LightAccent,
    background = BackgroundLight,       // Color(0xFFFDFBFF)
    surface = SurfaceLight,             // Color(0xFFFFFFFF)
    surfaceVariant = SurfaceVariantLight, // Color(0xFFF3EDF7)
    outline = OutlineLight,             // Color(0xFFCAC4D0)
    onPrimary = Color.White,
    onSecondary = SecondaryNavy,
    onBackground = SecondaryNavy,       // Color(0xFF1C1B1F)
    onSurface = SecondaryNavy,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color by default to enforce the stunning "Geometric Balance" branding
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

