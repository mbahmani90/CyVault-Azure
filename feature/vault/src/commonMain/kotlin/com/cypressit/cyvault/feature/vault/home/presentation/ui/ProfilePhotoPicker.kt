package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.compose.runtime.Composable
import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto

/**
 * Remembers a launcher for the system photo picker. [onPicked] gets the chosen photo, not
 * read yet; nothing is called when the user cancels.
 */
@Composable
internal expect fun rememberProfilePhotoPicker(onPicked: (PickedPhoto) -> Unit): () -> Unit
