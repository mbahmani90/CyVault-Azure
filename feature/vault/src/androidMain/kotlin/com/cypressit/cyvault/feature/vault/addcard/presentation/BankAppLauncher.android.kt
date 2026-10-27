package com.cypressit.cyvault.feature.vault.addcard.presentation

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

actual class BankAppLauncher(private val context: Context) {

    actual fun openBankUrl(url: String): Boolean {
        return try {
            val uri = url.toUri()
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, uri)
            true
        } catch (_: Exception) {
            false
        }
    }
}
