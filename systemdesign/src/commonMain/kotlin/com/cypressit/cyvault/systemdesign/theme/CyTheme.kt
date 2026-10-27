package com.cypressit.cyvault.systemdesign.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = CyColors.Primary,
    onPrimary = CyColors.OnPrimary,
    primaryContainer = CyColors.PrimaryContainer,
    onPrimaryContainer = CyColors.OnPrimaryContainer,
    secondary = CyColors.Secondary,
    onSecondary = CyColors.OnSecondary,
    secondaryContainer = CyColors.SecondaryContainer,
    onSecondaryContainer = CyColors.OnSecondaryContainer,
    background = CyColors.Background,
    onBackground = CyColors.OnBackground,
    surface = CyColors.Surface,
    onSurface = CyColors.OnSurface,
    surfaceVariant = CyColors.SurfaceVariant,
    onSurfaceVariant = CyColors.OnSurfaceVariant,
    surfaceTint = CyColors.Primary,
    outline = CyColors.Outline,
    outlineVariant = CyColors.OutlineVariant,
    surfaceContainerLowest = CyColors.SurfaceContainerLowest,
    surfaceContainerLow = CyColors.SurfaceContainerLow,
    surfaceContainer = CyColors.SurfaceContainer,
    surfaceContainerHigh = CyColors.SurfaceContainerHigh,
    surfaceContainerHighest = CyColors.SurfaceContainerHighest,
    error = CyColors.Error,
    onError = CyColors.OnError,
)

private val DarkColors = darkColorScheme(
    primary = CyColors.PrimaryDark,
    onPrimary = CyColors.OnPrimaryDark,
    primaryContainer = CyColors.PrimaryContainerDark,
    onPrimaryContainer = CyColors.OnPrimaryContainerDark,
    secondary = CyColors.SecondaryDark,
    onSecondary = CyColors.OnSecondaryDark,
    secondaryContainer = CyColors.SecondaryContainerDark,
    onSecondaryContainer = CyColors.OnSecondaryContainerDark,
    background = CyColors.BackgroundDark,
    onBackground = CyColors.OnBackgroundDark,
    surface = CyColors.SurfaceDark,
    onSurface = CyColors.OnSurfaceDark,
    surfaceVariant = CyColors.SurfaceVariantDark,
    onSurfaceVariant = CyColors.OnSurfaceVariantDark,
    surfaceTint = CyColors.PrimaryDark,
    outline = CyColors.OutlineDark,
    outlineVariant = CyColors.OutlineVariantDark,
    surfaceContainerLowest = CyColors.SurfaceContainerLowestDark,
    surfaceContainerLow = CyColors.SurfaceContainerLowDark,
    surfaceContainer = CyColors.SurfaceContainerDark,
    surfaceContainerHigh = CyColors.SurfaceContainerHighDark,
    surfaceContainerHighest = CyColors.SurfaceContainerHighestDark,
    error = CyColors.Error,
    onError = CyColors.OnError,
)

/**
 * Brand roles Material has no slot for. The top bar can't simply use `primary`: in dark mode
 * `primary` is a light purple (so buttons stay visible), while the bar stays a dark purple.
 */
@Immutable
data class CyBrandColors(
    val topBar: Color,
    val onTopBar: Color,
)

private val LightBrandColors = CyBrandColors(
    topBar = CyColors.Primary,
    onTopBar = CyColors.OnPrimary,
)

private val DarkBrandColors = CyBrandColors(
    topBar = CyColors.TopBarDark,
    onTopBar = CyColors.OnTopBarDark,
)

private val LocalCyBrandColors = staticCompositionLocalOf { LightBrandColors }

/**
 * Follows the system's light/dark setting. [darkTheme] is a parameter so previews and tests
 * can force a mode, and a later in-app setting can pass its own value.
 */
@Composable
fun CyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalCyBrandColors provides if (darkTheme) DarkBrandColors else LightBrandColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = CyTypography,
            shapes = CyShapes,
            content = content,
        )
    }
}

/** Read the brand roles like MaterialTheme.colorScheme: `CyTheme.brandColors.topBar`. */
object CyTheme {
    val brandColors: CyBrandColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCyBrandColors.current
}
