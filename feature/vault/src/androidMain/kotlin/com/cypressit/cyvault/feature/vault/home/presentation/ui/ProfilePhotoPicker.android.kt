package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto

@Composable
internal actual fun rememberProfilePhotoPicker(onPicked: (PickedPhoto) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)

    // The system Photo Picker: no storage permission, and the app only sees the chosen photo.
    // The photo is only referenced here; the data layer reads it off the main thread.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) currentOnPicked(PickedPhoto(uri.toString()))
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}
