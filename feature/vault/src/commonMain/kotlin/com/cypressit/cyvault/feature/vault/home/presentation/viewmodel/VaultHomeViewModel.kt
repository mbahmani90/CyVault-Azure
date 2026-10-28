package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class VaultHomeViewModel(
    private val getCards: GetCardsUseCase,
    private val deleteCard: DeleteCardUseCase,
    private val getCurrentAccount: GetCurrentAccountUseCase,
    private val signOutUser: SignOutUseCase,
    private val getProfilePhoto: GetProfilePhotoUseCase,
    private val changeProfilePhoto: ChangeProfilePhotoUseCase,
    private val clearProfilePhotoCache: ClearProfilePhotoCacheUseCase,
    private val getBalances: GetBalancesUseCase,
    private val saveBalance: SaveBalanceUseCase,
    private val deleteBalance: DeleteBalanceUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(VaultHomeState())
    val state: StateFlow<VaultHomeState> = _state.asStateFlow()

    private val _effect = Channel<VaultHomeEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    // The signed-in user's Entra object ID: names their cached photo on the device.
    private var userId: String? = null

    init {
        loadUser()
        // Cards aren't loaded on start: CardApiService still points to a placeholder host that
        // doesn't exist, so every start showed an error. Load them again once cards move to Azure.
        loadBalances()
    }

    fun onIntent(intent: VaultHomeIntent) {
        when (intent) {
            VaultHomeIntent.LoadCards -> loadCards()
            is VaultHomeIntent.DeleteCard -> deleteExistingCard(intent.id)
            VaultHomeIntent.SignOut -> signOut()
            VaultHomeIntent.OpenDrawer -> viewModelScope.launch { _effect.send(VaultHomeEffect.OpenDrawer) }
            is VaultHomeIntent.ChangeProfilePhoto -> updateProfilePhoto(intent.photo)
            VaultHomeIntent.AddCardFromDrawer -> viewModelScope.launch {
                _effect.send(VaultHomeEffect.CloseDrawer)
                _effect.send(VaultHomeEffect.NavigateToAddCard)
            }
            VaultHomeIntent.ShowAddItemDialog ->
                _state.update { it.copy(isAddItemDialogVisible = true, editingBalance = null) }
            VaultHomeIntent.DismissAddItemDialog ->
                _state.update { it.copy(isAddItemDialogVisible = false, editingBalance = null) }
            is VaultHomeIntent.SaveItem ->
                saveItem(intent.title, intent.description, intent.value, intent.currency, intent.date)
            is VaultHomeIntent.EditBalance ->
                _state.update { it.copy(isAddItemDialogVisible = true, editingBalance = intent.balance) }
            is VaultHomeIntent.DeleteBalance -> deleteExistingBalance(intent.id)
        }
    }

    private fun loadBalances() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingBalances = true) }
            getBalances()
                .onSuccess { balances ->
                    _state.update { it.copy(balances = balances.newestFirst(), isLoadingBalances = false) }
                }
                .onFailure {
                    _state.update { it.copy(isLoadingBalances = false) }
                    _effect.send(VaultHomeEffect.BalanceRequestFailed)
                }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun saveItem(title: String, description: String, value: Double, currency: String, date: String) {
        // Editing keeps the row's ID; a new balance gets a random one.
        val id = _state.value.editingBalance?.id ?: Uuid.random().toHexString()
        val balance = Balance(
            id = id,
            name = title,
            description = description,
            amount = value,
            currency = currency,
            date = date,
        )
        viewModelScope.launch {
            _state.update { it.copy(isSavingBalance = true) }
            saveBalance(balance)
                .onSuccess { saved ->
                    _state.update { state ->
                        val others = state.balances.filterNot { it.id == saved.id }
                        state.copy(
                            balances = (others + saved).newestFirst(),
                            isSavingBalance = false,
                            isAddItemDialogVisible = false,
                            editingBalance = null,
                        )
                    }
                    _effect.send(VaultHomeEffect.BalanceSaved)
                }
                .onFailure {
                    // The dialog stays open with what the user typed, so they can retry.
                    _state.update { it.copy(isSavingBalance = false) }
                    _effect.send(VaultHomeEffect.BalanceRequestFailed)
                }
        }
    }

    private fun deleteExistingBalance(id: String) {
        viewModelScope.launch {
            deleteBalance(id)
                .onSuccess {
                    _state.update { state -> state.copy(balances = state.balances.filterNot { it.id == id }) }
                    _effect.send(VaultHomeEffect.BalanceDeleted)
                }
                .onFailure { _effect.send(VaultHomeEffect.BalanceRequestFailed) }
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

/**
 * Newest date first, like the API returns them; balances without a readable date go last, and
 * the ID breaks ties so the order never jumps around.
 */
internal fun List<Balance>.newestFirst(): List<Balance> = sortedWith(
    compareByDescending<Balance> { balance -> balance.date?.let { runCatching { Instant.parse(it) }.getOrNull() } }
        .thenBy { it.id }
)
