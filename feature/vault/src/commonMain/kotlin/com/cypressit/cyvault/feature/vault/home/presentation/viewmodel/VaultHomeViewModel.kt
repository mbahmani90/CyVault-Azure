package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.core.session.SessionManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class VaultHomeViewModel(
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _effect = Channel<VaultHomeEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onIntent(intent: VaultHomeIntent) {
        when (intent) {
            VaultHomeIntent.SignOut -> signOut()
            VaultHomeIntent.OpenDrawer -> viewModelScope.launch { _effect.send(VaultHomeEffect.OpenDrawer) }
            VaultHomeIntent.NavigateBack -> viewModelScope.launch { _effect.send(VaultHomeEffect.NavigateBack) }
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
