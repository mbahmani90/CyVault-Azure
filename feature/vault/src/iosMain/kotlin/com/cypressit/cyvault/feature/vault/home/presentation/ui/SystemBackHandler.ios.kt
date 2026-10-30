package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.compose.runtime.Composable

// iOS has no system back button; the selection bar's close button leaves selection mode.
@Composable
internal actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
