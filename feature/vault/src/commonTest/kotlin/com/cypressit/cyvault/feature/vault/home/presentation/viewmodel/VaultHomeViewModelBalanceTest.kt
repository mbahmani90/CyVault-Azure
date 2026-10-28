package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import com.cypressit.cyvault.feature.vault.fake.FakeBalanceRepository
import com.cypressit.cyvault.feature.vault.fake.FakeCardRepository
import com.cypressit.cyvault.feature.vault.fake.FakeProfilePhotoRepository
import com.cypressit.cyvault.feature.vault.home.data.repository.AccountRepositoryImpl
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ChangeProfilePhotoUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ClearProfilePhotoCacheUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteBalanceUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteCardUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetBalancesUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCardsUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCurrentAccountUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetProfilePhotoUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.SaveBalanceUseCase
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
    private val cards = FakeCardRepository()
    private val balances = FakeBalanceRepository()

    private val main =
        Balance(id = "main", name = "Main account", amount = 1250.5, currency = "EUR", date = "2026-10-20T00:00:00Z")
    private val savings =
        Balance(id = "savings", name = "Savings", amount = 300.0, currency = "EUR", date = "2026-10-01T00:00:00Z")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): VaultHomeViewModel {
        val accounts = AccountRepositoryImpl(FakeAuthClient())
        return VaultHomeViewModel(
            getCards = GetCardsUseCase(cards),
            deleteCard = DeleteCardUseCase(cards),
            getCurrentAccount = GetCurrentAccountUseCase(accounts),
            signOutUser = SignOutUseCase(accounts),
            getProfilePhoto = GetProfilePhotoUseCase(photos),
            changeProfilePhoto = ChangeProfilePhotoUseCase(photos),
            clearProfilePhotoCache = ClearProfilePhotoCacheUseCase(photos),
            getBalances = GetBalancesUseCase(balances),
            saveBalance = SaveBalanceUseCase(balances),
            deleteBalance = DeleteBalanceUseCase(balances),
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
                main.copy(amount = -25.69),
                savings.copy(amount = 100.0),
                Balance(id = "uk", name = "Tesco", amount = -12.5, currency = "GBP"),
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
    fun saveBalance_new_getsAnIdAndIsAddedNewestFirst() = runTest(dispatcher) {
        balances.balances = listOf(savings)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.ShowAddItemDialog)
        viewModel.onIntent(
            VaultHomeIntent.SaveItem("Main account", "Daily spending", 1250.5, "EUR", "2026-10-28T09:00:00Z"),
        )
        advanceUntilIdle()

        val saved = balances.saved.single()
        assertTrue(saved.id.isNotBlank())
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

        viewModel.onIntent(VaultHomeIntent.EditBalance(main))
        viewModel.onIntent(VaultHomeIntent.SaveItem("Main account", "", 99.0, "EUR", "2026-10-20T00:00:00Z"))
        advanceUntilIdle()

        assertEquals("main", balances.saved.single().id)
        assertEquals(listOf(main.copy(amount = 99.0)), viewModel.state.value.balances)
        assertNull(viewModel.state.value.editingBalance)
    }

    @Test
    fun saveBalance_whenItFails_keepsTheDialogOpen() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        balances.failure = Exception("offline")

        viewModel.onIntent(VaultHomeIntent.ShowAddItemDialog)
        viewModel.onIntent(VaultHomeIntent.SaveItem("Main account", "", 1.0, "EUR", "2026-10-28T09:00:00Z"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isAddItemDialogVisible)
        assertFalse(viewModel.state.value.isSavingBalance)
        assertEquals(VaultHomeEffect.BalanceRequestFailed, viewModel.effect.first())
    }

    @Test
    fun deleteBalance_removesTheRow() = runTest(dispatcher) {
        balances.balances = listOf(main, savings)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.DeleteBalance("main"))
        advanceUntilIdle()

        assertEquals(listOf("main"), balances.deletedIds)
        assertEquals(listOf(savings), viewModel.state.value.balances)
        assertEquals(VaultHomeEffect.BalanceDeleted, viewModel.effect.first())
    }
}
