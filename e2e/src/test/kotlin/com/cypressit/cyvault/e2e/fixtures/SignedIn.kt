package com.cypressit.cyvault.e2e.fixtures

import com.cypressit.cyvault.e2e.config.E2eConfig
import com.cypressit.cyvault.e2e.pages.LoginPage
import com.cypressit.cyvault.e2e.pages.VaultHomePage
import kotlin.random.Random

/**
 * Fixture: signs in with the test account and returns the home screen. After the test, every
 * balance on the account is deleted (select all, delete), so each test leaves it empty.
 */
fun AppiumSession.signedIn(): VaultHomePage {
    val home = LoginPage(driver).signIn(E2eConfig.email, E2eConfig.password)
    cleanup { deleteAllBalances() }
    return home
}

/**
 * Fixture: like [signedIn], with one card added first, because a balance needs a card. After the
 * test, the balances are deleted first and then every card, so the account is left empty.
 */
fun AppiumSession.signedInWithCard(): VaultHomePage {
    val home = LoginPage(driver).signIn(E2eConfig.email, E2eConfig.password)
    // Cleanups run newest first: this one runs after the balances are gone.
    cleanup { check(VaultHomePage(driver).openCards().deleteAllCards()) { "Cards left on the test account" } }
    cleanup { deleteAllBalances() }

    check(home.isShown()) { "Home screen did not appear after sign-in" }
    val cards = home.openCards()
    check(cards.isShown()) { "Cards screen did not open" }
    val dialog = cards.tapAdd()
    dialog.enterCardNumber(uniqueCardNumber()).enterPhoneNumber(uniquePhoneNumber()).enterBankName(TEST_BANK).tapAdd()
    check(dialog.isClosed()) { "Add Card dialog did not close after Add" }
    check(cards.hasCards()) { "The new card is not on the Cards screen" }
    return cards.goBack()
}

private fun AppiumSession.deleteAllBalances() {
    check(VaultHomePage(driver).deleteAllBalances()) { "Balances left on the test account" }
}

private const val TEST_BANK = "E2E Bank"

// The API lets a phone number belong to only one card across all users, so each run makes its own
// numbers; a fixed one fails once any account has it. Valid for the app and API, not real.

/** 16 digits: 4 followed by 15 random ones. */
private fun uniqueCardNumber(): String = "4" + randomDigits(15)

/** A Portuguese mobile number: 9 followed by 8 random digits. */
private fun uniquePhoneNumber(): String = "9" + randomDigits(8)

private fun randomDigits(count: Int): String = (1..count).joinToString("") { Random.nextInt(10).toString() }
