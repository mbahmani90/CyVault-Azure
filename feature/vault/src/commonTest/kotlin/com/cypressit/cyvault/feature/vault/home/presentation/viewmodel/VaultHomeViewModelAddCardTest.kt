package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain, advanceUntilIdle
class VaultHomeViewModelAddCardTest {

    private val dispatcher = StandardTestDispatcher()
    private val photos = FakeProfilePhotoRepository()
    private val cards = FakeCardRepository()
    private val balances = FakeBalanceRepository()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): VaultHomeViewModel {
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
            saveBalance = SaveBalanceUseCase(balances),
            deleteBalance = DeleteBalanceUseCase(balances),
            deleteBalances = DeleteBalancesUseCase(balances),
            sendTransfer = SendTransferUseCase(balances, FakeDeviceRepository()),
            enrollDevice = EnrollDeviceUseCase(FakeDeviceRepository()),
            transferPromptTexts = fixedTransferPromptTexts,
        )
    }

    // Cards is in every build, release included.
    @Test
    fun cardsFromDrawer_closesTheDrawerAndOpensCards() = runTest(dispatcher) {
        val viewModel = viewModel().also { advanceUntilIdle() }

        viewModel.onIntent(VaultHomeIntent.AddCardFromDrawer)

        assertEquals(VaultHomeEffect.CloseDrawer, viewModel.effect.first())
        assertEquals(VaultHomeEffect.NavigateToAddCard, viewModel.effect.first())
    }
}
