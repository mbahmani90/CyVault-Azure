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
import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteBalancesUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteCardUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetBalancesUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCardsUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCurrentAccountUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetProfilePhotoUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.SaveBalanceUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.SignOutUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain, advanceUntilIdle
class VaultHomeViewModelSelectionTest {

    private val dispatcher = StandardTestDispatcher()
    private val photos = FakeProfilePhotoRepository()
    private val cards = FakeCardRepository()
    private val balances = FakeBalanceRepository()

    private val main =
        Balance(id = "main", name = "Main account", amount = 1250.5, currency = "EUR", date = "2026-10-20T00:00:00Z")
    private val savings =
        Balance(id = "savings", name = "Savings", amount = 300.0, currency = "EUR", date = "2026-10-01T00:00:00Z")
    private val rent =
        Balance(id = "rent", name = "Rent", amount = -900.0, currency = "EUR", date = "2026-09-01T00:00:00Z")

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
            deleteBalances = DeleteBalancesUseCase(balances),
        )
    }

    /** A view model with [main], [savings] and [rent] loaded. */
    private fun TestScope.loaded(): VaultHomeViewModel {
        balances.balances = listOf(main, savings, rent)
        return viewModel().also { advanceUntilIdle() }
    }

    @Test
    fun longClick_startsSelectionModeWithThatBalance() = runTest(dispatcher) {
        val viewModel = loaded()
        assertFalse(viewModel.state.value.isSelectionMode)

        viewModel.onIntent(VaultHomeIntent.LongClickBalance(main))

        val state = viewModel.state.value
        assertTrue(state.isSelectionMode)
        assertTrue(state.isBalanceSelected("main"))
        assertFalse(state.isBalanceSelected("savings"))
        assertEquals(1, state.selectedBalanceCount)
    }

    @Test
    fun click_outsideSelectionMode_opensTheEditor() = runTest(dispatcher) {
        val viewModel = loaded()

        viewModel.onIntent(VaultHomeIntent.ClickBalance(main))

        val state = viewModel.state.value
        assertTrue(state.isAddItemDialogVisible)
        assertEquals(main, state.editingBalance)
        assertFalse(state.isSelectionMode)
    }

    @Test
    fun click_inSelectionMode_selectsInsteadOfEditing() = runTest(dispatcher) {
        val viewModel = loaded()

        viewModel.onIntent(VaultHomeIntent.LongClickBalance(main))
        viewModel.onIntent(VaultHomeIntent.ClickBalance(savings))

        val state = viewModel.state.value
        assertEquals(2, state.selectedBalanceCount)
        assertFalse(state.isAddItemDialogVisible)
    }

    @Test
    fun click_deselectingTheLastOneLeavesSelectionMode() = runTest(dispatcher) {
        val viewModel = loaded()

        viewModel.onIntent(VaultHomeIntent.LongClickBalance(main))
        viewModel.onIntent(VaultHomeIntent.ClickBalance(main))

        assertFalse(viewModel.state.value.isSelectionMode)
        assertEquals(0, viewModel.state.value.selectedBalanceCount)
    }

    @Test
    fun selectAll_selectsEveryBalance() = runTest(dispatcher) {
        val viewModel = loaded()

        viewModel.onIntent(VaultHomeIntent.LongClickBalance(main))
        viewModel.onIntent(VaultHomeIntent.SelectAllBalances)

        assertEquals(setOf("main", "savings", "rent"), viewModel.state.value.selectedBalanceIds)
        assertTrue(viewModel.state.value.areAllBalancesSelected)
    }

    @Test
    fun clearSelection_leavesSelectionMode() = runTest(dispatcher) {
        val viewModel = loaded()

        viewModel.onIntent(VaultHomeIntent.SelectAllBalances)
        viewModel.onIntent(VaultHomeIntent.ClearBalanceSelection)

        assertFalse(viewModel.state.value.isSelectionMode)
        assertEquals(listOf(main, savings, rent), viewModel.state.value.balances)
    }

    @Test
    fun requestDeleteSelected_asksToConfirmWithoutDeleting() = runTest(dispatcher) {
        val viewModel = loaded()

        viewModel.onIntent(VaultHomeIntent.LongClickBalance(main))
        viewModel.onIntent(VaultHomeIntent.RequestDeleteSelectedBalances)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isDeleteSelectedConfirmVisible)
        assertEquals(emptyList(), balances.deletedIds)
    }

    @Test
    fun requestDeleteSelected_withNothingSelected_asksNothing() = runTest(dispatcher) {
        val viewModel = loaded()

        viewModel.onIntent(VaultHomeIntent.RequestDeleteSelectedBalances)

        assertFalse(viewModel.state.value.isDeleteSelectedConfirmVisible)
    }

    @Test
    fun dismissDeleteSelected_keepsTheSelection() = runTest(dispatcher) {
        val viewModel = loaded()

        viewModel.onIntent(VaultHomeIntent.LongClickBalance(main))
        viewModel.onIntent(VaultHomeIntent.RequestDeleteSelectedBalances)
        viewModel.onIntent(VaultHomeIntent.DismissDeleteSelectedBalances)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isDeleteSelectedConfirmVisible)
        assertTrue(state.isBalanceSelected("main"))
        assertEquals(emptyList(), balances.deletedIds)
    }

    @Test
    fun confirmDeleteSelected_removesThemAndLeavesSelectionMode() = runTest(dispatcher) {
        val viewModel = loaded()

        viewModel.onIntent(VaultHomeIntent.LongClickBalance(main))
        viewModel.onIntent(VaultHomeIntent.ClickBalance(rent))
        viewModel.onIntent(VaultHomeIntent.RequestDeleteSelectedBalances)
        viewModel.onIntent(VaultHomeIntent.ConfirmDeleteSelectedBalances)
        advanceUntilIdle()

        assertEquals(setOf("main", "rent"), balances.deletedIds.toSet())
        val state = viewModel.state.value
        assertEquals(listOf(savings), state.balances)
        assertFalse(state.isSelectionMode)
        assertFalse(state.isDeleteSelectedConfirmVisible)
        assertFalse(state.isDeletingBalances)
        assertEquals(VaultHomeEffect.BalancesDeleted(2), viewModel.effect.first())
    }

    @Test
    fun confirmDeleteSelected_whenOneFails_keepsItSelectedAndReportsBoth() = runTest(dispatcher) {
        val viewModel = loaded()
        balances.failingDeleteIds = setOf("rent")

        viewModel.onIntent(VaultHomeIntent.LongClickBalance(main))
        viewModel.onIntent(VaultHomeIntent.ClickBalance(rent))
        viewModel.onIntent(VaultHomeIntent.RequestDeleteSelectedBalances)
        viewModel.onIntent(VaultHomeIntent.ConfirmDeleteSelectedBalances)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf(savings, rent), state.balances)
        assertEquals(setOf("rent"), state.selectedBalanceIds)
        assertEquals(
            listOf(VaultHomeEffect.BalancesDeleted(1), VaultHomeEffect.BalanceRequestFailed),
            viewModel.effect.take(2).toList(),
        )
    }

    @Test
    fun deleteSingleBalance_alsoRemovesItFromTheSelection() = runTest(dispatcher) {
        val viewModel = loaded()

        // Ask to delete main from its editor, then select it and savings before confirming.
        viewModel.onIntent(VaultHomeIntent.ClickBalance(main))
        viewModel.onIntent(VaultHomeIntent.RequestDeleteEditingBalance)
        viewModel.onIntent(VaultHomeIntent.LongClickBalance(main))
        viewModel.onIntent(VaultHomeIntent.ClickBalance(savings))
        viewModel.onIntent(VaultHomeIntent.ConfirmDeleteBalance)
        advanceUntilIdle()

        assertEquals(setOf("savings"), viewModel.state.value.selectedBalanceIds)
    }
}
