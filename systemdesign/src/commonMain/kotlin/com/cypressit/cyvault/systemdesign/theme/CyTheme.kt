package com.cypressit.cyvault.systemdesign.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = CyColors.Primary,
    onPrimary = CyColors.OnPrimary,
    primaryContainer = CyColors.PrimaryContainer,
    onPrimaryContainer = CyColors.OnPrimaryContainer,
    secondary = CyColors.Secondary,
    onSecondary = CyColors.OnSecondary,
    background = CyColors.Background,
    onBackground = CyColors.OnBackground,
    surface = CyColors.Surface,
    onSurface = CyColors.OnSurface,
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
    background = CyColors.BackgroundDark,
    onBackground = CyColors.OnBackgroundDark,
    surface = CyColors.SurfaceDark,
    onSurface = CyColors.OnSurfaceDark,
    error = CyColors.Error,
    onError = CyColors.OnError,
)

@Composable
fun CyTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CyTypography,
        shapes = CyShapes,
        content = content,
    )
}
