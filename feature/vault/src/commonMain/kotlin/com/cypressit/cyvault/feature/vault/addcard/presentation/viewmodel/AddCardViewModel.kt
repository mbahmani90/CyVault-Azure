package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.core.presentation.MviViewModel
import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.CreateCardUseCase
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
            AddCardIntent.AddCardSubmitted -> submit()
        }
    }

    // A dismissed dialog starts empty next time. While saving it stays open, so the result has
    // somewhere to show.
    private fun dismissDialog() {
        if (_state.value.isSavingCard) return
        pendingCardId = null
        _state.update {
            it.copy(isAddCardDialogVisible = false, cardNumber = "", phoneNumber = "", saveError = null)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun submit() {
        val current = _state.value
        if (!current.isAddEnabled) return
        val id = pendingCardId ?: Uuid.random().toHexString().also { pendingCardId = it }

        viewModelScope.launch {
            _state.update { it.copy(isSavingCard = true, saveError = null) }
            createCard(id = id, cardNumber = current.cardNumber, phoneNumber = current.phoneNumber)
                .onSuccess { card ->
                    pendingCardId = null
                    _state.update {
                        it.copy(
                            cards = listOf(card) + it.cards.filterNot { existing -> existing.id == card.id },
                            isSavingCard = false,
                            isAddCardDialogVisible = false,
                            cardNumber = "",
                            phoneNumber = "",
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
