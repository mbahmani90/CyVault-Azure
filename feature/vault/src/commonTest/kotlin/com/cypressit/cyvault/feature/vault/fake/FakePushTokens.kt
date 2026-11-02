package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.push.PushTokens

/** A phone's push token; null by default, like a phone without Google Play services. */
class FakePushTokens(var token: String? = null) : PushTokens {

    var deleteCount = 0

    override suspend fun current(): String? = token

    override suspend fun delete() {
        deleteCount++
        token = null
    }
}
