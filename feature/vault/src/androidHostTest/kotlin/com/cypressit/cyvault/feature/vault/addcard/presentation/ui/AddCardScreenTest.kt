package com.cypressit.cyvault.feature.vault.addcard.presentation.ui

import androidx.compose.material3.SnackbarHostState
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
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardIntent
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardState
import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.model.CardType
import com.cypressit.cyvault.systemdesign.theme.CyTheme
import com.cypressit.cyvault.testtags.CardsTags
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** The stateless Cards screen: what each state shows, and the intent each tap or keystroke sends. */
@OptIn(ExperimentalTestApi::class) // runComposeUiTest
@RunWith(RobolectricTestRunner::class)
class AddCardScreenTest {

    private val visa = Card(
        id = "visa",
        bankName = "Millennium",
        cardHolderName = "Maria",
        cardNumber = "4111111111111111",
        cvv2 = "123",
        expiryDate = "12/30",
        balance = 100.0,
        cardType = CardType.entries.first(),
    )

    private val intents = mutableListOf<AddCardIntent>()

    private fun ComposeUiTest.show(state: AddCardState) = setContent {
        CyTheme(darkTheme = false) {
            AddCardScreen(
                state = state,
                onIntent = { intents += it },
                onNavigateBack = {},
                snackbarHostState = SnackbarHostState(),
            )
        }
    }

    @Test
    fun loading_showsTheProgressIndicator() = runComposeUiTest {
        show(AddCardState(isCardsLoading = true))

        onNodeWithTag(CardsTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun noCards_showsTheEmptyText() = runComposeUiTest {
        show(AddCardState())

        onNodeWithTag(CardsTags.EMPTY_TEXT).assertIsDisplayed()
    }

    @Test
    fun cards_showBankAndLastFourDigits() = runComposeUiTest {
        show(AddCardState(cards = listOf(visa)))

        onNodeWithTag(CardsTags.LIST).assertIsDisplayed()
        onNodeWithText("Millennium").assertIsDisplayed()
        onNodeWithText("1111", substring = true).assertIsDisplayed()
    }

    @Test
    fun addButton_sendsAddCardClicked() = runComposeUiTest {
        show(AddCardState())

        onNodeWithTag(CardsTags.ADD_BUTTON).performClick()

        assertEquals(listOf<AddCardIntent>(AddCardIntent.AddCardClicked), intents)
    }

    @Test
    fun dialog_isHiddenUntilRequested() = runComposeUiTest {
        show(AddCardState())

        onNodeWithTag(CardsTags.ADD_CARD_DIALOG).assertDoesNotExist()
    }

    @Test
    fun dialog_withInvalidFields_disablesAdd() = runComposeUiTest {
        show(AddCardState(isAddCardDialogVisible = true, cardNumber = "4111", phoneNumber = "912"))

        onNodeWithTag(CardsTags.ADD_CARD_DIALOG).assertIsDisplayed()
        onNodeWithTag(CardsTags.SUBMIT_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun dialog_withValidFields_enablesAdd() = runComposeUiTest {
        show(
            AddCardState(
                isAddCardDialogVisible = true,
                cardNumber = "4111111111111111",
                phoneNumber = "912345678",
            ),
        )

        onNodeWithTag(CardsTags.SUBMIT_BUTTON).assertIsEnabled()
    }

    @Test
    fun typingACardNumber_sendsCardNumberChanged() = runComposeUiTest {
        show(AddCardState(isAddCardDialogVisible = true))

        onNodeWithTag(CardsTags.CARD_NUMBER_FIELD).performTextInput("4111")

        assertEquals(listOf<AddCardIntent>(AddCardIntent.CardNumberChanged("4111")), intents)
    }

    @Test
    fun typingAPhoneNumber_sendsPhoneNumberChanged() = runComposeUiTest {
        show(AddCardState(isAddCardDialogVisible = true))

        onNodeWithTag(CardsTags.PHONE_NUMBER_FIELD).performTextInput("912")

        assertEquals(listOf<AddCardIntent>(AddCardIntent.PhoneNumberChanged("912")), intents)
    }

    @Test
    fun cancel_sendsAddCardDialogDismissed() = runComposeUiTest {
        show(AddCardState(isAddCardDialogVisible = true))

        onNodeWithTag(CardsTags.CANCEL_BUTTON).performClick()

        assertEquals(listOf<AddCardIntent>(AddCardIntent.AddCardDialogDismissed), intents)
    }
}
