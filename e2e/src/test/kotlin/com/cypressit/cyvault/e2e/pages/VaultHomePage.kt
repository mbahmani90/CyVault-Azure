package com.cypressit.cyvault.e2e.pages

import com.cypressit.cyvault.testtags.VaultHomeTags
import io.appium.java_client.AppiumDriver
import org.openqa.selenium.WebElement

class VaultHomePage(driver: AppiumDriver) : BasePage(driver) {
    fun isShown(): Boolean = waitUntil { isShown(VaultHomeTags.ADD_ITEM_BUTTON) }

    fun tapAddItem(): AddItemDialogPage {
        find(VaultHomeTags.ADD_ITEM_BUTTON).click()
        return AddItemDialogPage(driver)
    }

    /** Waits for a row showing [title]; rows load from the backend. */
    fun isBalanceShown(title: String): Boolean = waitUntil { balanceRow(title) != null }

    fun isBalanceGone(title: String): Boolean = waitUntil { balanceRow(title) == null }

    /** A long press starts selection mode with this balance selected. */
    fun longPressBalance(title: String) = apply {
        val row = checkNotNull(balanceRow(title)) { "No balance \"$title\" on the home screen" }
        longPress(row)
    }

    fun isInSelectionMode(): Boolean = waitUntil { isShown(VaultHomeTags.SELECTION_DELETE_BUTTON) }

    fun tapDeleteSelected(): DeleteBalanceDialogPage {
        find(VaultHomeTags.SELECTION_DELETE_BUTTON).click()
        return DeleteBalanceDialogPage(driver)
    }

    /** Deletes the balance through the UI: select it, delete, then confirm. */
    fun deleteBalance(title: String): VaultHomePage = longPressBalance(title).tapDeleteSelected().tapConfirm()

    // The row's own text is empty: the title is on a text element inside it.
    private fun balanceRow(title: String): WebElement? =
        findAll(VaultHomeTags.BALANCE_ROW).firstOrNull { it.findElements(byText(title)).isNotEmpty() }
}
