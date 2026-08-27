package com.viel.compose.popupselector.sample.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val LightPrimary = Color(0xFF5A55CA)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE2DFFF)
val LightOnPrimaryContainer = Color(0xFF140765)

val LightBackground = Color(0xFFFBF8FD)
val LightOnBackground = Color(0xFF1B1B1F)
val LightSurface = Color(0xFFFBF8FD)
val LightOnSurface = Color(0xFF1B1B1F)
val LightSurfaceVariant = Color(0xFFE4E1EC)
val LightOnSurfaceVariant = Color(0xFF47464F)
val LightSurfaceContainer = Color(0xFFF0EDF4)
val LightSurfaceContainerHigh = Color(0xFFEAE7EE)
val LightOutline = Color(0xFF777680)
val LightOutlineVariant = Color(0xFFC8C5D0)

val DarkPrimary = Color(0xFFC3C0FF)
val DarkOnPrimary = Color(0xFF2B229A)
val DarkPrimaryContainer = Color(0xFF423CB2)
val DarkOnPrimaryContainer = Color(0xFFE2DFFF)

val DarkBackground = Color(0xFF131316)
val DarkOnBackground = Color(0xFFE4E1E6)
val DarkSurface = Color(0xFF131316)
val DarkOnSurface = Color(0xFFE4E1E6)
val DarkSurfaceVariant = Color(0xFF47464F)
val DarkOnSurfaceVariant = Color(0xFFC8C5D0)
val DarkSurfaceContainer = Color(0xFF1F1F23)
val DarkSurfaceContainerHigh = Color(0xFF29292D)
val DarkOutline = Color(0xFF918F9A)
val DarkOutlineVariant = Color(0xFF47464F)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)
