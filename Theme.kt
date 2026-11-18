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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF032B60),
    primaryContainer = LubumBlueDark,
    onPrimaryContainer = LubumBlueContainer,
    secondary = LubumRed,
    onSecondary = Color.White,
    secondaryContainer = LubumRedDark,
    onSecondaryContainer = LubumRedLight,
    tertiary = DeliveryBlue,
    onTertiary = Color.White,
    background = DarkSurface,
    surface = DarkSurfaceCard,
    surfaceVariant = Color(0xFF334155),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = LubumRed
)

private val LightColorScheme = lightColorScheme(
    primary = LubumBlue,
    onPrimary = Color.White,
    primaryContainer = LubumBlueContainer,
    onPrimaryContainer = LubumOnBlueContainer,
    secondary = LubumRed,
    onSecondary = Color.White,
    secondaryContainer = LubumRedLight,
    onSecondaryContainer = LubumRedDark,
    tertiary = DeliveryBlue,
    onTertiary = Color.White,
    tertiaryContainer = DeliveryBlueLight,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor,
    error = LubumRed
)

@Composable
fun LubumarketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
