package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.CreateCardUseCase
import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.DeleteCardsUseCase
import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository
import com.cypressit.cyvault.feature.vault.card.domain.repository.InvalidCardException
import com.cypressit.cyvault.feature.vault.card.domain.repository.PhoneNumberAlreadyLinkedException
import com.cypressit.cyvault.feature.vault.fake.FakeCardRepository
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCardsUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ObserveCardsUseCase
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
        last4 = "1111",
        brand = "Visa",
        bankName = "Millennium",
        phoneNumber = "+351912345678",
        createdAt = "2026-11-01T09:30:00+00:00",
    )

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: CardRepository = FakeCardRepository()) =
        AddCardViewModel(
            GetCardsUseCase(repository),
            ObserveCardsUseCase(repository),
            CreateCardUseCase(repository),
            DeleteCardsUseCase(repository),
        )

    // The bank name is required, so a valid form has one unless a test says otherwise.
    private fun AddCardViewModel.enter(cardNumber: String, phoneNumber: String, bankName: String = "CGD") {
        onIntent(AddCardIntent.CardNumberChanged(cardNumber))
        onIntent(AddCardIntent.PhoneNumberChanged(phoneNumber))
        onIntent(AddCardIntent.BankNameChanged(bankName))
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
    fun init_showsTheCardsAlreadyFetchedWithoutASpinner() = runTest(dispatcher) {
        val repository = FakeCardRepository(cards = listOf(visa))
        // As Home would have, before the Cards screen opens.
        repository.getCards()

        val viewModel = viewModel(repository)
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(visa), viewModel.state.value.cards)
        assertFalse(viewModel.state.value.isCardsLoading)
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
    fun aValidCardAndPhoneNumber_enableAdd() {
        val viewModel = viewModel()

        viewModel.enter(cardNumber = "4111111111111111", phoneNumber = "912345678")

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

        viewModel.enter(cardNumber = "4111111111111111", phoneNumber = "91234567")

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

    @Test
    fun submit_savesTheCardClosesTheDialogAndAddsItToTheList() = runTest(dispatcher) {
        val repository = FakeCardRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onIntent(AddCardIntent.AddCardClicked)
        viewModel.enter(cardNumber = "4111111111111111", phoneNumber = "912345678")

        viewModel.onIntent(AddCardIntent.AddCardSubmitted)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("1111"), state.cards.map { it.last4 })
        assertFalse(state.isAddCardDialogVisible)
        assertFalse(state.isSavingCard)
        assertEquals("", state.cardNumber)
        assertEquals(AddCardEffect.CardAdded, viewModel.effect.first())
    }

    @Test
    fun submit_withInvalidFields_doesNothing() = runTest(dispatcher) {
        val repository = FakeCardRepository()
        val viewModel = viewModel(repository)
        viewModel.enter(cardNumber = "4111", phoneNumber = "912345678")

        viewModel.onIntent(AddCardIntent.AddCardSubmitted)
        advanceUntilIdle()

        assertTrue(repository.createdIds.isEmpty())
    }

    @Test
    fun submit_whenThePhoneIsLinked_keepsTheDialogAndSaysSo() = runTest(dispatcher) {
        val repository = FakeCardRepository().apply { createFailure = PhoneNumberAlreadyLinkedException() }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onIntent(AddCardIntent.AddCardClicked)
        viewModel.enter(cardNumber = "4111111111111111", phoneNumber = "912345678")

        viewModel.onIntent(AddCardIntent.AddCardSubmitted)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isAddCardDialogVisible)
        assertFalse(viewModel.state.value.isSavingCard)
        assertEquals(AddCardSaveError.PhoneNumberAlreadyLinked, viewModel.state.value.saveError)
    }

    @Test
    fun submit_whenSavingFails_showsFailed() = runTest(dispatcher) {
        val repository = FakeCardRepository().apply { createFailure = IllegalStateException() }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.enter(cardNumber = "4111111111111111", phoneNumber = "912345678")

        viewModel.onIntent(AddCardIntent.AddCardSubmitted)
        advanceUntilIdle()

        assertEquals(AddCardSaveError.Failed, viewModel.state.value.saveError)
    }

    @Test
    fun submit_whenTheApiRejectsTheInput_showsInvalidCard() = runTest(dispatcher) {
        val repository = FakeCardRepository().apply { createFailure = InvalidCardException() }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.enter(cardNumber = "4111111111111111", phoneNumber = "912345678")

        viewModel.onIntent(AddCardIntent.AddCardSubmitted)
        advanceUntilIdle()

        assertEquals(AddCardSaveError.InvalidCard, viewModel.state.value.saveError)
    }

    @Test
    fun editingAField_clearsTheSaveError() = runTest(dispatcher) {
        val repository = FakeCardRepository().apply { createFailure = PhoneNumberAlreadyLinkedException() }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.enter(cardNumber = "4111111111111111", phoneNumber = "912345678")
        viewModel.onIntent(AddCardIntent.AddCardSubmitted)
        advanceUntilIdle()

        viewModel.onIntent(AddCardIntent.PhoneNumberChanged("912345679"))

        assertEquals(null, viewModel.state.value.saveError)
    }

    @Test
    fun anyCardNumberWith16Digits_enablesAdd() {
        val viewModel = viewModel()

        viewModel.enter(cardNumber = "4444111155559999", phoneNumber = "958476321")

        assertTrue(viewModel.state.value.isAddEnabled)
    }

    @Test
    fun retryAfterAFailure_reusesTheSameCardId() = runTest(dispatcher) {
        val repository = FakeCardRepository().apply { createFailure = IllegalStateException() }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onIntent(AddCardIntent.AddCardClicked)
        viewModel.enter(cardNumber = "4111111111111111", phoneNumber = "912345678")

        viewModel.onIntent(AddCardIntent.AddCardSubmitted)
        advanceUntilIdle()
        repository.createFailure = null
        viewModel.onIntent(AddCardIntent.AddCardSubmitted)
        advanceUntilIdle()

        assertEquals(2, repository.createdIds.size)
        assertEquals(repository.createdIds[0], repository.createdIds[1])
    }

    @Test
    fun aNewDialogAfterASave_usesANewCardId() = runTest(dispatcher) {
        val repository = FakeCardRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        repeat(2) {
            viewModel.onIntent(AddCardIntent.AddCardClicked)
            viewModel.enter(cardNumber = "4111111111111111", phoneNumber = "91234567$it")
            viewModel.onIntent(AddCardIntent.AddCardSubmitted)
            advanceUntilIdle()
        }

        assertEquals(2, repository.createdIds.toSet().size)
    }

    @Test
    fun submit_sendsTheTrimmedBankName() = runTest(dispatcher) {
        val repository = FakeCardRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.enter(cardNumber = "4111111111111111", phoneNumber = "912345678")
        viewModel.onIntent(AddCardIntent.BankNameChanged("  Millennium  "))

        viewModel.onIntent(AddCardIntent.AddCardSubmitted)
        advanceUntilIdle()

        assertEquals(listOf("Millennium"), repository.createdBankNames)
        assertEquals("", viewModel.state.value.bankName)
    }

    @Test
    fun bankName_isRequired() {
        val viewModel = viewModel()

        viewModel.enter(cardNumber = "4111111111111111", phoneNumber = "912345678", bankName = "  ")

        assertFalse(viewModel.state.value.isBankNameValid)
        assertFalse(viewModel.state.value.isAddEnabled)
    }

    @Test
    fun bankName_isCappedAtTheApiLimit() {
        val viewModel = viewModel()

        viewModel.onIntent(AddCardIntent.BankNameChanged("x".repeat(60)))

        assertEquals(50, viewModel.state.value.bankName.length)
    }

    private val mastercard = visa.copy(id = "mastercard", last4 = "4444", brand = "Mastercard")

    private suspend fun kotlinx.coroutines.test.TestScope.loaded(repository: FakeCardRepository): AddCardViewModel {
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun clickingACard_showsItsDetails() = runTest(dispatcher) {
        val viewModel = loaded(FakeCardRepository(cards = listOf(visa)))

        viewModel.onIntent(AddCardIntent.ClickCard(visa))

        assertEquals(visa, viewModel.state.value.viewingCard)
    }

    @Test
    fun deleteFromDetails_asksFirstThenDeletesTheCard() = runTest(dispatcher) {
        val repository = FakeCardRepository(cards = listOf(visa, mastercard))
        val viewModel = loaded(repository)
        viewModel.onIntent(AddCardIntent.ClickCard(visa))

        viewModel.onIntent(AddCardIntent.RequestDeleteViewingCard)

        assertEquals(null, viewModel.state.value.viewingCard)
        assertEquals(visa, viewModel.state.value.pendingDeleteCard)
        assertTrue(repository.deletedIds.isEmpty())

        viewModel.onIntent(AddCardIntent.ConfirmDeleteCard)
        advanceUntilIdle()

        assertEquals(listOf("visa"), repository.deletedIds)
        assertEquals(listOf(mastercard), viewModel.state.value.cards)
        assertEquals(AddCardEffect.CardsDeleted(1), viewModel.effect.first())
    }

    @Test
    fun dismissingTheDeleteQuestion_keepsTheCard() = runTest(dispatcher) {
        val repository = FakeCardRepository(cards = listOf(visa))
        val viewModel = loaded(repository)
        viewModel.onIntent(AddCardIntent.ClickCard(visa))
        viewModel.onIntent(AddCardIntent.RequestDeleteViewingCard)

        viewModel.onIntent(AddCardIntent.DismissDeleteCard)
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.pendingDeleteCard)
        assertTrue(repository.deletedIds.isEmpty())
        assertEquals(listOf(visa), viewModel.state.value.cards)
    }

    @Test
    fun longPress_startsSelectionAndClicksToggle() = runTest(dispatcher) {
        val viewModel = loaded(FakeCardRepository(cards = listOf(visa, mastercard)))

        viewModel.onIntent(AddCardIntent.LongClickCard(visa))
        assertTrue(viewModel.state.value.isSelectionMode)

        viewModel.onIntent(AddCardIntent.ClickCard(mastercard))
        assertEquals(setOf("visa", "mastercard"), viewModel.state.value.selectedCardIds)
        assertEquals(null, viewModel.state.value.viewingCard)

        viewModel.onIntent(AddCardIntent.ClickCard(visa))
        assertEquals(setOf("mastercard"), viewModel.state.value.selectedCardIds)
    }

    @Test
    fun selectAllAndClear() = runTest(dispatcher) {
        val viewModel = loaded(FakeCardRepository(cards = listOf(visa, mastercard)))
        viewModel.onIntent(AddCardIntent.LongClickCard(visa))

        viewModel.onIntent(AddCardIntent.SelectAllCards)
        assertTrue(viewModel.state.value.areAllCardsSelected)

        viewModel.onIntent(AddCardIntent.ClearCardSelection)
        assertFalse(viewModel.state.value.isSelectionMode)
    }

    @Test
    fun deleteSelected_asksFirstThenDeletesThem() = runTest(dispatcher) {
        val repository = FakeCardRepository(cards = listOf(visa, mastercard))
        val viewModel = loaded(repository)
        viewModel.onIntent(AddCardIntent.LongClickCard(visa))
        viewModel.onIntent(AddCardIntent.ClickCard(mastercard))

        viewModel.onIntent(AddCardIntent.RequestDeleteSelectedCards)
        assertTrue(viewModel.state.value.isDeleteSelectedConfirmVisible)

        viewModel.onIntent(AddCardIntent.ConfirmDeleteSelectedCards)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.cards.isEmpty())
        assertFalse(viewModel.state.value.isSelectionMode)
        assertFalse(viewModel.state.value.isDeleteSelectedConfirmVisible)
        assertEquals(AddCardEffect.CardsDeleted(2), viewModel.effect.first())
    }

    @Test
    fun deleteSelected_keepsTheFailedOnesSelected() = runTest(dispatcher) {
        val repository = FakeCardRepository(cards = listOf(visa, mastercard)).apply { failingDeleteIds = setOf("visa") }
        val viewModel = loaded(repository)
        viewModel.onIntent(AddCardIntent.LongClickCard(visa))
        viewModel.onIntent(AddCardIntent.ClickCard(mastercard))
        viewModel.onIntent(AddCardIntent.RequestDeleteSelectedCards)

        viewModel.onIntent(AddCardIntent.ConfirmDeleteSelectedCards)
        advanceUntilIdle()

        assertEquals(listOf(visa), viewModel.state.value.cards)
        assertEquals(setOf("visa"), viewModel.state.value.selectedCardIds)
        assertEquals(AddCardEffect.CardsDeleted(1), viewModel.effect.first())
        assertEquals(AddCardEffect.CardsDeleteFailed, viewModel.effect.first())
    }
}
