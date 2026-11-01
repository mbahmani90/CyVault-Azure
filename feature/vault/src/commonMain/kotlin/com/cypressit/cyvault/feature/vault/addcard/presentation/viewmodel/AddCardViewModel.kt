package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.core.presentation.MviViewModel
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCardsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddCardViewModel(
    private val getCards: GetCardsUseCase,
) : ViewModel(), MviViewModel<AddCardState, AddCardIntent, AddCardEffect> {

    private val _state = MutableStateFlow(AddCardState())
    override val state: StateFlow<AddCardState> = _state.asStateFlow()

    private val _effect = Channel<AddCardEffect>(Channel.BUFFERED)
    override val effect: Flow<AddCardEffect> = _effect.receiveAsFlow()

    init {
        loadCards()
    }

    override fun onIntent(intent: AddCardIntent) {
        when (intent) {
            AddCardIntent.AddCardClicked -> _state.update { it.copy(isAddCardDialogVisible = true) }
            // A dismissed dialog starts empty next time.
            AddCardIntent.AddCardDialogDismissed -> _state.update {
                it.copy(isAddCardDialogVisible = false, cardNumber = "", phoneNumber = "")
            }
            is AddCardIntent.CardNumberChanged -> _state.update { it.copy(cardNumber = intent.value) }
            is AddCardIntent.PhoneNumberChanged -> _state.update { it.copy(phoneNumber = intent.value) }
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
