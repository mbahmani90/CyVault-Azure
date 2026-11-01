package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.core.presentation.MviViewModel
import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.CreateCardUseCase
import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.DeleteCardsUseCase
import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.model.CardInput
import com.cypressit.cyvault.feature.vault.card.domain.repository.InvalidCardException
import com.cypressit.cyvault.feature.vault.card.domain.repository.PhoneNumberAlreadyLinkedException
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCardsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AddCardViewModel(
    private val getCards: GetCardsUseCase,
    private val createCard: CreateCardUseCase,
    private val deleteCards: DeleteCardsUseCase,
) : ViewModel(), MviViewModel<AddCardState, AddCardIntent, AddCardEffect> {

    private val _state = MutableStateFlow(AddCardState())
    override val state: StateFlow<AddCardState> = _state.asStateFlow()

    private val _effect = Channel<AddCardEffect>(Channel.BUFFERED)
    override val effect: Flow<AddCardEffect> = _effect.receiveAsFlow()

    // One ID per opened dialog: pressing Add again after a failure updates the same card on the
    // API instead of adding a second one.
    private var pendingCardId: String? = null

    init {
        loadCards()
    }

    override fun onIntent(intent: AddCardIntent) {
        when (intent) {
            AddCardIntent.AddCardClicked -> _state.update { it.copy(isAddCardDialogVisible = true) }
            AddCardIntent.AddCardDialogDismissed -> dismissDialog()
            // Editing a field clears the last save error: it was about the old input.
            is AddCardIntent.CardNumberChanged ->
                _state.update { it.copy(cardNumber = intent.value, saveError = null) }
            is AddCardIntent.PhoneNumberChanged ->
                _state.update { it.copy(phoneNumber = intent.value, saveError = null) }
            // Capped as typed, so the API's length limit is never the reason a save fails.
            is AddCardIntent.BankNameChanged -> _state.update {
                it.copy(bankName = intent.value.take(CardInput.MAX_BANK_NAME_LENGTH), saveError = null)
            }
            AddCardIntent.AddCardSubmitted -> submit()
            is AddCardIntent.ClickCard -> clickCard(intent.card)
            is AddCardIntent.LongClickCard -> toggleCardSelection(intent.card.id)
            AddCardIntent.CardDetailsDismissed -> _state.update { it.copy(viewingCard = null) }
            AddCardIntent.RequestDeleteViewingCard -> _state.update {
                it.copy(pendingDeleteCard = it.viewingCard, viewingCard = null)
            }
            AddCardIntent.ConfirmDeleteCard -> confirmDeleteCard()
            AddCardIntent.DismissDeleteCard -> _state.update { it.copy(pendingDeleteCard = null) }
            AddCardIntent.ClearCardSelection -> _state.update { it.copy(selectedCardIds = emptySet()) }
            AddCardIntent.SelectAllCards -> _state.update { state ->
                state.copy(selectedCardIds = state.cards.mapTo(mutableSetOf()) { it.id })
            }
            AddCardIntent.RequestDeleteSelectedCards ->
                _state.update { it.copy(isDeleteSelectedConfirmVisible = true) }
            AddCardIntent.ConfirmDeleteSelectedCards -> {
                _state.update { it.copy(isDeleteSelectedConfirmVisible = false) }
                delete(_state.value.selectedCardIds)
            }
            AddCardIntent.DismissDeleteSelectedCards ->
                _state.update { it.copy(isDeleteSelectedConfirmVisible = false) }
        }
    }

    private fun clickCard(card: Card) {
        if (_state.value.isSelectionMode) {
            toggleCardSelection(card.id)
        } else {
            _state.update { it.copy(viewingCard = card) }
        }
    }

    private fun toggleCardSelection(id: String) {
        _state.update { state ->
            val selected = state.selectedCardIds
            state.copy(selectedCardIds = if (id in selected) selected - id else selected + id)
        }
    }

    private fun confirmDeleteCard() {
        val card = _state.value.pendingDeleteCard ?: return
        _state.update { it.copy(pendingDeleteCard = null) }
        delete(setOf(card.id))
    }

    private fun delete(ids: Set<String>) {
        if (ids.isEmpty() || _state.value.isDeletingCards) return
        viewModelScope.launch {
            _state.update { it.copy(isDeletingCards = true) }
            val deleted = deleteCards(ids)
            // The ones that failed stay in the list and stay selected, so the user can retry.
            _state.update { state ->
                state.copy(
                    cards = state.cards.filterNot { it.id in deleted },
                    selectedCardIds = state.selectedCardIds - deleted,
                    isDeletingCards = false,
                )
            }
            if (deleted.isNotEmpty()) _effect.send(AddCardEffect.CardsDeleted(deleted.size))
            if (deleted.size < ids.size) _effect.send(AddCardEffect.CardsDeleteFailed)
        }
    }

    // A dismissed dialog starts empty next time. While saving it stays open, so the result has
    // somewhere to show.
    private fun dismissDialog() {
        if (_state.value.isSavingCard) return
        pendingCardId = null
        _state.update {
            it.copy(
                isAddCardDialogVisible = false,
                cardNumber = "",
                phoneNumber = "",
                bankName = "",
                saveError = null,
            )
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun submit() {
        val current = _state.value
        if (!current.isAddEnabled) return
        val id = pendingCardId ?: Uuid.random().toHexString().also { pendingCardId = it }

        viewModelScope.launch {
            _state.update { it.copy(isSavingCard = true, saveError = null) }
            createCard(
                id = id,
                cardNumber = current.cardNumber,
                phoneNumber = current.phoneNumber,
                bankName = current.bankName.trim(),
            )
                .onSuccess { card ->
                    pendingCardId = null
                    _state.update {
                        it.copy(
                            cards = listOf(card) + it.cards.filterNot { existing -> existing.id == card.id },
                            isSavingCard = false,
                            isAddCardDialogVisible = false,
                            cardNumber = "",
                            phoneNumber = "",
                            bankName = "",
                        )
                    }
                    _effect.send(AddCardEffect.CardAdded)
                }
                .onFailure { error ->
                    // Shown in the dialog, which stays open so the user can fix the input.
                    val saveError = when (error) {
                        is PhoneNumberAlreadyLinkedException -> AddCardSaveError.PhoneNumberAlreadyLinked
                        is InvalidCardException -> AddCardSaveError.InvalidCard
                        else -> AddCardSaveError.Failed
                    }
                    _state.update { it.copy(isSavingCard = false, saveError = saveError) }
                }
        }
    }

    private fun loadCards() {
        viewModelScope.launch {
            _state.update { it.copy(isCardsLoading = true) }
            getCards()
                .onSuccess { cards -> _state.update { it.copy(cards = cards, isCardsLoading = false) } }
                .onFailure {
                    _state.update { it.copy(isCardsLoading = false) }
                    _effect.send(AddCardEffect.CardsLoadFailed)
                }
        }
    }
}
