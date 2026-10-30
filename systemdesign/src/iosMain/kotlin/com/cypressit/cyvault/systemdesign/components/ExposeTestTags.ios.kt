package com.cypressit.cyvault.systemdesign.components

import androidx.compose.ui.Modifier

// iOS already exposes test tags as accessibility identifiers.
actual fun Modifier.exposeTestTags(): Modifier = this
