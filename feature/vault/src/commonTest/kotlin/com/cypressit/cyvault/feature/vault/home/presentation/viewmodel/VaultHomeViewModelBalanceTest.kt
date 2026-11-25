package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.device.domain.usecase.EnrollDeviceUseCase
import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import com.cypressit.cyvault.feature.vault.fake.FakeBalanceCache
import com.cypressit.cyvault.feature.vault.fake.FakeBalanceRepository
import com.cypressit.cyvault.feature.vault.fake.FakeCardRepository
import com.cypressit.cyvault.feature.vault.fake.FakeDeviceRepository
import com.cypressit.cyvault.feature.vault.fake.FakeProfilePhotoRepository
import com.cypressit.cyvault.feature.vault.fake.fixedTransferPromptTexts
import com.cypressit.cyvault.feature.vault.fake.succeedingSessionApi
import com.cypressit.cyvault.feature.vault.home.data.repository.AccountRepositoryImpl
import com.cypressit.cyvault.feature.vault.home.domain.debug.NoCardsForSamplesException
import com.cypressit.cyvault.feature.vault.home.domain.debug.SampleBalancesSeeder
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceTotals
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
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ObserveBalanceTotalsUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ObserveCardsUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ObserveLoadedBalanceIdsUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.RefreshBalanceTotalsUseCase
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
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
        val accounts = AccountRepositoryImpl(authClient, succeedingSessionApi(authClient), FakeBalanceCache())
        return VaultHomeViewModel(
            getCards = GetCardsUseCase(cards),
            observeCards = ObserveCardsUseCase(cards),
            deleteCard = DeleteCardUseCase(cards),
            getCurrentAccount = GetCurrentAccountUseCase(accounts),
            signOutUser = SignOutUseCase(accounts, FakeDeviceRepository()),
            getProfilePhoto = GetProfilePhotoUseCase(photos),
            changeProfilePhoto = ChangeProfilePhotoUseCase(photos),
            clearProfilePhotoCache = ClearProfilePhotoCacheUseCase(photos),
            getBalances = GetBalancesUseCase(balances),
            observeLoadedBalanceIds = ObserveLoadedBalanceIdsUseCase(balances),
            observeBalanceTotals = ObserveBalanceTotalsUseCase(balances),
            refreshBalanceTotals = RefreshBalanceTotalsUseCase(balances),
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
    fun total_waitsForTheFirstPageAndTheTotals() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        balances.totalsGate = gate
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isTotalLoading)

        viewModel.onIntent(VaultHomeIntent.BalancesLoadChanged(isLoading = false, hasFailed = false))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isTotalLoading, "the totals are still loading")

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isTotalLoading)
    }

    @Test
    fun total_waitsForTheFirstPageWhenTheTotalsCameFirst() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onIntent(VaultHomeIntent.BalancesLoadChanged(isLoading = true, hasFailed = false))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isTotalLoading)

        viewModel.onIntent(VaultHomeIntent.BalancesLoadChanged(isLoading = false, hasFailed = false))
        assertFalse(viewModel.state.value.isTotalLoading)
    }

    @Test
    fun total_showsWhenTheTotalsFail() = runTest(dispatcher) {
        balances.failure = Exception("offline")
        val viewModel = viewModel()
        viewModel.onIntent(VaultHomeIntent.BalancesLoadChanged(isLoading = false, hasFailed = true))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isTotalLoading)
    }

    @Test
    fun total_hidesAgainDuringAPull() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onIntent(VaultHomeIntent.BalancesLoadChanged(isLoading = false, hasFailed = false))
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        balances.totalsGate = gate

        viewModel.onIntent(VaultHomeIntent.Refresh)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isTotalLoading)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isTotalLoading)
    }

    @Test
    fun total_staysShownWhileADeleteRefreshesIt() = runTest(dispatcher) {
        balances.balances = listOf(main)
        val viewModel = viewModel()
        viewModel.onIntent(VaultHomeIntent.BalancesLoadChanged(isLoading = false, hasFailed = false))
        advanceUntilIdle()
        balances.totalsGate = CompletableDeferred()

        viewModel.onIntent(VaultHomeIntent.LongClickBalance(main))
        viewModel.onIntent(VaultHomeIntent.RequestDeleteSelectedBalances)
        viewModel.onIntent(VaultHomeIntent.ConfirmDeleteSelectedBalances)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isTotalLoading)
    }

    @Test
    fun init_loadsTheBalances() = runTest(dispatcher) {
        balances.balances = listOf(main, savings)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(setOf("main", "savings"), viewModel.state.value.loadedBalanceIds)
    }

    @Test
    fun init_isLoadingUntilThePagingReportsOtherwise() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isLoadingBalances)

        viewModel.onIntent(VaultHomeIntent.BalancesLoadChanged(isLoading = false, hasFailed = false))

        assertFalse(viewModel.state.value.isLoadingBalances)
        assertFalse(viewModel.state.value.hasBalancesLoadFailed)
    }

    @Test
    fun init_fetchesTheTotals() = runTest(dispatcher) {
        viewModel()
        advanceUntilIdle()

        assertEquals(1, balances.totalsRefreshCount)
    }

    @Test
    fun totals_showWhatTheRepositoryHolds() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        balances.totals.value = BalanceTotals(listOf("EUR" to 74.31, "GBP" to -12.5), "2026-08-08T16:58:51Z")
        advanceUntilIdle()

        assertEquals(listOf("EUR" to 74.31, "GBP" to -12.5), viewModel.state.value.balanceTotals)
        assertEquals("2026-08-08T16:58:51Z", viewModel.state.value.oldestBalanceDate)
    }

    @Test
    fun totals_whenFetchingFails_isQuiet() = runTest(dispatcher) {
        balances.failure = Exception("offline")

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(VaultHomeIntent.BalancesLoadChanged(isLoading = false, hasFailed = true))

        // The list's failure is the only thing reported.
        assertEquals(VaultHomeEffect.BalanceRequestFailed, viewModel.effect.first())
        assertNull(withTimeoutOrNull(1) { viewModel.effect.first() })
    }

    @Test
    fun firstPageFailing_reportsIt() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.BalancesLoadChanged(isLoading = false, hasFailed = true))

        assertTrue(viewModel.state.value.hasBalancesLoadFailed)
        assertFalse(viewModel.state.value.isLoadingBalances)
        assertEquals(VaultHomeEffect.BalanceRequestFailed, viewModel.effect.first())
    }

    @Test
    fun firstPageRetrying_clearsTheFailure() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onIntent(VaultHomeIntent.BalancesLoadChanged(isLoading = false, hasFailed = true))

        viewModel.onIntent(VaultHomeIntent.BalancesLoadChanged(isLoading = true, hasFailed = false))

        assertFalse(viewModel.state.value.hasBalancesLoadFailed)
        assertTrue(viewModel.state.value.isLoadingBalances)
    }

    @Test
    fun refresh_asksTheListToReloadAndFetchesTotalsAndCards() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        val cardFetches = cards.getCardsCalls
        val totalFetches = balances.totalsRefreshCount

        viewModel.onIntent(VaultHomeIntent.Refresh)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isRefreshing)
        assertEquals(VaultHomeEffect.RefreshBalances, viewModel.effect.first())
        assertEquals(cardFetches + 1, cards.getCardsCalls)
        assertEquals(totalFetches + 1, balances.totalsRefreshCount)
    }

    @Test
    fun refresh_spinsUntilTheLoadItStartedIsDone() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.Refresh)
        viewModel.onIntent(VaultHomeIntent.BalancesLoadChanged(isLoading = true, hasFailed = false))
        assertTrue(viewModel.state.value.isRefreshing)

        viewModel.onIntent(VaultHomeIntent.BalancesLoadChanged(isLoading = false, hasFailed = false))
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun refresh_whileRefreshing_isIgnored() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        val cardFetches = cards.getCardsCalls

        viewModel.onIntent(VaultHomeIntent.Refresh)
        viewModel.onIntent(VaultHomeIntent.Refresh)
        advanceUntilIdle()

        assertEquals(cardFetches + 1, cards.getCardsCalls)
    }

    @Test
    fun refresh_whenLoadingFails_reportsItAndStopsSpinning() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.Refresh)
        advanceUntilIdle()
        assertEquals(VaultHomeEffect.RefreshBalances, viewModel.effect.first())
        viewModel.onIntent(VaultHomeIntent.BalancesLoadChanged(isLoading = false, hasFailed = true))

        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(VaultHomeEffect.BalanceRequestFailed, viewModel.effect.first())
    }

    @Test
    fun saveBalance_new_getsAnIdIsCachedAndUpdatesTheTotals() = runTest(dispatcher) {
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
        assertEquals(setOf("savings", saved.id), state.loadedBalanceIds)
        assertEquals(2, balances.totalsRefreshCount)
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
        assertEquals(setOf("main"), viewModel.state.value.loadedBalanceIds)
        assertEquals(99.0, balances.balances.single().amount)
        assertNull(viewModel.state.value.editingBalance)
    }

    @Test
    fun saveBalance_edit_whenTheServerGivesANewId_replacesTheOldRow() = runTest(dispatcher) {
        balances.balances = listOf(main)
        balances.idAfterUpdate = "moved"
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.ClickBalance(main))
        advanceUntilIdle()
        viewModel.onIntent(VaultHomeIntent.SaveItem("Main account", "", 99.0, "EUR", "2026-10-20T00:00:00Z"))
        advanceUntilIdle()

        assertEquals("main", balances.attemptedIds.single())
        assertEquals(setOf("moved"), viewModel.state.value.loadedBalanceIds)
    }

    @Test
    fun saveBalance_new_savedAgainAfterAFailure_keepsItsIdUntilItSaves() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        fun save() = viewModel.onIntent(
            VaultHomeIntent.SaveItem("Main account", "", 1.0, "EUR", "2026-10-28T09:00:00Z"),
        )

        viewModel.onIntent(VaultHomeIntent.ShowAddItemDialog)
        advanceUntilIdle()
        balances.failure = Exception("offline")
        save()
        advanceUntilIdle()
        assertEquals(VaultHomeEffect.BalanceRequestFailed, viewModel.effect.first())
        balances.failure = null
        save()
        advanceUntilIdle()
        assertEquals(VaultHomeEffect.BalanceSaved, viewModel.effect.first())

        // The next new balance is another one.
        viewModel.onIntent(VaultHomeIntent.ShowAddItemDialog)
        advanceUntilIdle()
        save()
        advanceUntilIdle()

        val (failed, retried, next) = balances.attemptedIds
        assertEquals(failed, retried)
        assertTrue(next != retried)
    }

    @Test
    fun saveBalance_new_dismissedAfterAFailure_getsANewIdNextTime() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        balances.failure = Exception("offline")

        repeat(2) {
            viewModel.onIntent(VaultHomeIntent.ShowAddItemDialog)
            advanceUntilIdle()
            viewModel.onIntent(VaultHomeIntent.SaveItem("Main account", "", 1.0, "EUR", "2026-10-28T09:00:00Z"))
            advanceUntilIdle()
            assertEquals(VaultHomeEffect.BalanceRequestFailed, viewModel.effect.first())
            viewModel.onIntent(VaultHomeIntent.DismissAddItemDialog)
        }

        assertEquals(2, balances.attemptedIds.toSet().size)
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
        assertEquals(setOf("savings"), viewModel.state.value.loadedBalanceIds)
        assertEquals(2, balances.totalsRefreshCount)
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
        assertEquals(setOf("main"), viewModel.state.value.loadedBalanceIds)
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
        assertEquals(setOf("main"), viewModel.state.value.loadedBalanceIds)
        assertFalse(viewModel.state.value.isAddingSampleBalances)
        assertEquals(VaultHomeEffect.CloseDrawer, viewModel.effect.first())
        assertEquals(VaultHomeEffect.RefreshBalances, viewModel.effect.first())
        assertEquals(VaultHomeEffect.SampleBalancesAdded(50), viewModel.effect.first())
    }

    @Test
    fun addSampleBalances_withoutACard_saysACardIsNeeded() = runTest(dispatcher) {
        val viewModel = viewModel(sampleBalancesSeeder = { Result.failure(NoCardsForSamplesException()) })
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.AddSampleBalances)
        advanceUntilIdle()

        assertEquals(VaultHomeEffect.CloseDrawer, viewModel.effect.first())
        assertEquals(VaultHomeEffect.RefreshBalances, viewModel.effect.first())
        assertEquals(VaultHomeEffect.SampleBalancesNeedACard, viewModel.effect.first())
    }

    @Test
    fun addSampleBalances_whenASaveFails_reportsARequestFailure() = runTest(dispatcher) {
        val viewModel = viewModel(sampleBalancesSeeder = { Result.failure(Exception("offline")) })
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.AddSampleBalances)
        advanceUntilIdle()

        assertEquals(VaultHomeEffect.CloseDrawer, viewModel.effect.first())
        assertEquals(VaultHomeEffect.RefreshBalances, viewModel.effect.first())
        assertEquals(VaultHomeEffect.BalanceRequestFailed, viewModel.effect.first())
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
