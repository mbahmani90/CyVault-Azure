package com.cypressit.cyvault.feature.vault.home.domain.model

/**
 * A photo the user chose in the system picker, not read yet. [reference] is the platform's
 * handle to it (a content URI on Android); only the data layer knows how to open it.
 */
data class PickedPhoto(val reference: String)
