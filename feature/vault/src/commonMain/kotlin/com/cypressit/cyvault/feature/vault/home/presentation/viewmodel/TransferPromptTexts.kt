package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.device.domain.repository.ApprovalPrompt
import cyvault.vault.generated.resources.Res
import cyvault.vault.generated.resources.transfer_approve_description
import cyvault.vault.generated.resources.transfer_approve_title
import cyvault.vault.generated.resources.transfer_cancel
import org.jetbrains.compose.resources.getString

/**
 * The biometric prompt's text for approving a transfer: the system shows it, not a composable, so
 * the ViewModel builds it. String resources in the app, fixed text in tests.
 */
fun interface TransferPromptTexts {
    suspend fun forTransfer(amount: String, currency: String, phoneNumber: String): ApprovalPrompt
}

/** Reads the prompt's text from the vault's string resources, in the user's language. */
class ResourceTransferPromptTexts : TransferPromptTexts {
    override suspend fun forTransfer(amount: String, currency: String, phoneNumber: String) = ApprovalPrompt(
        title = getString(Res.string.transfer_approve_title),
        description = getString(Res.string.transfer_approve_description, amount, currency, phoneNumber),
        cancelLabel = getString(Res.string.transfer_cancel),
    )
}
