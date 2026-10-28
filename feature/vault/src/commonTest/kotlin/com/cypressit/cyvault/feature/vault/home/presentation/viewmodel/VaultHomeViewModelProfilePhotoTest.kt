package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient.Companion.USER_ID
import com.cypressit.cyvault.feature.vault.fake.FakeBalanceRepository
import com.cypressit.cyvault.feature.vault.fake.FakeCardRepository
import com.cypressit.cyvault.feature.vault.fake.FakeProfilePhotoRepository
import com.cypressit.cyvault.feature.vault.home.data.repository.AccountRepositoryImpl
import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain, runCurrent, advanceUntilIdle
class VaultHomeViewModelProfilePhotoTest {

    private val dispatcher = StandardTestDispatcher()
    private val authClient = FakeAuthClient()
    private val accounts = AccountRepositoryImpl(authClient)
    private val photos = FakeProfilePhotoRepository()
    private val cards = FakeCardRepository()
    private val balances = FakeBalanceRepository()

    private val photo = PickedPhoto("content://media/picker/0/1")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = VaultHomeViewModel(
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

    @Test
    fun init_showsTheSignedInUsersPhoto() = runTest(dispatcher) {
        photos.photo = flowOf(byteArrayOf(1))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(USER_ID), photos.photoRequests)
        assertEquals("Ada Lovelace", viewModel.state.value.userName)
        assertContentEquals(byteArrayOf(1), viewModel.state.value.profilePhoto)
    }

    @Test
    fun changeProfilePhoto_showsProgressWhileUploading() = runTest(dispatcher) {
        photos.photo = flowOf(byteArrayOf(1))
        photos.uploadGate = CompletableDeferred()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.ChangeProfilePhoto(photo))
        runCurrent()

        assertTrue(viewModel.state.value.isUpdatingPhoto)
        assertContentEquals(byteArrayOf(1), viewModel.state.value.profilePhoto)
        photos.uploadGate?.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isUpdatingPhoto)
    }

    @Test
    fun changeProfilePhoto_success_showsTheStoredPhotoAndConfirms() = runTest(dispatcher) {
        photos.changeResult = Result.success(byteArrayOf(7))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.ChangeProfilePhoto(photo))
        advanceUntilIdle()

        assertEquals(listOf(USER_ID to photo), photos.changes)
        assertContentEquals(byteArrayOf(7), viewModel.state.value.profilePhoto)
        assertFalse(viewModel.state.value.isUpdatingPhoto)
        assertEquals(VaultHomeEffect.ProfilePhotoUpdated, viewModel.effect.first())
    }

    @Test
    fun changeProfilePhoto_failure_keepsTheOldPhotoAndReportsIt() = runTest(dispatcher) {
        photos.photo = flowOf(byteArrayOf(1))
        photos.changeResult = Result.failure(Exception("Upload failed"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.ChangeProfilePhoto(photo))
        advanceUntilIdle()

        assertContentEquals(byteArrayOf(1), viewModel.state.value.profilePhoto)
        assertFalse(viewModel.state.value.isUpdatingPhoto)
        assertEquals(VaultHomeEffect.ProfilePhotoUpdateFailed, viewModel.effect.first())
    }

    @Test
    fun changeProfilePhoto_signedOut_doesNothing() = runTest(dispatcher) {
        authClient.user = null
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.ChangeProfilePhoto(photo))
        advanceUntilIdle()

        assertTrue(photos.changes.isEmpty())
    }

    @Test
    fun signOut_clearsTheCachedPhotosThenGoesToLogin() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(VaultHomeIntent.SignOut)
        advanceUntilIdle()

        assertEquals(1, authClient.signOutCount)
        assertEquals(1, photos.clearCount)
        assertEquals(
            listOf(VaultHomeEffect.CloseDrawer, VaultHomeEffect.NavigateToLogin),
            viewModel.effect.take(2).toList(),
        )
    }
}
