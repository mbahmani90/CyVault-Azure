package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.core.presentation.MviViewModel
import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.model.CardInput
import com.cypressit.cyvault.feature.vault.device.domain.repository.SigningCancelledException
import com.cypressit.cyvault.feature.vault.device.domain.repository.SigningUnavailableException
import com.cypressit.cyvault.feature.vault.device.domain.usecase.EnrollDeviceUseCase
import com.cypressit.cyvault.feature.vault.home.domain.debug.NoCardsForSamplesException
import com.cypressit.cyvault.feature.vault.home.domain.debug.SampleBalancesSeeder
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceType
import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto
import com.cypressit.cyvault.feature.vault.home.domain.repository.InvalidTransferException
import com.cypressit.cyvault.feature.vault.home.domain.repository.NoCardForPhoneNumberException
import com.cypressit.cyvault.feature.vault.home.domain.repository.SigningLockedException
import com.cypressit.cyvault.feature.vault.home.domain.repository.TransferChallengeExpiredException
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
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class VaultHomeViewModel(
    private val getCards: GetCardsUseCase,
    private val observeCards: ObserveCardsUseCase,
    private val deleteCard: DeleteCardUseCase,
    private val getCurrentAccount: GetCurrentAccountUseCase,
    private val signOutUser: SignOutUseCase,
    private val getProfilePhoto: GetProfilePhotoUseCase,
    private val changeProfilePhoto: ChangeProfilePhotoUseCase,
    private val clearProfilePhotoCache: ClearProfilePhotoCacheUseCase,
    private val getBalances: GetBalancesUseCase,
    private val saveBalance: SaveBalanceUseCase,
    private val deleteBalance: DeleteBalanceUseCase,
    private val deleteBalances: DeleteBalancesUseCase,
    private val sendTransfer: SendTransferUseCase,
    private val enrollDevice: EnrollDeviceUseCase,
    private val transferPromptTexts: TransferPromptTexts,
    /** Null unless :feature:vault-debug is loaded, which only debug builds do. */
    private val sampleBalancesSeeder: SampleBalancesSeeder? = null,
) : ViewModel(), MviViewModel<VaultHomeState, VaultHomeIntent, VaultHomeEffect> {

    private val _state = MutableStateFlow(
        VaultHomeState(canAddSampleBalances = sampleBalancesSeeder != null),
    )
    override val state: StateFlow<VaultHomeState> = _state.asStateFlow()

    private val _effect = Channel<VaultHomeEffect>(Channel.BUFFERED)
    override val effect: Flow<VaultHomeEffect> = _effect.receiveAsFlow()

    // The signed-in user's Entra object ID: names their cached photo on the device.
    private var userId: String? = null

    // One ID per transfer: pressing Send again after a failure repeats the same transfer on the
    // API instead of sending the money twice. Changing what's sent starts a new one.
    private var pendingTransferId: String? = null

    // The ID of the new balance in the open dialog, kept until it saves: after a failure the first
    // try may still have reached the API, and saving again then replaces that row.
    private var pendingBalanceId: String? = null

    init {
        loadUser()
        loadBalances()
        collectCards()
        // Fetched once for the row names; the dialogs then use the shared list. Quietly leaves the
        // names out on failure: a dialog fetches again, and reports it, when it opens.
        viewModelScope.launch { getCards() }
    }

    override fun onIntent(intent: VaultHomeIntent) {
        when (intent) {
            VaultHomeIntent.LoadCards -> loadCards()
            VaultHomeIntent.Refresh -> refresh()
            is VaultHomeIntent.DeleteCard -> deleteExistingCard(intent.id)
            VaultHomeIntent.SignOut -> signOut()
            VaultHomeIntent.OpenDrawer -> viewModelScope.launch { _effect.send(VaultHomeEffect.OpenDrawer) }
            is VaultHomeIntent.ChangeProfilePhoto -> updateProfilePhoto(intent.photo)
            VaultHomeIntent.TransferClicked -> openTransfer()
            VaultHomeIntent.TransferDismissed -> dismissTransfer()
            is VaultHomeIntent.TransferPhoneNumberChanged ->
                editTransfer { it.copy(transferPhoneNumber = intent.value) }
            is VaultHomeIntent.TransferAmountChanged -> editTransfer { it.copy(transferAmount = intent.value) }
            is VaultHomeIntent.TransferCardMenuOpenChanged ->
                _state.update { it.copy(isTransferCardMenuOpen = intent.isOpen) }
            is VaultHomeIntent.TransferCardSelected -> editTransfer {
                it.copy(transferCardId = intent.cardId, isTransferCardMenuOpen = false)
            }
            is VaultHomeIntent.TransferCurrencyMenuOpenChanged ->
                _state.update { it.copy(isTransferCurrencyMenuOpen = intent.isOpen) }
            is VaultHomeIntent.TransferCurrencySelected -> editTransfer {
                it.copy(transferCurrency = intent.currency, isTransferCurrencyMenuOpen = false)
            }
            VaultHomeIntent.TransferSubmitted -> submitTransfer()
            VaultHomeIntent.AddCardFromDrawer -> viewModelScope.launch {
                _effect.send(VaultHomeEffect.CloseDrawer)
                _effect.send(VaultHomeEffect.NavigateToAddCard)
            }
            VaultHomeIntent.ShowAddItemDialog -> openItemDialog(editing = null)
            VaultHomeIntent.DismissAddItemDialog -> _state.update {
                it.copy(isAddItemDialogVisible = false, editingBalance = null, isItemCardMenuOpen = false)
            }
            is VaultHomeIntent.ItemCardMenuOpenChanged ->
                _state.update { it.copy(isItemCardMenuOpen = intent.isOpen) }
            is VaultHomeIntent.ItemCardSelected -> _state.update {
                it.copy(itemCardId = intent.cardId, isItemCardMenuOpen = false)
            }
            is VaultHomeIntent.SaveItem ->
                saveItem(intent.title, intent.description, intent.value, intent.currency, intent.date)
            is VaultHomeIntent.ClickBalance -> clickBalance(intent.balance)
            is VaultHomeIntent.LongClickBalance -> toggleBalanceSelection(intent.balance.id)
            VaultHomeIntent.RequestDeleteEditingBalance -> _state.update {
                // The confirmation replaces the edit dialog.
                it.copy(pendingDeleteBalance = it.editingBalance, isAddItemDialogVisible = false, editingBalance = null)
            }
            VaultHomeIntent.ConfirmDeleteBalance -> confirmDeleteBalance()
            VaultHomeIntent.DismissDeleteBalance -> _state.update { it.copy(pendingDeleteBalance = null) }
            VaultHomeIntent.SelectAllBalances -> selectAllBalances()
            VaultHomeIntent.ClearBalanceSelection -> _state.update { it.copy(selectedBalanceIds = emptySet()) }
            VaultHomeIntent.RequestDeleteSelectedBalances -> _state.update {
                it.copy(isDeleteSelectedConfirmVisible = it.isSelectionMode)
            }
            VaultHomeIntent.ConfirmDeleteSelectedBalances -> deleteSelectedBalances()
            VaultHomeIntent.DismissDeleteSelectedBalances ->
                _state.update { it.copy(isDeleteSelectedConfirmVisible = false) }
            VaultHomeIntent.AddSampleBalances -> addSamples()
        }
    }

    private fun loadBalances() {
        viewModelScope.launch { fetchBalances() }
    }

    private suspend fun fetchBalances() {
        _state.update { it.copy(isLoadingBalances = true) }
        getBalances()
            .onSuccess { balances ->
                _state.update { state ->
                    val ids = balances.mapTo(mutableSetOf()) { it.id }
                    state.copy(
                        balances = balances.newestFirst(),
                        isLoadingBalances = false,
                        // A selected balance that's gone can't stay selected.
                        selectedBalanceIds = state.selectedBalanceIds intersect ids,
                    )
                }
            }
            .onFailure {
                _state.update { it.copy(isLoadingBalances = false) }
                _effect.send(VaultHomeEffect.BalanceRequestFailed)
            }
    }

    // Fetches the balances and the cards (for the rows' bank names) side by side; the spinner stays
    // until both are back. A balance failure is reported like on start; a card failure is quiet,
    // as the rows just keep the names they had. A pull while one is running is ignored.
    private fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val cards = async { getCards() }
            fetchBalances()
            cards.await()
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun saveItem(title: String, description: String, value: Double, currency: String, date: String) {
        // Save is disabled until a card is chosen; the API rejects a balance without one.
        val cardId = _state.value.itemCard?.id ?: return
        val editingId = _state.value.editingBalance?.id
        val id = editingId ?: pendingBalanceId ?: Uuid.random().toHexString().also { pendingBalanceId = it }
        // The dialog signs the value by its Purchase/Income choice; the balance keeps it above zero.
        val balance = Balance(
            id = id,
            name = title,
            description = description,
            amount = abs(value),
            currency = currency,
            date = date,
            cardId = cardId,
            type = if (value < 0) BalanceType.Purchase else BalanceType.Income,
        )
        viewModelScope.launch {
            _state.update { it.copy(isSavingBalance = true) }
            saveBalance(balance, isNew = editingId == null)
                .onSuccess { saved ->
                    pendingBalanceId = null
                    _state.update { state ->
                        // A new date gives the balance a new ID, so the old row goes by its old one.
                        val others = state.balances.filterNot { it.id == editingId || it.id == saved.id }
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
                    _state.update { state ->
                        state.copy(
                            balances = state.balances.filterNot { it.id == id },
                            selectedBalanceIds = state.selectedBalanceIds - id,
                        )
                    }
                    _effect.send(VaultHomeEffect.BalanceDeleted)
                }
                .onFailure { _effect.send(VaultHomeEffect.BalanceRequestFailed) }
        }
    }

    private fun clickBalance(balance: Balance) {
        if (_state.value.isSelectionMode) {
            toggleBalanceSelection(balance.id)
        } else {
            openItemDialog(editing = balance)
        }
    }

    private fun confirmDeleteBalance() {
        val balance = _state.value.pendingDeleteBalance ?: return
        _state.update { it.copy(pendingDeleteBalance = null) }
        deleteExistingBalance(balance.id)
    }

    private fun toggleBalanceSelection(id: String) {
        _state.update { state ->
            val selected = state.selectedBalanceIds
            state.copy(selectedBalanceIds = if (id in selected) selected - id else selected + id)
        }
    }

    private fun selectAllBalances() {
        _state.update { state -> state.copy(selectedBalanceIds = state.balances.mapTo(mutableSetOf()) { it.id }) }
    }

    private fun deleteSelectedBalances() {
        val ids = _state.value.selectedBalanceIds
        _state.update { it.copy(isDeleteSelectedConfirmVisible = false) }
        if (ids.isEmpty() || _state.value.isDeletingBalances) return
        viewModelScope.launch {
            _state.update { it.copy(isDeletingBalances = true) }
            val deleted = deleteBalances(ids)
            // The ones that failed stay in the list and stay selected, so the user can retry.
            _state.update { state ->
                state.copy(
                    balances = state.balances.filterNot { it.id in deleted },
                    selectedBalanceIds = state.selectedBalanceIds - deleted,
                    isDeletingBalances = false,
                )
            }
            if (deleted.isNotEmpty()) _effect.send(VaultHomeEffect.BalancesDeleted(deleted.size))
            if (deleted.size < ids.size) _effect.send(VaultHomeEffect.BalanceRequestFailed)
        }
    }

    private fun addSamples() {
        val seeder = sampleBalancesSeeder ?: return
        if (_state.value.isAddingSampleBalances) return
        viewModelScope.launch {
            _effect.send(VaultHomeEffect.CloseDrawer)
            _state.update { it.copy(isAddingSampleBalances = true) }
            val result = seeder.addSampleBalances()
            _state.update { it.copy(isAddingSampleBalances = false) }
            // Reload either way: a failure part way through still saved some.
            loadBalances()
            result
                .onSuccess { _effect.send(VaultHomeEffect.SampleBalancesAdded(it)) }
                .onFailure {
                    _effect.send(
                        if (it is NoCardsForSamplesException) {
                            VaultHomeEffect.SampleBalancesNeedACard
                        } else {
                            VaultHomeEffect.BalanceRequestFailed
                        },
                    )
                }
        }
    }

    private fun loadUser() {
        viewModelScope.launch {
            val user = getCurrentAccount() ?: return@launch
            userId = user.id
            _state.update { it.copy(userName = user.name, userEmail = user.email) }
            // Quietly: a device that isn't enrolled yet, offline or without a biometric, is
            // tried again the next time the vault opens.
            launch { enrollDevice(user.id) }
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

    // A change to what's sent makes it a new transfer, and the last error was about the old one.
    private fun editTransfer(change: (VaultHomeState) -> VaultHomeState) {
        if (_state.value.isSendingTransfer) return
        pendingTransferId = null
        _state.update { change(it).copy(transferError = null) }
    }

    // While sending, the dialog stays open, so the result has somewhere to show.
    private fun dismissTransfer() {
        if (_state.value.isSendingTransfer) return
        pendingTransferId = null
        _state.update {
            it.copy(
                isTransferDialogVisible = false,
                transferPhoneNumber = "",
                transferAmount = "",
                isTransferCardMenuOpen = false,
                isTransferCurrencyMenuOpen = false,
                transferError = null,
            )
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun submitTransfer() {
        val current = _state.value
        if (!current.isTransferEnabled) return
        val cardId = current.transferCard?.id ?: return
        val phoneNumber = CardInput.normalizePhoneNumber(current.transferPhoneNumber) ?: return
        val amount = current.transferAmountValue ?: return
        val id = userId ?: return
        val transferId = pendingTransferId ?: Uuid.random().toHexString().also { pendingTransferId = it }

        viewModelScope.launch {
            _state.update { it.copy(isSendingTransfer = true, transferError = null) }
            // What the user approves with their fingerprint or face: this amount to this number.
            val prompt = transferPromptTexts.forTransfer(
                amount = current.transferAmount.trim(),
                currency = current.transferCurrency,
                phoneNumber = phoneNumber,
            )
            sendTransfer(id, transferId, cardId, phoneNumber, amount, current.transferCurrency, prompt)
                .onSuccess { sent ->
                    pendingTransferId = null
                    _state.update { state ->
                        state.copy(
                            // The sender's Transfer row; the receiver's appears in their own list.
                            balances = (state.balances.filterNot { it.id == sent.id } + sent).newestFirst(),
                            isSendingTransfer = false,
                            isTransferDialogVisible = false,
                            transferPhoneNumber = "",
                            transferAmount = "",
                        )
                    }
                    _effect.send(
                        VaultHomeEffect.TransferSent(
                            amount = current.transferAmount.trim(),
                            currency = current.transferCurrency,
                            phoneNumber = phoneNumber,
                        ),
                    )
                }
                .onFailure { error ->
                    // Shown in the dialog, which stays open with what the user typed. Backing out
                    // of the biometric prompt isn't an error: the dialog just waits for Send again.
                    val transferError = when (error) {
                        is SigningCancelledException -> null
                        is NoCardForPhoneNumberException -> TransferError.NoCardForPhoneNumber
                        is InvalidTransferException -> TransferError.Invalid
                        is SigningUnavailableException -> TransferError.SigningUnavailable
                        is TransferChallengeExpiredException -> TransferError.ChallengeExpired
                        is SigningLockedException -> TransferError.SigningLocked
                        else -> TransferError.Failed
                    }
                    _state.update { it.copy(isSendingTransfer = false, transferError = transferError) }
                }
        }
    }

    // Home's card lists come from the repository's shared one, so a card added or deleted on the
    // Cards screen shows here at once. A chosen card that's gone falls back to the first.
    private fun collectCards() {
        viewModelScope.launch {
            observeCards().collect { cards ->
                _state.update { state ->
                    state.copy(
                        itemCards = cards,
                        transferCards = cards,
                        itemCardId = state.itemCardId.keptOrFirst(cards),
                        transferCardId = state.transferCardId.keptOrFirst(cards),
                    )
                }
            }
        }
    }

    // Fetches the cards for a dialog that has none to show; collectCards takes the result.
    private fun fetchDialogCards(setLoading: VaultHomeState.(Boolean) -> VaultHomeState) {
        _state.update { it.setLoading(true) }
        viewModelScope.launch {
            val result = getCards()
            _state.update { it.setLoading(false) }
            if (result.isFailure) _effect.send(VaultHomeEffect.TransferCardsLoadFailed)
        }
    }

    // Opens the add-item dialog for a new balance, on the first card, or for [editing], on its own
    // card. Before the cards arrive the edited card's ID waits for them, and collectCards checks it.
    private fun openItemDialog(editing: Balance?) {
        pendingBalanceId = null
        _state.update {
            it.copy(
                isAddItemDialogVisible = true,
                editingBalance = editing,
                isItemCardMenuOpen = false,
                itemCardId = editing?.cardId.keptOrFirst(it.itemCards) ?: editing?.cardId,
            )
        }
        if (_state.value.itemCards.isEmpty()) fetchDialogCards { copy(isLoadingItemCards = it) }
    }

    // The dialog keeps its own loading flag: Home's isLoading would replace the whole page with a
    // spinner. It keeps the card chosen last time.
    private fun openTransfer() {
        _state.update { it.copy(isTransferDialogVisible = true) }
        if (_state.value.transferCards.isEmpty()) fetchDialogCards { copy(isLoadingTransferCards = it) }
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

/** This card ID if it's in [cards], else the first card's; null when there are no cards. */
private fun String?.keptOrFirst(cards: List<Card>): String? =
    this?.takeIf { id -> cards.any { it.id == id } } ?: cards.firstOrNull()?.id

/**
 * Newest date first, like the API returns them; balances without a readable date go last, and
 * the ID breaks ties so the order never jumps around.
 */
internal fun List<Balance>.newestFirst(): List<Balance> = sortedWith(
    compareByDescending<Balance> { balance -> balance.date?.let { runCatching { Instant.parse(it) }.getOrNull() } }
        .thenBy { it.id },
)
