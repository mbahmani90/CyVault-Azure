package com.cypressit.cyvault.systemdesign.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Makes the status bar icons readable on a coloured bar drawn behind the status bar: dark
 * icons on a light [barColor], light icons on a dark one. The previous icon colour comes back
 * when the screen leaves composition, so other screens keep the system's automatic choice.
 */
@Composable
fun StatusBarIconsFor(barColor: Color) {
    // Above this luminance, black contrasts more with the bar than white does (WCAG crossover).
    StatusBarIcons(darkIcons = barColor.luminance() > 0.179f)
}

@Composable
internal expect fun StatusBarIcons(darkIcons: Boolean)
