package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.compose.runtime.Composable
import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto

/**
 * Not implemented on iOS yet: the photo API needs Entra sign-in, which iOS doesn't have, so
 * there is nowhere to upload to. Tapping the avatar does nothing.
 */
@Composable
internal actual fun rememberProfilePhotoPicker(onPicked: (PickedPhoto) -> Unit): () -> Unit = {}
