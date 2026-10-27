package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.core.session.SessionManager
import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteCardUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCardsUseCase
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
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(VaultHomeState())
    val state: StateFlow<VaultHomeState> = _state.asStateFlow()

    private val _effect = Channel<VaultHomeEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

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
        }
    }

    private fun loadUser() {
        viewModelScope.launch {
            val user = sessionManager.currentUser() ?: return@launch
            _state.update { it.copy(userName = user.name, userEmail = user.email) }
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
            sessionManager.signOut()
                .onSuccess {
                    _effect.send(VaultHomeEffect.NavigateToLogin)
                }
                .onFailure { error ->
                    _effect.send(VaultHomeEffect.ShowError(error.message ?: "Sign out failed"))
                }
        }
    }
}
