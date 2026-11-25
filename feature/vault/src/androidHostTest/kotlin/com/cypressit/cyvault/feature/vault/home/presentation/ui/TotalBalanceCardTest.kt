package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeState
import com.cypressit.cyvault.systemdesign.theme.CyTheme
import com.cypressit.cyvault.testtags.VaultHomeTags
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** The total card: a loading bar until the total is ready, then one line per currency. */
@OptIn(ExperimentalTestApi::class) // runComposeUiTest
@RunWith(RobolectricTestRunner::class)
class TotalBalanceCardTest {

    private val loaded = VaultHomeState(isLoadingBalances = false, isLoadingTotals = false)

    private var transferClicks = 0
    private var addClicks = 0

    private fun ComposeUiTest.show(state: VaultHomeState) = setContent {
        CyTheme(darkTheme = false) {
            TotalBalanceCard(
                state = state,
                onTransfer = { transferClicks++ },
                onAddItem = { addClicks++ },
            )
        }
    }

    @Test
    fun whileLoading_showsTheBarInsteadOfTheTotal() = runComposeUiTest {
        show(VaultHomeState(balanceTotals = listOf("EUR" to 403.28)))

        onNodeWithTag(VaultHomeTags.TOTAL_LOADING).assertIsDisplayed()
        onNodeWithText("403.28", substring = true).assertDoesNotExist()
    }

    @Test
    fun whenLoaded_showsOneLinePerCurrencyAndTheOldestDate() = runComposeUiTest {
        show(
            loaded.copy(
                balanceTotals = listOf("EUR" to 403.28, "USD" to 12.5),
                oldestBalanceDate = "2026-01-02T00:00:00Z",
            ),
        )

        onNodeWithTag(VaultHomeTags.TOTAL_LOADING).assertDoesNotExist()
        onNodeWithText("403.28", substring = true).assertIsDisplayed()
        onNodeWithText("12.50", substring = true).assertIsDisplayed()
        onNodeWithText("From", substring = true).assertIsDisplayed()
    }

    @Test
    fun withNoBalances_showsZero() = runComposeUiTest {
        show(loaded)

        onNodeWithText("0.00", substring = true).assertIsDisplayed()
        onNodeWithText("From", substring = true).assertDoesNotExist()
    }

    @Test
    fun buttonsWorkWhileTheTotalLoads() = runComposeUiTest {
        show(VaultHomeState())

        onNodeWithTag(VaultHomeTags.TRANSFER_BUTTON).performClick()
        onNodeWithTag(VaultHomeTags.ADD_ITEM_BUTTON).performClick()

        assertEquals(1, transferClicks)
        assertEquals(1, addClicks)
    }
}
