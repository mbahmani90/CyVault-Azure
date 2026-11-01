package com.cypressit.cyvault.feature.vault.card.domain.model

/**
 * The card and phone number rules the API applies (CardFunctions in azure-backend). The app checks
 * the same rules first, so Add is only enabled for input the API accepts.
 */
object CardInput {

    private val cardNumberPattern = Regex("^[0-9]{16,19}$")
    private val portugueseMobilePattern = Regex("^9[0-9]{8}$")
    private val e164Pattern = Regex("^\\+[1-9][0-9]{7,14}$")

    /** 16 to 19 digits that pass the Luhn check; spaces and dashes are ignored. */
    fun isValidCardNumber(value: String): Boolean {
        val digits = withoutSeparators(value)
        if (!cardNumberPattern.matches(digits)) return false

        // Luhn: from the right, double every second digit (subtracting 9 above 9); the sum must end in 0.
        val sum = digits.reversed().withIndex().sumOf { (index, char) ->
            val digit = char - '0'
            if (index % 2 == 1) (digit * 2).let { if (it > 9) it - 9 else it } else digit
        }
        return sum % 10 == 0
    }

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
