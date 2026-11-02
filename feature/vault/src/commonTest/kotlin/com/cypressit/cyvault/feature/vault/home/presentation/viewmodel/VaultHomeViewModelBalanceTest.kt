package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.device.domain.usecase.EnrollDeviceUseCase
import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import com.cypressit.cyvault.feature.vault.fake.FakeBalanceRepository
import com.cypressit.cyvault.feature.vault.fake.FakeCardRepository
import com.cypressit.cyvault.feature.vault.fake.FakeDeviceRepository
import com.cypressit.cyvault.feature.vault.fake.FakeProfilePhotoRepository
import com.cypressit.cyvault.feature.vault.fake.fixedTransferPromptTexts
import com.cypressit.cyvault.feature.vault.fake.succeedingSessionApi
import com.cypressit.cyvault.feature.vault.home.data.repository.AccountRepositoryImpl
import com.cypressit.cyvault.feature.vault.home.domain.debug.SampleBalancesSeeder
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceType
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class VaultHomeViewModelBalanceTest {

    private val dispatcher = StandardTestDispatcher()
    private val photos = FakeProfilePhotoRepository()
    private val visa = Card("visa", "1111", "Visa", "CGD", "+351912345678", "")
    private val mastercard = visa.copy(id = "mastercard", last4 = "4444", brand = "Mastercard")
    private val cards = FakeCardRepository(cards = listOf(visa, mastercard))
    private val balances = FakeBalanceRepository()

    private val main =
        Balance(id = "main", name = "Main account", amount = 1250.5, currency = "EUR", date = "2026-10-20T00:00:00Z")
    private val savings =
        Balance(id = "savings", name = "Savings", amount = 300.0, currency = "EUR", date = "2026-10-01T00:00:00Z")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(sampleBalancesSeeder: SampleBalancesSeeder? = null): VaultHomeViewModel {
        val authClient = FakeAuthClient()
        val accounts = AccountRepositoryImpl(authClient, succeedingSessionApi(authClient))
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
            sendTransfer = SendTransferUseCase(balances, FakeDeviceRepository()),
            enrollDevice = EnrollDeviceUseCase(FakeDeviceRepository()),
            transferPromptTexts = fixedTransferPromptTexts,
            sampleBalancesSeeder = sampleBalancesSeeder,
        )
    }

    @Test
    fun init_loadsTheBalances() = runTest(dispatcher) {
        balances.balances = listOf(main, savings)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(main, savings), viewModel.state.value.balances)
        assertFalse(viewModel.state.value.isLoadingBalances)
    }

    @Test
    fun init_showsLoadingUntilTheBalancesArrive() = runTest(dispatcher) {
        val response = CompletableDeferred<Unit>()
        balances.gate = response
        balances.balances = listOf(main)

        val viewModel = viewModel()
        runCurrent()
        assertTrue(viewModel.state.value.isLoadingBalances)

        response.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoadingBalances)
        assertEquals(listOf(main), viewModel.state.value.balances)
    }

    @Test
    fun balanceTotals_addUpEachCurrencySeparately() {
        val state = VaultHomeState(
            balances = listOf(
                main.copy(amount = 25.69, type = BalanceType.Purchase),
                savings.copy(amount = 100.0),
                Balance(id = "uk", name = "Tesco", amount = 12.5, currency = "GBP", type = BalanceType.Purchase),
            ),
        )

        assertEquals(
            listOf("EUR" to 74.31, "GBP" to -12.5),
            state.balanceTotals.map { (c, t) ->
                c to
                    (kotlin.math.round(t * 100) / 100)
            },
        )
    }

    @Test
    fun init_whenLoadingFails_reportsIt() = runTest(dispatcher) {
        balances.failure = Exception("offline")

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(VaultHomeEffect.BalanceRequestFailed, viewModel.effect.first())
    }

    @Test
    fun refresh_fetchesTheBalancesAndCardsAgain() = runTest(dispatcher) {
        balances.balances = listOf(savings)
        val viewModel = viewModel()
        advanceUntilIdle()
        val cardFetches = cards.getCardsCalls

        balances.balances = listOf(main, savings)
        viewModel.onIntent(VaultHomeIntent.Refresh)

        assertTrue(viewModel.state.value.isRefreshing)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(listOf(main, savings), viewModel.state.value.balances)
        assertEquals(cardFetches + 1, cards.getCardsCalls)
    }

    @Test
    fun refresh_whileRefreshing_isIgnored() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        val response = CompletableDeferred<Unit>()
        balances.gate = response
        val cardFetches = cards.getCardsCalls

        viewModel.onIntent(VaultHomeIntent.Refresh)
        viewModel.onIntent(VaultHomeIntent.Refresh)
        runCurrent()
        response.complete(Unit)
        advanceUntilIdle()

        assertEquals(cardFetches + 1, cards.getCardsCalls)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun refresh_whenLoadingFails_reportsItAndStopsSpinning() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        balances.failure = Exception("offline")

        viewModel.onIntent(VaultHomeIntent.Refresh)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(VaultHomeEffect.BalanceRequestFailed, viewModel.effect.first())
    }

    @Test
    fun saveBalance_new_getsAnIdAndIsAddedNewestFirst() = runTest(dispatcher) {
        balances.balances = listOf(savings)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.ShowAddItemDialog)
        advanceUntilIdle()
        viewModel.onIntent(
            VaultHomeIntent.SaveItem("Main account", "Daily spending", 1250.5, "EUR", "2026-10-28T09:00:00Z"),
        )
        advanceUntilIdle()

        val saved = balances.saved.single()
        assertTrue(saved.id.isNotBlank())
        assertEquals("visa", saved.cardId)
        assertEquals("Daily spending", saved.description)
        assertEquals("2026-10-28T09:00:00Z", saved.date)
        val state = viewModel.state.value
        assertEquals(listOf("Main account", "Savings"), state.balances.map { it.name })
        assertFalse(state.isAddItemDialogVisible)
        assertFalse(state.isSavingBalance)
        assertEquals(VaultHomeEffect.BalanceSaved, viewModel.effect.first())
    }

    @Test
    fun saveBalance_edit_keepsTheIdAndReplacesTheRow() = runTest(dispatcher) {
        balances.balances = listOf(main)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.ClickBalance(main))
        advanceUntilIdle()
        viewModel.onIntent(VaultHomeIntent.SaveItem("Main account", "", 99.0, "EUR", "2026-10-20T00:00:00Z"))
        advanceUntilIdle()

        assertEquals("main", balances.saved.single().id)
        assertEquals(listOf(main.copy(amount = 99.0, cardId = "visa")), viewModel.state.value.balances)
        assertNull(viewModel.state.value.editingBalance)
    }

    @Test
    fun saveBalance_whenItFails_keepsTheDialogOpen() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        balances.failure = Exception("offline")

        viewModel.onIntent(VaultHomeIntent.ShowAddItemDialog)
        advanceUntilIdle()
        viewModel.onIntent(VaultHomeIntent.SaveItem("Main account", "", 1.0, "EUR", "2026-10-28T09:00:00Z"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isAddItemDialogVisible)
        assertFalse(viewModel.state.value.isSavingBalance)
        assertEquals(VaultHomeEffect.BalanceRequestFailed, viewModel.effect.first())
    }

    @Test
    fun init_loadsTheCardsTheRowsAreNamedAfter() = runTest(dispatcher) {
        val onVisa = main.copy(cardId = "visa")
        val onMastercard = savings.copy(cardId = "mastercard")
        balances.balances = listOf(onVisa, onMastercard)
        cards.serverCards = listOf(visa, mastercard.copy(bankName = ""))
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        // The bank, or the brand when the card has no bank.
        assertEquals("CGD", state.cardNameOf(onVisa))
        assertEquals("Mastercard", state.cardNameOf(onMastercard))
        assertNull(state.cardNameOf(main))
        assertNull(state.cardNameOf(main.copy(cardId = "deleted")))
    }

    @Test
    fun showAddItemDialog_usesTheCardsFetchedOnStartAndPicksTheFirst() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.ShowAddItemDialog)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoadingItemCards)
        assertEquals(listOf(visa, mastercard), state.itemCards)
        assertEquals(visa, state.itemCard)
        // Fetched once, on start: the dialog doesn't fetch again.
        assertEquals(1, cards.getCardsCalls)
    }

    @Test
    fun showAddItemDialog_withoutCardsYet_fetchesThem() = runTest(dispatcher) {
        cards.serverCards = emptyList()
        val viewModel = viewModel()
        advanceUntilIdle()
        cards.serverCards = listOf(visa)

        viewModel.onIntent(VaultHomeIntent.ShowAddItemDialog)
        assertTrue(viewModel.state.value.isLoadingItemCards)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoadingItemCards)
        assertEquals(visa, viewModel.state.value.itemCard)
    }

    @Test
    fun aCardAddedElsewhere_showsOnHomeWithoutFetching() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        // As the Cards screen would, through the shared repository.
        val added = cards.createCard("amex", "378282246310005", "+351912345679", "Millennium").getOrThrow()
        advanceUntilIdle()

        assertEquals(listOf(added, visa, mastercard), viewModel.state.value.itemCards)
        assertEquals(1, cards.getCardsCalls)
    }

    @Test
    fun clickBalance_keepsTheBalancesCard() = runTest(dispatcher) {
        val onMastercard = main.copy(cardId = "mastercard")
        balances.balances = listOf(onMastercard)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.ClickBalance(onMastercard))
        advanceUntilIdle()

        assertEquals(mastercard, viewModel.state.value.itemCard)
    }

    @Test
    fun itemCardSelected_choosesTheCardAndClosesTheMenu() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(VaultHomeIntent.ShowAddItemDialog)
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.ItemCardMenuOpenChanged(true))
        assertTrue(viewModel.state.value.isItemCardMenuOpen)
        viewModel.onIntent(VaultHomeIntent.ItemCardSelected("mastercard"))

        assertEquals(mastercard, viewModel.state.value.itemCard)
        assertFalse(viewModel.state.value.isItemCardMenuOpen)
    }

    @Test
    fun saveItem_withoutACard_savesNothing() = runTest(dispatcher) {
        cards.serverCards = emptyList()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(VaultHomeIntent.ShowAddItemDialog)
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.SaveItem("Main account", "", 1.0, "EUR", "2026-10-28T09:00:00Z"))
        advanceUntilIdle()

        assertNull(viewModel.state.value.itemCard)
        assertTrue(balances.saved.isEmpty())
        assertTrue(viewModel.state.value.isAddItemDialogVisible)
    }

    @Test
    fun requestDeleteEditingBalance_closesTheEditorAndAsksToConfirm() = runTest(dispatcher) {
        balances.balances = listOf(main, savings)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.ClickBalance(main))
        viewModel.onIntent(VaultHomeIntent.RequestDeleteEditingBalance)

        val state = viewModel.state.value
        assertEquals(main, state.pendingDeleteBalance)
        assertFalse(state.isAddItemDialogVisible)
        assertNull(state.editingBalance)
        assertEquals(emptyList(), balances.deletedIds)
    }

    @Test
    fun confirmDeleteBalance_removesTheRow() = runTest(dispatcher) {
        balances.balances = listOf(main, savings)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.ClickBalance(main))
        viewModel.onIntent(VaultHomeIntent.RequestDeleteEditingBalance)
        viewModel.onIntent(VaultHomeIntent.ConfirmDeleteBalance)
        advanceUntilIdle()

        assertEquals(listOf("main"), balances.deletedIds)
        assertEquals(listOf(savings), viewModel.state.value.balances)
        assertNull(viewModel.state.value.pendingDeleteBalance)
        assertEquals(VaultHomeEffect.BalanceDeleted, viewModel.effect.first())
    }

    @Test
    fun dismissDeleteBalance_keepsTheBalance() = runTest(dispatcher) {
        balances.balances = listOf(main)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.ClickBalance(main))
        viewModel.onIntent(VaultHomeIntent.RequestDeleteEditingBalance)
        viewModel.onIntent(VaultHomeIntent.DismissDeleteBalance)
        advanceUntilIdle()

        assertNull(viewModel.state.value.pendingDeleteBalance)
        assertEquals(emptyList(), balances.deletedIds)
        assertEquals(listOf(main), viewModel.state.value.balances)
    }

    @Test
    fun addSampleBalances_withASeeder_addsThemAndReloads() = runTest(dispatcher) {
        var seeded = 0
        val viewModel = viewModel(
            sampleBalancesSeeder = {
                seeded++
                Result.success(50)
            },
        )
        advanceUntilIdle()
        balances.balances = listOf(main)

        assertTrue(viewModel.state.value.canAddSampleBalances)
        viewModel.onIntent(VaultHomeIntent.AddSampleBalances)
        advanceUntilIdle()

        assertEquals(1, seeded)
        assertEquals(listOf(main), viewModel.state.value.balances)
        assertFalse(viewModel.state.value.isAddingSampleBalances)
        assertEquals(VaultHomeEffect.CloseDrawer, viewModel.effect.first())
        assertEquals(VaultHomeEffect.SampleBalancesAdded(50), viewModel.effect.first())
    }

    @Test
    fun addSampleBalances_withoutASeeder_isHiddenAndDoesNothing() = runTest(dispatcher) {
        val viewModel = viewModel(sampleBalancesSeeder = null)
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.AddSampleBalances)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canAddSampleBalances)
        assertFalse(viewModel.state.value.isAddingSampleBalances)
    }

    @Test
    fun balanceTotals_subtractPurchasesAndTransfersAndAddIncomeAndReceives() {
        fun balance(type: BalanceType, amount: Double) =
            Balance(id = type.name, name = "", amount = amount, currency = "EUR", type = type)

        val state = VaultHomeState(
            balances = listOf(
                balance(BalanceType.Income, 100.0),
                balance(BalanceType.Purchase, 30.0),
                balance(BalanceType.Transfer, 20.0),
                balance(BalanceType.Receive, 5.0),
            ),
        )

        assertEquals(listOf("EUR" to 55.0), state.balanceTotals)
    }

    @Test
    fun saveItem_savesAPositiveAmountWithItsType() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(VaultHomeIntent.ShowAddItemDialog)
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.SaveItem("Lidl", "", -12.5, "EUR", "2026-10-28T09:00:00Z"))
        advanceUntilIdle()

        val saved = balances.saved.single()
        assertEquals(12.5, saved.amount)
        assertEquals(BalanceType.Purchase, saved.type)
    }
}
