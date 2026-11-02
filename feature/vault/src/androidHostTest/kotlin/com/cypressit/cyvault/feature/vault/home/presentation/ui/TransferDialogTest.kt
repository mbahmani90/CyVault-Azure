package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeIntent
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeState
import com.cypressit.cyvault.systemdesign.theme.CyTheme
import com.cypressit.cyvault.testtags.TransferDialogTags
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** The transfer dialog: what each state shows, and the intent each tap or keystroke sends. */
@OptIn(ExperimentalTestApi::class) // runComposeUiTest
@RunWith(RobolectricTestRunner::class)
class TransferDialogTest {

    private val visa = Card(
        id = "visa",
        last4 = "1111",
        brand = "Visa",
        bankName = "CGD",
        phoneNumber = "+351912345678",
        createdAt = "",
    )

    private val opened = VaultHomeState(
        isTransferDialogVisible = true,
        transferCards = listOf(visa),
        transferCardId = "visa",
    )

    private val intents = mutableListOf<VaultHomeIntent>()

    private fun ComposeUiTest.show(state: VaultHomeState) = setContent {
        CyTheme(darkTheme = false) {
            TransferDialog(state = state, onIntent = { intents += it })
        }
    }

    @Test
    fun showsTheChosenCard() = runComposeUiTest {
        show(opened)

        onNodeWithTag(TransferDialogTags.DIALOG).assertIsDisplayed()
        onNodeWithText("CGD · Visa **** 1111").assertIsDisplayed()
    }

    @Test
    fun typingAPhoneNumber_sendsTransferPhoneNumberChanged() = runComposeUiTest {
        show(opened)

        onNodeWithTag(TransferDialogTags.PHONE_NUMBER_FIELD).performTextInput("912")

        assertEquals(listOf<VaultHomeIntent>(VaultHomeIntent.TransferPhoneNumberChanged("912")), intents)
    }

    @Test
    fun typingAnAmount_sendsTransferAmountChanged() = runComposeUiTest {
        show(opened)

        onNodeWithTag(TransferDialogTags.AMOUNT_FIELD).performTextInput("10")

        assertEquals(listOf<VaultHomeIntent>(VaultHomeIntent.TransferAmountChanged("10")), intents)
    }

    @Test
    fun currencyButton_opensItsMenu() = runComposeUiTest {
        show(opened)

        onNodeWithTag(TransferDialogTags.CURRENCY_BUTTON).performClick()

        assertEquals(listOf<VaultHomeIntent>(VaultHomeIntent.TransferCurrencyMenuOpenChanged(true)), intents)
    }

    @Test
    fun send_isDisabledUntilTheFormIsValid() = runComposeUiTest {
        show(opened)

        onNodeWithTag(TransferDialogTags.SEND_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun send_isEnabledForAValidForm() = runComposeUiTest {
        show(opened.copy(transferPhoneNumber = "912345678", transferAmount = "10"))

        onNodeWithTag(TransferDialogTags.SEND_BUTTON).assertIsEnabled()
    }

    @Test
    fun cancel_sendsTransferDismissed() = runComposeUiTest {
        show(opened)

        onNodeWithTag(TransferDialogTags.CANCEL_BUTTON).performClick()

        assertEquals(listOf<VaultHomeIntent>(VaultHomeIntent.TransferDismissed), intents)
    }
}
