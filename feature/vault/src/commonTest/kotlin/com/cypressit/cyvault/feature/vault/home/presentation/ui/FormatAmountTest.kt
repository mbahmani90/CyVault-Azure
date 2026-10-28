package com.cypressit.cyvault.feature.vault.home.presentation.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatAmountTest {

    @Test
    fun negative_hasAMinusBeforeTheSymbol() {
        assertEquals("−€ 25.69", formatAmount(-25.69, "EUR"))
    }

    @Test
    fun positive_hasAPlusOnlyWhenAsked() {
        assertEquals("£ 5.00", formatAmount(5.0, "GBP"))
        assertEquals("+$ 5.00", formatAmount(5.0, "USD", plusSign = true))
    }

    @Test
    fun symbolAfter_putsTheSymbolAfterTheNumber() {
        assertEquals("−31.67 €", formatAmount(-31.67, "EUR", symbolAfter = true))
        assertEquals("0.00 €", formatAmount(0.0, "EUR", symbolAfter = true))
    }

    @Test
    fun sumsRoundToTheNearestCent() {
        // 25.69 + 5.98 is 31.669999… as a Double.
        assertEquals("−€ 31.67", formatAmount(-25.69 + -5.98, "EUR"))
    }
}
