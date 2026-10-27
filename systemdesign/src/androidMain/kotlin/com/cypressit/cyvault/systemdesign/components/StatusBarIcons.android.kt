package com.cypressit.cyvault.systemdesign.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun StatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(darkIcons) {
        val window = view.context.findActivity()?.window
        val previous = window?.hasDarkStatusBarIcons()
        window?.setDarkStatusBarIcons(darkIcons)
        onDispose {
            if (window != null && previous != null) window.setDarkStatusBarIcons(previous)
        }
    }
}

// Platform APIs instead of androidx.core's WindowCompat, which this module doesn't depend on.
private fun Window.hasDarkStatusBarIcons(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val flag = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        insetsController?.systemBarsAppearance?.and(flag) == flag
    } else {
        @Suppress("DEPRECATION")
        decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0
    }

private fun Window.setDarkStatusBarIcons(dark: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val flag = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        insetsController?.setSystemBarsAppearance(if (dark) flag else 0, flag)
    } else {
        @Suppress("DEPRECATION")
        decorView.systemUiVisibility = if (dark) {
            decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
