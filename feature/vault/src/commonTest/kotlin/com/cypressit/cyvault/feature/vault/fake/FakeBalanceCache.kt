package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.feature.vault.home.data.local.BalanceCache

class FakeBalanceCache : BalanceCache {
    /** When set, clearing fails with this. */
    var failure: Exception? = null
    var clearCount = 0

    override suspend fun clear() {
        failure?.let { throw it }
        clearCount++
    }
}
