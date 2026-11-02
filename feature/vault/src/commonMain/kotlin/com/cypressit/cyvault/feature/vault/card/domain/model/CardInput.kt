package com.cypressit.cyvault.feature.vault.card.domain.model

/**
 * The card and phone number rules the API applies (CardFunctions in azure-backend). The app checks
 * the same rules first, so Add is only enabled for input the API accepts.
 */
object CardInput {

    /** The longest bank name the API stores. */
    const val MAX_BANK_NAME_LENGTH = 50

    /** Required: not blank once trimmed, and at most [MAX_BANK_NAME_LENGTH] characters. */
    fun isValidBankName(value: String): Boolean = value.trim().length in 1..MAX_BANK_NAME_LENGTH

    private val cardNumberPattern = Regex("^[0-9]{16,19}$")
    private val portugueseMobilePattern = Regex("^9[0-9]{8}$")
    private val e164Pattern = Regex("^\\+[1-9][0-9]{7,14}$")

    /** 16 to 19 digits; spaces and dashes are ignored. There's no Luhn check, as on the API. */
    fun isValidCardNumber(value: String): Boolean = cardNumberPattern.matches(withoutSeparators(value))

    /**
     * The number in E.164, or null when it isn't valid. A 9-digit Portuguese mobile number
     * (starting with 9) gets the +351 prefix, and 00 is read as +.
     */
    fun normalizePhoneNumber(value: String): String? {
        var number = withoutSeparators(value)
        if (number.startsWith("00")) number = "+" + number.drop(2)
        if (portugueseMobilePattern.matches(number)) number = "+351$number"
        return number.takeIf { e164Pattern.matches(it) }
    }

    private fun withoutSeparators(value: String) = value.filterNot { it == ' ' || it == '-' }
}
