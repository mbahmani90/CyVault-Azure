package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository
import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import com.cypressit.cyvault.feature.vault.fake.FakeBalanceRepository
import com.cypressit.cyvault.feature.vault.fake.FakeCardRepository
import com.cypressit.cyvault.feature.vault.fake.FakeProfilePhotoRepository
import com.cypressit.cyvault.feature.vault.fake.succeedingSessionApi
import com.cypressit.cyvault.feature.vault.home.data.repository.AccountRepositoryImpl
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceType
import com.cypressit.cyvault.feature.vault.home.domain.repository.InvalidTransferException
import com.cypressit.cyvault.feature.vault.home.domain.repository.NoCardForPhoneNumberException
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ChangeProfilePhotoUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ClearProfilePhotoCacheUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteBalanceUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteBalancesUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteCardUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetBalancesUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCardsUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCurrentAccountUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetProfilePhotoUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ObserveCardsUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.SaveBalanceUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.SendTransferUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.SignOutUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain, advanceUntilIdle
class VaultHomeViewModelTransferTest {

    private val dispatcher = StandardTestDispatcher()

    private val visa = Card(
        id = "visa",
        last4 = "1111",
        brand = "Visa",
        bankName = "CGD",
        phoneNumber = "+351912345678",
        createdAt = "",
    )
    private val mastercard = visa.copy(id = "mastercard", last4 = "4444", brand = "Mastercard", bankName = "")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val balances = FakeBalanceRepository()

    private fun viewModel(
        cards: CardRepository = FakeCardRepository(cards = listOf(visa, mastercard)),
    ): VaultHomeViewModel {
        val authClient = FakeAuthClient()
        val accounts = AccountRepositoryImpl(authClient, succeedingSessionApi(authClient))
        val photos = FakeProfilePhotoRepository()
        return VaultHomeViewModel(
            getCards = GetCardsUseCase(cards),
            observeCards = ObserveCardsUseCase(cards),
            deleteCard = DeleteCardUseCase(cards),
            getCurrentAccount = GetCurrentAccountUseCase(accounts),
            signOutUser = SignOutUseCase(accounts),
            getProfilePhoto = GetProfilePhotoUseCase(photos),
            changeProfilePhoto = ChangeProfilePhotoUseCase(photos),
            clearProfilePhotoCache = ClearProfilePhotoCacheUseCase(photos),
            getBalances = GetBalancesUseCase(balances),
            saveBalance = SaveBalanceUseCase(balances),
            deleteBalance = DeleteBalanceUseCase(balances),
            deleteBalances = DeleteBalancesUseCase(balances),
            sendTransfer = SendTransferUseCase(balances),
        )
    }

    private fun TestScope.openedTransfer(viewModel: VaultHomeViewModel = viewModel()): VaultHomeViewModel {
        advanceUntilIdle()
        viewModel.onIntent(VaultHomeIntent.TransferClicked)
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun transferClicked_opensTheDialogAndPicksTheFirstCard() = runTest(dispatcher) {
        val state = openedTransfer().state.value

        assertTrue(state.isTransferDialogVisible)
        assertEquals(listOf(visa, mastercard), state.transferCards)
        assertEquals(visa, state.transferCard)
        assertEquals("EUR", state.transferCurrency)
    }

    @Test
    fun transferClicked_doesNotShowCardsOnHome() = runTest(dispatcher) {
        val state = openedTransfer().state.value

        assertTrue(state.cards.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun choosingACardAndCurrency_closesTheirMenus() = runTest(dispatcher) {
        val viewModel = openedTransfer()

        viewModel.onIntent(VaultHomeIntent.TransferCardMenuOpenChanged(true))
        assertTrue(viewModel.state.value.isTransferCardMenuOpen)
        viewModel.onIntent(VaultHomeIntent.TransferCardSelected("mastercard"))
        viewModel.onIntent(VaultHomeIntent.TransferCurrencyMenuOpenChanged(true))
        viewModel.onIntent(VaultHomeIntent.TransferCurrencySelected("GBP"))

        val state = viewModel.state.value
        assertEquals(mastercard, state.transferCard)
        assertEquals("GBP", state.transferCurrency)
        assertFalse(state.isTransferCardMenuOpen)
        assertFalse(state.isTransferCurrencyMenuOpen)
    }

    @Test
    fun send_needsAValidPhoneAndAnAmountAboveZero() = runTest(dispatcher) {
        val viewModel = openedTransfer()
        assertFalse(viewModel.state.value.isTransferEnabled)

        viewModel.onIntent(VaultHomeIntent.TransferPhoneNumberChanged("912345678"))
        viewModel.onIntent(VaultHomeIntent.TransferAmountChanged("0"))
        assertFalse(viewModel.state.value.isTransferEnabled)
        assertTrue(viewModel.state.value.isTransferAmountInvalid)

        viewModel.onIntent(VaultHomeIntent.TransferAmountChanged("12,50"))
        assertEquals(12.5, viewModel.state.value.transferAmountValue)
        assertTrue(viewModel.state.value.isTransferEnabled)
    }

    @Test
    fun anInvalidPhoneNumber_isFlagged() = runTest(dispatcher) {
        val viewModel = openedTransfer()

        viewModel.onIntent(VaultHomeIntent.TransferPhoneNumberChanged("212345678"))

        assertTrue(viewModel.state.value.isTransferPhoneNumberInvalid)
    }

    @Test
    fun withoutCards_sendStaysDisabled() = runTest(dispatcher) {
        val viewModel = openedTransfer(viewModel(FakeCardRepository()))
        viewModel.onIntent(VaultHomeIntent.TransferPhoneNumberChanged("912345678"))
        viewModel.onIntent(VaultHomeIntent.TransferAmountChanged("10"))

        assertNull(viewModel.state.value.transferCard)
        assertFalse(viewModel.state.value.isTransferEnabled)
    }

    @Test
    fun cardsFailingToLoad_saysSo() = runTest(dispatcher) {
        val failing = object : CardRepository by FakeCardRepository() {
            override suspend fun getCards(): Result<List<Card>> = Result.failure(IllegalStateException())
        }
        val viewModel = openedTransfer(viewModel(failing))

        assertFalse(viewModel.state.value.isLoadingTransferCards)
        assertEquals(VaultHomeEffect.TransferCardsLoadFailed, viewModel.effect.first())
    }

    @Test
    fun dismissing_closesAndClearsTheFields() = runTest(dispatcher) {
        val viewModel = openedTransfer()
        viewModel.onIntent(VaultHomeIntent.TransferPhoneNumberChanged("912345678"))
        viewModel.onIntent(VaultHomeIntent.TransferAmountChanged("10"))

        viewModel.onIntent(VaultHomeIntent.TransferDismissed)

        val state = viewModel.state.value
        assertFalse(state.isTransferDialogVisible)
        assertEquals("", state.transferPhoneNumber)
        assertEquals("", state.transferAmount)
    }

    private fun TestScope.filledTransfer(): VaultHomeViewModel {
        val viewModel = openedTransfer()
        viewModel.onIntent(VaultHomeIntent.TransferPhoneNumberChanged("912345678"))
        viewModel.onIntent(VaultHomeIntent.TransferAmountChanged("20,50"))
        return viewModel
    }

    @Test
    fun send_sendsFromTheChosenCardAndAddsTheTransferRow() = runTest(dispatcher) {
        val viewModel = filledTransfer()

        viewModel.onIntent(VaultHomeIntent.TransferSubmitted)
        advanceUntilIdle()

        val state = viewModel.state.value
        val sent = state.balances.single()
        assertEquals(BalanceType.Transfer, sent.type)
        assertEquals(20.5, sent.amount)
        assertEquals("visa", sent.cardId)
        // The phone number goes to the API in E.164.
        assertEquals("+351912345678", sent.counterparty)
        assertFalse(state.isTransferDialogVisible)
        assertFalse(state.isSendingTransfer)
        assertEquals("", state.transferAmount)
    }

    @Test
    fun send_noCardForThePhoneNumber_saysSoInTheDialog() = runTest(dispatcher) {
        balances.transferFailure = NoCardForPhoneNumberException()
        val viewModel = filledTransfer()

        viewModel.onIntent(VaultHomeIntent.TransferSubmitted)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(TransferError.NoCardForPhoneNumber, state.transferError)
        assertTrue(state.isTransferDialogVisible)
        assertEquals("20,50", state.transferAmount)
    }

    @Test
    fun send_rejected_isInvalid() = runTest(dispatcher) {
        balances.transferFailure = InvalidTransferException()
        val viewModel = filledTransfer()

        viewModel.onIntent(VaultHomeIntent.TransferSubmitted)
        advanceUntilIdle()

        assertEquals(TransferError.Invalid, viewModel.state.value.transferError)
    }

    @Test
    fun retryingAfterAFailure_repeatsTheSameTransfer() = runTest(dispatcher) {
        balances.transferFailure = IllegalStateException("offline")
        val viewModel = filledTransfer()
        viewModel.onIntent(VaultHomeIntent.TransferSubmitted)
        advanceUntilIdle()
        assertEquals(TransferError.Failed, viewModel.state.value.transferError)

        balances.transferFailure = null
        viewModel.onIntent(VaultHomeIntent.TransferSubmitted)
        advanceUntilIdle()

        assertEquals(1, balances.transferIds.toSet().size)
        assertEquals(2, balances.transferIds.size)
    }

    @Test
    fun changingTheAmountAfterAFailure_makesItANewTransferAndClearsTheError() = runTest(dispatcher) {
        balances.transferFailure = IllegalStateException("offline")
        val viewModel = filledTransfer()
        viewModel.onIntent(VaultHomeIntent.TransferSubmitted)
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.TransferAmountChanged("30"))
        assertNull(viewModel.state.value.transferError)
        balances.transferFailure = null
        viewModel.onIntent(VaultHomeIntent.TransferSubmitted)
        advanceUntilIdle()

        assertEquals(2, balances.transferIds.toSet().size)
    }
}
