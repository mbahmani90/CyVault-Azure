package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.compose.runtime.Composable

/** While [enabled], the system back gesture or button calls [onBack] instead of leaving the screen. */
@Composable
internal expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
