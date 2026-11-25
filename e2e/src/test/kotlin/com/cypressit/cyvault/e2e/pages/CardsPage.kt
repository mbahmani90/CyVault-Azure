package com.cypressit.cyvault.e2e.pages

import com.cypressit.cyvault.testtags.CardsTags
import io.appium.java_client.AppiumDriver

class CardsPage(driver: AppiumDriver) : BasePage(driver) {
    fun isShown(): Boolean = waitUntil { isShown(CardsTags.ADD_BUTTON) }

    fun tapAdd(): AddCardDialogPage {
        find(CardsTags.ADD_BUTTON).click()
        return AddCardDialogPage(driver)
    }

    /** Waits for a card row; cards load from the backend. */
    fun hasCards(): Boolean = waitUntil { findAll(CardsTags.CARD_ROW).isNotEmpty() }

    fun isEmpty(): Boolean = waitUntil { isShown(CardsTags.EMPTY_TEXT) }

    /**
     * Deletes every card through the UI: long press the first row, select all, delete, confirm.
     * Returns true once the list is empty.
     */
    fun deleteAllCards(): Boolean {
        // Rows load from the backend: wait until they, or the empty text, show.
        waitUntil { findAll(CardsTags.CARD_ROW).isNotEmpty() || isShown(CardsTags.EMPTY_TEXT) }
        val firstRow = findAll(CardsTags.CARD_ROW).firstOrNull() ?: return isEmpty()
        longPress(firstRow)
        find(CardsTags.SELECTION_SELECT_ALL_BUTTON).click()
        find(CardsTags.SELECTION_DELETE_BUTTON).click()
        find(CardsTags.DELETE_CONFIRM_BUTTON).click()
        return isEmpty()
    }

    /** The system back button: the Cards screen goes back to the home screen. */
    fun goBack(): VaultHomePage {
        driver.navigate().back()
        return VaultHomePage(driver)
    }
}
