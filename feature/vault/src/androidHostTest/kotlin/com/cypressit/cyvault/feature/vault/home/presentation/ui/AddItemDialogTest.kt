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

    private fun ComposeUiTest.show(card: Card?, isLoadingCards: Boolean = false) = setContent {
        CyTheme(darkTheme = false) {
            AddItemDialog(
                item = null,
                isSaving = false,
                cards = listOfNotNull(card),
                card = card,
                isLoadingCards = isLoadingCards,
                isCardMenuOpen = false,
                onCardMenuOpenChanged = { menuOpenChanges += it },
                onCardSelected = {},
                onDismiss = {},
                onSave = { _, _, _, _, _ -> },
                onDelete = {},
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
}
