package com.cypressit.cyvault.e2e.pages

import com.cypressit.cyvault.testtags.AddItemDialogTags
import io.appium.java_client.AppiumDriver

class AddItemDialogPage(driver: AppiumDriver) : BasePage(driver) {
    fun isShown(): Boolean = waitUntil { isShown(AddItemDialogTags.DIALOG) }

    fun isClosed(): Boolean = waitUntil { !isShown(AddItemDialogTags.DIALOG) }

    fun enterTitle(title: String) = apply { find(AddItemDialogTags.TITLE_FIELD).sendKeys(title) }

    fun enterDescription(description: String) =
        apply { find(AddItemDialogTags.DESCRIPTION_FIELD).sendKeys(description) }

    fun enterValue(value: String) = apply { find(AddItemDialogTags.VALUE_FIELD).sendKeys(value) }

    fun tapSave(): VaultHomePage {
        find(AddItemDialogTags.SAVE_BUTTON).click()
        return VaultHomePage(driver)
    }

    fun tapCancel(): VaultHomePage {
        find(AddItemDialogTags.CANCEL_BUTTON).click()
        return VaultHomePage(driver)
    }
}
