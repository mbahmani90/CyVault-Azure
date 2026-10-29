package com.cypressit.cyvault.systemdesign.components

import androidx.compose.ui.Modifier

/**
 * Lets UI automation outside the app (Appium) find elements by their Modifier.testTag.
 * Apply it at the root of every compose window: the app's content and each dialog,
 * because a dialog is a window of its own and doesn't inherit it.
 */
expect fun Modifier.exposeTestTags(): Modifier
