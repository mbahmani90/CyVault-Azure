package com.cypressit.cyvault.feature.vault.vaultlist.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.feature.vault.vaultlist.domain.usecase.DeleteCardUseCase
import com.cypressit.cyvault.feature.vault.vaultlist.domain.usecase.GetCardsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VaultListViewModel(
    private val getCards: GetCardsUseCase,
    private val deleteCard: DeleteCardUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(VaultListState())
    val state: StateFlow<VaultListState> = _state.asStateFlow()

    private val _effect = Channel<VaultListEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        loadCards()
    }

    fun onIntent(intent: VaultListIntent) {
        when (intent) {
            VaultListIntent.LoadCards -> loadCards()
            is VaultListIntent.DeleteCard -> deleteExistingCard(intent.id)
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
                    _effect.send(VaultListEffect.ShowError(err.message ?: "Something went wrong"))
                }
        }
    }

    private fun deleteExistingCard(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            deleteCard(id)
                .onSuccess {
                    _effect.send(VaultListEffect.CardDeleted)
                    loadCards()
                }
                .onFailure { err ->
                    _state.update { it.copy(isLoading = false) }
                    _effect.send(VaultListEffect.ShowError(err.message ?: "Something went wrong"))
                }
        }
    }
}
