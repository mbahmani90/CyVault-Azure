package com.cypressit.cyvault.core.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The contract every screen's ViewModel follows: the screen renders [state], reacts once to each
 * [effect] (navigation, snackbars) and sends every user action through [onIntent].
 */
interface MviViewModel<S, I, E> {
    val state: StateFlow<S>
    val effect: Flow<E>
    fun onIntent(intent: I)
}
