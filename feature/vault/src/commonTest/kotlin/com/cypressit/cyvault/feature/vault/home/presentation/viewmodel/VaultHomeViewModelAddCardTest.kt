package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import com.cypressit.cyvault.feature.vault.fake.FakeBalanceRepository
import com.cypressit.cyvault.feature.vault.fake.FakeCardRepository
import com.cypressit.cyvault.feature.vault.fake.FakeProfilePhotoRepository
import com.cypressit.cyvault.feature.vault.fake.succeedingSessionApi
import com.cypressit.cyvault.feature.vault.home.data.repository.AccountRepositoryImpl
import com.cypressit.cyvault.feature.vault.home.domain.debug.AddCardPreview
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
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain, advanceUntilIdle
class VaultHomeViewModelAddCardTest {

    private val dispatcher = StandardTestDispatcher()
    private val photos = FakeProfilePhotoRepository()
    private val cards = FakeCardRepository()
    private val balances = FakeBalanceRepository()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(addCardPreview: AddCardPreview?): VaultHomeViewModel {
        val authClient = FakeAuthClient()
        val accounts = AccountRepositoryImpl(authClient, succeedingSessionApi(authClient))
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
            addCardPreview = addCardPreview,
        )
    }

    @Test
    fun releaseBuild_hidesAddCardAndIgnoresTheIntent() = runTest(dispatcher) {
        val viewModel = viewModel(addCardPreview = null).also { advanceUntilIdle() }
        assertFalse(viewModel.state.value.canAddCard)

        viewModel.onIntent(VaultHomeIntent.AddCardFromDrawer)

        assertNull(withTimeoutOrNull(1.seconds) { viewModel.effect.first() })
    }

    @Test
    fun debugBuild_closesTheDrawerAndOpensAddCard() = runTest(dispatcher) {
        val viewModel = viewModel(addCardPreview = AddCardPreview).also { advanceUntilIdle() }
        assertTrue(viewModel.state.value.canAddCard)

        viewModel.onIntent(VaultHomeIntent.AddCardFromDrawer)

        assertEquals(VaultHomeEffect.CloseDrawer, viewModel.effect.first())
        assertEquals(VaultHomeEffect.NavigateToAddCard, viewModel.effect.first())
    }
}
