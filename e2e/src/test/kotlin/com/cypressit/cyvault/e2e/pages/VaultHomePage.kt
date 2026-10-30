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

    fun tapSelectAll() = apply { find(VaultHomeTags.SELECTION_SELECT_ALL_BUTTON).click() }

    /** Deletes the balance through the UI: select it, delete, then confirm. */
    fun deleteBalance(title: String): VaultHomePage = longPressBalance(title).tapDeleteSelected().tapConfirm()

    fun isEmpty(): Boolean = waitUntil { isShown(VaultHomeTags.BALANCES_EMPTY_TEXT) }

    /**
     * Deletes every balance through the UI: long press the first row, select all, delete, confirm.
     * Returns true once the list is empty.
     */
    fun deleteAllBalances(): Boolean {
        // Rows load from the backend: wait until they, or the empty text, show.
        waitUntil { findAll(VaultHomeTags.BALANCE_ROW).isNotEmpty() || isShown(VaultHomeTags.BALANCES_EMPTY_TEXT) }
        val firstRow = findAll(VaultHomeTags.BALANCE_ROW).firstOrNull() ?: return isEmpty()
        longPress(firstRow)
        tapSelectAll().tapDeleteSelected().tapConfirm()
        return isEmpty()
    }

    // The row's own text is empty: the title is on a text element inside it.
    private fun balanceRow(title: String): WebElement? =
        findAll(VaultHomeTags.BALANCE_ROW).firstOrNull { it.findElements(byText(title)).isNotEmpty() }
}
