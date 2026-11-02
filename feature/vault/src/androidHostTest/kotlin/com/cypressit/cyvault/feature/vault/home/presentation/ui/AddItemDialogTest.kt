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
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceType
import com.cypressit.cyvault.systemdesign.theme.CyTheme
import com.cypressit.cyvault.testtags.AddItemDialogTags
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** The add-item dialog's card field: what it shows, and that Save needs a card. */
@OptIn(ExperimentalTestApi::class) // runComposeUiTest
@RunWith(RobolectricTestRunner::class)
class AddItemDialogTest {

    private val visa = Card("visa", "1111", "Visa", "CGD", "+351912345678", "")

    private val menuOpenChanges = mutableListOf<Boolean>()

    private var deleteClicks = 0

    private fun ComposeUiTest.show(card: Card?, isLoadingCards: Boolean = false, item: Balance? = null) = setContent {
        CyTheme(darkTheme = false) {
            AddItemDialog(
                item = item,
                isSaving = false,
                cards = listOfNotNull(card),
                card = card,
                isLoadingCards = isLoadingCards,
                isCardMenuOpen = false,
                onCardMenuOpenChanged = { menuOpenChanges += it },
                onCardSelected = {},
                onDismiss = {},
                onSave = { _, _, _, _, _ -> },
                onDelete = { deleteClicks++ },
            )
        }
    }

    private fun ComposeUiTest.fillTitleAndValue() {
        onNodeWithTag(AddItemDialogTags.TITLE_FIELD).performTextInput("Lidl")
        onNodeWithTag(AddItemDialogTags.VALUE_FIELD).performTextInput("12.50")
    }

    @Test
    fun showsTheChosenCard() = runComposeUiTest {
        show(card = visa)

        onNodeWithText("CGD · Visa **** 1111").assertIsDisplayed()
    }

    @Test
    fun withACard_saveIsEnabled() = runComposeUiTest {
        show(card = visa)
        fillTitleAndValue()

        onNodeWithTag(AddItemDialogTags.SAVE_BUTTON).assertIsEnabled()
    }

    @Test
    fun withoutACard_saveIsDisabled() = runComposeUiTest {
        show(card = null)
        fillTitleAndValue()

        onNodeWithText("No cards yet: add one in Cards").assertIsDisplayed()
        onNodeWithTag(AddItemDialogTags.SAVE_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun whileLoading_showsLoading() = runComposeUiTest {
        show(card = null, isLoadingCards = true)

        onNodeWithText("Loading your cards…").assertIsDisplayed()
    }

    @Test
    fun tappingTheCardField_asksToOpenTheMenu() = runComposeUiTest {
        show(card = visa)

        onNodeWithTag(AddItemDialogTags.CARD_FIELD).performClick()

        assertEquals(listOf(true), menuOpenChanges)
    }

    @Test
    fun aTransfer_showsReadOnlyWithDelete() = runComposeUiTest {
        val transfer = Balance(
            id = "t1-sent",
            name = "",
            amount = 20.0,
            currency = "EUR",
            cardId = "visa",
            type = BalanceType.Transfer,
            counterparty = "+351912345678",
        )
        show(card = visa, item = transfer)

        onNodeWithText("Sent to +351912345678").assertIsDisplayed()
        onNodeWithText("20.00 €").assertIsDisplayed()
        onNodeWithTag(AddItemDialogTags.TITLE_FIELD).assertDoesNotExist()
        onNodeWithTag(AddItemDialogTags.SAVE_BUTTON).assertDoesNotExist()
        onNodeWithText("Delete").performClick()
        assertEquals(1, deleteClicks)
    }
}
