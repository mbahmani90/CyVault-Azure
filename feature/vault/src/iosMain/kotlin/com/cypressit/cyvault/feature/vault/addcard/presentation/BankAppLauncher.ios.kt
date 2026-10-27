package com.cypressit.cyvault.feature.vault.addcard.presentation

import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIApplication

actual class BankAppLauncher {

    actual fun openBankUrl(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        val safari = SFSafariViewController(nsUrl)
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(safari, animated = true, completion = null)
        return true
    }
}
