package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val LuxuryDarkColorScheme = darkColorScheme(
    primary = ChampagneGold,
    onPrimary = OnGoldDark,
    primaryContainer = GoldContainerDark,
    onPrimaryContainer = BrightGold,
    secondary = WhatsAppGreen,
    onSecondary = Color(0xFF04210F),
    secondaryContainer = WhatsAppContainer,
    onSecondaryContainer = Color(0xFF8EF0B1),
    tertiary = DiffAmber,
    onTertiary = Color(0xFF241600),
    tertiaryContainer = DiffAmberContainer,
    onTertiaryContainer = Color(0xFFFFD88A),
    background = ObsidianVoid,
    onBackground = IvoryInk,
    surface = ObsidianSurface,
    onSurface = IvoryInk,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = WarmMutedInk,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianCard,
    surfaceContainerHigh = ObsidianCardHigh,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF282622),
    error = ErrorCrimson,
    onError = Color(0xFF2E0406),
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFFFB4B4),
)

private val WarmLightColorScheme = lightColorScheme(
    primary = LightBronzePrimary,
    onPrimary = Color.White,
    primaryContainer = LightBronzeContainer,
    onPrimaryContainer = Color(0xFF2D1E00),
    secondary = WhatsAppGreenDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F7E2),
    onSecondaryContainer = Color(0xFF073B1A),
    tertiary = Color(0xFFA86B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE5B8),
    onTertiaryContainer = Color(0xFF331D00),
    background = LightIvoryBackground,
    onBackground = LightInk,
    surface = LightIvorySurface,
    onSurface = LightInk,
    surfaceVariant = LightIvoryCard,
    onSurfaceVariant = LightMutedInk,
    outline = Color(0xFFD6CCB9),
    outlineVariant = Color(0xFFE7DFD0),
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TasaBisuTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) LuxuryDarkColorScheme else WarmLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
