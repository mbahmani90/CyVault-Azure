package com.cypressit.cyvault.feature.vault.card.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The same cases the API's CardFunctions accepts and rejects. */
class CardInputTest {

    @Test
    fun cardNumber_passingLuhn_isValid() {
        assertTrue(CardInput.isValidCardNumber("4111111111111111"))
        assertTrue(CardInput.isValidCardNumber("4111 1111 1111 1111"))
        assertTrue(CardInput.isValidCardNumber("5555-5555-5555-4444"))
    }

    @Test
    fun cardNumber_failingLuhn_isInvalid() {
        assertFalse(CardInput.isValidCardNumber("4111111111111112"))
        assertFalse(CardInput.isValidCardNumber("1234567812345678"))
    }

    @Test
    fun cardNumber_withTheWrongLength_isInvalid() {
        assertFalse(CardInput.isValidCardNumber("411111111111111"))
        assertFalse(CardInput.isValidCardNumber("41111111111111111111"))
        assertFalse(CardInput.isValidCardNumber(""))
    }

    @Test
    fun phoneNumber_portugueseMobile_getsThePrefix() {
        assertEquals("+351912345678", CardInput.normalizePhoneNumber("912345678"))
        assertEquals("+351912345678", CardInput.normalizePhoneNumber("912 345 678"))
    }

    @Test
    fun phoneNumber_international_isKept() {
        assertEquals("+351912345678", CardInput.normalizePhoneNumber("+351 912 345 678"))
        assertEquals("+351912345678", CardInput.normalizePhoneNumber("00351912345678"))
    }

    @Test
    fun phoneNumber_notMobileOrInternational_isInvalid() {
        assertNull(CardInput.normalizePhoneNumber("212345678"))
        assertNull(CardInput.normalizePhoneNumber("91234567"))
        assertNull(CardInput.normalizePhoneNumber(""))
    }
}
