package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ChangeProfilePhotoUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ClearProfilePhotoCacheUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteCardUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCardsUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCurrentAccountUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetProfilePhotoUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.SignOutUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VaultHomeViewModel(
    private val getCards: GetCardsUseCase,
    private val deleteCard: DeleteCardUseCase,
    private val getCurrentAccount: GetCurrentAccountUseCase,
    private val signOutUser: SignOutUseCase,
    private val getProfilePhoto: GetProfilePhotoUseCase,
    private val changeProfilePhoto: ChangeProfilePhotoUseCase,
    private val clearProfilePhotoCache: ClearProfilePhotoCacheUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(VaultHomeState())
    val state: StateFlow<VaultHomeState> = _state.asStateFlow()

    private val _effect = Channel<VaultHomeEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    // The signed-in user's Entra object ID: names their cached photo on the device.
    private var userId: String? = null

    init {
        loadUser()
        loadCards()
    }

    fun onIntent(intent: VaultHomeIntent) {
        when (intent) {
            VaultHomeIntent.LoadCards -> loadCards()
            is VaultHomeIntent.DeleteCard -> deleteExistingCard(intent.id)
            VaultHomeIntent.SignOut -> signOut()
            VaultHomeIntent.OpenDrawer -> viewModelScope.launch { _effect.send(VaultHomeEffect.OpenDrawer) }
            is VaultHomeIntent.ChangeProfilePhoto -> updateProfilePhoto(intent.photo)
        }
    }

    private fun loadUser() {
        viewModelScope.launch {
            val user = getCurrentAccount() ?: return@launch
            userId = user.id
            _state.update { it.copy(userName = user.name, userEmail = user.email) }
            // The cached photo shows at once; the server's copy follows when it arrives.
            getProfilePhoto(user.id).collect { photo ->
                // An upload started meanwhile wins over this older copy.
                _state.update { if (it.isUpdatingPhoto) it else it.copy(profilePhoto = photo) }
            }
        }
    }

    private fun updateProfilePhoto(photo: PickedPhoto) {
        val id = userId ?: return
        viewModelScope.launch {
            // The current photo stays, under a progress indicator, until the upload succeeds.
            _state.update { it.copy(isUpdatingPhoto = true) }
            changeProfilePhoto(id, photo)
                .onSuccess { stored ->
                    _state.update { it.copy(profilePhoto = stored, isUpdatingPhoto = false) }
                    _effect.send(VaultHomeEffect.ProfilePhotoUpdated)
                }
                .onFailure {
                    _state.update { it.copy(isUpdatingPhoto = false) }
                    _effect.send(VaultHomeEffect.ProfilePhotoUpdateFailed)
                }
        }
    }

    private fun loadCards() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            getCards()
                .onSuccess { cards ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            cards = cards,
                            totalBalance = cards.sumOf { card -> card.balance },
                        )
                    }
                }
                .onFailure { err ->
                    _state.update { it.copy(isLoading = false) }
                    _effect.send(VaultHomeEffect.ShowError(err.message ?: "Something went wrong"))
                }
        }
    }

    private fun deleteExistingCard(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            deleteCard(id)
                .onSuccess {
                    _effect.send(VaultHomeEffect.CardDeleted)
                    loadCards()
                }
                .onFailure { err ->
                    _state.update { it.copy(isLoading = false) }
                    _effect.send(VaultHomeEffect.ShowError(err.message ?: "Something went wrong"))
                }
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            _effect.send(VaultHomeEffect.CloseDrawer)
            signOutUser()
                .onSuccess {
                    // The photo is personal: don't leave it on the device after sign-out.
                    clearProfilePhotoCache()
                    _effect.send(VaultHomeEffect.NavigateToLogin)
                }
                .onFailure { error ->
                    _effect.send(VaultHomeEffect.ShowError(error.message ?: "Sign out failed"))
                }
        }
    }
}
