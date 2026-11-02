package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.feature.vault.device.domain.repository.ApprovalPrompt
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.TransferPromptTexts

/** Fixed English text, so tests don't need the string resources. */
val fixedTransferPromptTexts = TransferPromptTexts { amount, currency, phoneNumber ->
    ApprovalPrompt(
        title = "Approve transfer",
        description = "Send $amount $currency to $phoneNumber",
        cancelLabel = "Cancel",
    )
}
