package com.cypressit.cyvault.feature.vault.addcard.presentation.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardViewModel
import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository
import com.cypressit.cyvault.feature.vault.fake.FakeCardRepository
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCardsUseCase
import com.cypressit.cyvault.systemdesign.theme.CyTheme
import com.cypressit.cyvault.testtags.CardsTags
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The route with a real ViewModel: taps go through onIntent and come back as state. */
@OptIn(ExperimentalTestApi::class) // runComposeUiTest
@RunWith(RobolectricTestRunner::class)
class AddCardRouteTest {

    private fun ComposeUiTest.showRoute(repository: CardRepository) = setContent {
        CyTheme(darkTheme = false) {
            AddCardRoute(
                onNavigateBack = {},
                onCardSaved = {},
                viewModel = AddCardViewModel(GetCardsUseCase(repository)),
            )
        }
    }

    @Test
    fun addingACard_opensTheDialogAndEnablesAddForValidInput() = runComposeUiTest {
        showRoute(FakeCardRepository())

        onNodeWithTag(CardsTags.ADD_BUTTON).performClick()
        onNodeWithTag(CardsTags.CARD_NUMBER_FIELD).performTextInput("4111111111111111")
        onNodeWithTag(CardsTags.PHONE_NUMBER_FIELD).performTextInput("912345678")

        onNodeWithTag(CardsTags.SUBMIT_BUTTON).assertIsEnabled()
    }

    @Test
    fun cancel_closesTheDialog() = runComposeUiTest {
        showRoute(FakeCardRepository())

        onNodeWithTag(CardsTags.ADD_BUTTON).performClick()
        onNodeWithTag(CardsTags.CANCEL_BUTTON).performClick()

        onNodeWithTag(CardsTags.ADD_CARD_DIALOG).assertDoesNotExist()
    }

    @Test
    fun loadFailure_showsASnackbar() = runComposeUiTest {
        val failing = object : CardRepository by FakeCardRepository() {
            override suspend fun getCards(): Result<List<Card>> = Result.failure(IllegalStateException())
        }
        showRoute(failing)

        val message = "Couldn't load your cards"
        waitUntil {
            onAllNodesWithText(message, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(message, substring = true).assertIsDisplayed()
    }
}
