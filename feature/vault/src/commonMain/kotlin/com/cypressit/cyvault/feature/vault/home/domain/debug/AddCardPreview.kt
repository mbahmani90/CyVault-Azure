package com.cypressit.cyvault.feature.vault.home.domain.debug

/**
 * Turns on Add Card, which isn't ready for users: its API is still a placeholder. Only
 * :feature:vault-debug provides it, and only debug builds load that module; without it, the
 * drawer hides Add Card.
 */
data object AddCardPreview
