package com.cypressit.cyvault.systemdesign.theme

import androidx.compose.ui.graphics.Color

/**
 * The brand palette: purple primary (#9C27B0) with its dark (#7B1FA2) and light (#E1BEE7)
 * shades, a deep-purple accent (#7C4DFF), and neutral greys for text and dividers. Every
 * Material role a component may use is set here, so nothing falls back to Material's baseline.
 *
 * Internal: screens use MaterialTheme.colorScheme, so they stay right in light and dark.
 */
internal object CyColors {
    // Light
    val Primary = Color(0xFF9C27B0) // primary
    val OnPrimary = Color(0xFFFFFFFF) // text / icons
    val PrimaryContainer = Color(0xFFE1BEE7) // light primary
    val OnPrimaryContainer = Color(0xFF7B1FA2) // dark primary
    val Secondary = Color(0xFF7C4DFF) // accent
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFEDE7FF)
    val OnSecondaryContainer = Color(0xFF4A2AB3)
    val Background = Color(0xFFFFFFFF)
    val OnBackground = Color(0xFF212121) // primary text
    val Surface = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFF212121) // primary text
    val SurfaceVariant = Color(0xFFEEEEEE)
    val OnSurfaceVariant = Color(0xFF757575) // secondary text
    val Outline = Color(0xFF757575)
    val OutlineVariant = Color(0xFFBDBDBD) // divider
    val SurfaceContainerLowest = Color(0xFFFFFFFF)
    val SurfaceContainerLow = Color(0xFFFAFAFA)
    val SurfaceContainer = Color(0xFFF5F5F5)
    val SurfaceContainerHigh = Color(0xFFF0F0F0)
    val SurfaceContainerHighest = Color(0xFFEEEEEE)
    val Error = Color(0xFFB3261E)
    val OnError = Color(0xFFFFFFFF)

    // Dark: same roles; primary and accent flip to lighter shades so they stay visible
    val PrimaryDark = Color(0xFFCE93D8)
    val OnPrimaryDark = Color(0xFF4A148C)
    val PrimaryContainerDark = Color(0xFFCE93D8) // light purple: the + button stands out
    val OnPrimaryContainerDark = Color(0xFF4A148C)
    val TopBarDark = Color(0xFF7B1FA2) // dark primary
    val OnTopBarDark = Color(0xFFFFFFFF)
    val SecondaryDark = Color(0xFFB39DFF)
    val OnSecondaryDark = Color(0xFF2A0F80)
    val SecondaryContainerDark = Color(0xFF4A2AB3)
    val OnSecondaryContainerDark = Color(0xFFEDE7FF)
    val BackgroundDark = Color(0xFF121212)
    val OnBackgroundDark = Color(0xFFECECEC)
    val SurfaceDark = Color(0xFF121212)
    val OnSurfaceDark = Color(0xFFECECEC)
    val SurfaceVariantDark = Color(0xFF2E2E2E)
    val OnSurfaceVariantDark = Color(0xFFB8B8B8)
    val OutlineDark = Color(0xFF8E8E8E)
    val OutlineVariantDark = Color(0xFF424242)
    val SurfaceContainerLowestDark = Color(0xFF0D0D0D)
    val SurfaceContainerLowDark = Color(0xFF1A1A1A)
    val SurfaceContainerDark = Color(0xFF1F1F1F)
    val SurfaceContainerHighDark = Color(0xFF262626)
    val SurfaceContainerHighestDark = Color(0xFF2E2E2E)

    // Avatar backgrounds (white initials on top), the same in light and dark
    val AvatarPalette = listOf(
        Color(0xFF1A73E8), // blue
        Color(0xFF188038), // green
        Color(0xFFC5221F), // red
        Color(0xFFE37400), // orange
        Color(0xFF9334E6), // purple
        Color(0xFF007B83), // teal
        Color(0xFFB80672), // pink
        Color(0xFF5F6368), // grey
    )
}
