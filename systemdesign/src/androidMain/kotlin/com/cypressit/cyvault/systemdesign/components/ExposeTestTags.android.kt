package com.cypressit.cyvault.systemdesign.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

// Android shows test tags to UI Automator only as resource IDs, and only when asked to.
@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.exposeTestTags(): Modifier = semantics { testTagsAsResourceId = true }
