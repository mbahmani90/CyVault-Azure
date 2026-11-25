package com.cypressit.cyvault.e2e.pages

import com.cypressit.cyvault.testtags.CardsTags
import io.appium.java_client.AppiumDriver

class AddCardDialogPage(driver: AppiumDriver) : BasePage(driver) {
    fun isShown(): Boolean = waitUntil { isShown(CardsTags.ADD_CARD_DIALOG) }

    fun isClosed(): Boolean = waitUntil { !isShown(CardsTags.ADD_CARD_DIALOG) }

    fun enterCardNumber(number: String) = apply { find(CardsTags.CARD_NUMBER_FIELD).sendKeys(number) }

    fun enterPhoneNumber(number: String) = apply { find(CardsTags.PHONE_NUMBER_FIELD).sendKeys(number) }

    fun enterBankName(name: String) = apply { find(CardsTags.BANK_NAME_FIELD).sendKeys(name) }

    /** Add is enabled once every field is valid. */
    fun tapAdd(): CardsPage {
        check(waitUntil { find(CardsTags.SUBMIT_BUTTON).isEnabled }) { "Add stayed disabled" }
        find(CardsTags.SUBMIT_BUTTON).click()
        return CardsPage(driver)
    }
}
