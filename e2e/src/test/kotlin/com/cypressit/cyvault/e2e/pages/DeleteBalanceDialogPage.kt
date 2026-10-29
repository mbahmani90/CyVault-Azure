package com.cypressit.cyvault.e2e.pages

import com.cypressit.cyvault.testtags.DeleteBalanceDialogTags
import io.appium.java_client.AppiumDriver

class DeleteBalanceDialogPage(driver: AppiumDriver) : BasePage(driver) {
    fun isShown(): Boolean = waitUntil { isShown(DeleteBalanceDialogTags.DIALOG) }

    fun tapConfirm(): VaultHomePage {
        find(DeleteBalanceDialogTags.CONFIRM_BUTTON).click()
        return VaultHomePage(driver)
    }

    fun tapCancel(): VaultHomePage {
        find(DeleteBalanceDialogTags.CANCEL_BUTTON).click()
        return VaultHomePage(driver)
    }
}
