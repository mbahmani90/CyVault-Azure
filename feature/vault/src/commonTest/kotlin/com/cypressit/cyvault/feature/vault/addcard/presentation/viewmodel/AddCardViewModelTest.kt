package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.model.CardType
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository
import com.cypressit.cyvault.feature.vault.fake.FakeCardRepository
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCardsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain, advanceUntilIdle
class AddCardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

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

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: CardRepository = FakeCardRepository()) =
        AddCardViewModel(GetCardsUseCase(repository))

    private fun AddCardViewModel.enter(cardNumber: String, phoneNumber: String) {
        onIntent(AddCardIntent.CardNumberChanged(cardNumber))
        onIntent(AddCardIntent.PhoneNumberChanged(phoneNumber))
    }

    @Test
    fun init_loadsTheCards() = runTest(dispatcher) {
        val viewModel = viewModel(FakeCardRepository(cards = listOf(visa)))

        advanceUntilIdle()

        assertEquals(listOf(visa), viewModel.state.value.cards)
        assertFalse(viewModel.state.value.isCardsLoading)
        assertFalse(viewModel.state.value.isEmpty)
    }

    @Test
    fun init_withNoCards_isEmpty() = runTest(dispatcher) {
        val viewModel = viewModel()

        advanceUntilIdle()

        assertTrue(viewModel.state.value.isEmpty)
    }

    @Test
    fun init_whenLoadingFails_sendsCardsLoadFailed() = runTest(dispatcher) {
        val failing = object : CardRepository by FakeCardRepository() {
            override suspend fun getCards(): Result<List<Card>> = Result.failure(IllegalStateException())
        }
        val viewModel = viewModel(failing)

        advanceUntilIdle()

        assertEquals(AddCardEffect.CardsLoadFailed, viewModel.effect.first())
        assertFalse(viewModel.state.value.isCardsLoading)
    }

    @Test
    fun addCardClicked_showsTheDialog() {
        val viewModel = viewModel()

        viewModel.onIntent(AddCardIntent.AddCardClicked)

        assertTrue(viewModel.state.value.isAddCardDialogVisible)
    }

    @Test
    fun dialogDismissed_hidesTheDialogAndClearsTheFields() {
        val viewModel = viewModel()
        viewModel.onIntent(AddCardIntent.AddCardClicked)
        viewModel.enter(cardNumber = "4111", phoneNumber = "912")

        viewModel.onIntent(AddCardIntent.AddCardDialogDismissed)

        assertFalse(viewModel.state.value.isAddCardDialogVisible)
        assertEquals("", viewModel.state.value.cardNumber)
        assertEquals("", viewModel.state.value.phoneNumber)
    }

    @Test
    fun emptyForm_disablesAdd() {
        assertFalse(viewModel().state.value.isAddEnabled)
    }

    @Test
    fun sixteenCardDigitsAndNinePhoneDigits_enableAdd() {
        val viewModel = viewModel()

        viewModel.enter(cardNumber = "1234567812345678", phoneNumber = "912345678")

        assertTrue(viewModel.state.value.isAddEnabled)
    }

    @Test
    fun fifteenCardDigits_disableAdd() {
        val viewModel = viewModel()

        viewModel.enter(cardNumber = "123456781234567", phoneNumber = "912345678")

        assertFalse(viewModel.state.value.isCardNumberValid)
        assertFalse(viewModel.state.value.isAddEnabled)
    }

    @Test
    fun eightPhoneDigits_disableAdd() {
        val viewModel = viewModel()

        viewModel.enter(cardNumber = "1234567812345678", phoneNumber = "91234567")

        assertFalse(viewModel.state.value.isPhoneNumberValid)
        assertFalse(viewModel.state.value.isAddEnabled)
    }

    @Test
    fun validation_countsOnlyDigits() {
        val viewModel = viewModel()

        viewModel.enter(cardNumber = "1234 5678 1234 567", phoneNumber = "+3 9123 4567")

        assertFalse(viewModel.state.value.isCardNumberValid)
        assertTrue(viewModel.state.value.isPhoneNumberValid)
    }

    @Test
    fun typing_updatesBothFields() {
        val viewModel = viewModel()

        viewModel.enter(cardNumber = "4111", phoneNumber = "+351 912")

        assertEquals("4111", viewModel.state.value.cardNumber)
        assertEquals("+351 912", viewModel.state.value.phoneNumber)
    }
}
