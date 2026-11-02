package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.feature.vault.device.data.local.DeviceIdStore

class FakeDeviceIdStore : DeviceIdStore {

    val ids = mutableMapOf<String, String>()

    override suspend fun deviceId(userId: String): String? = ids[userId]

    override suspend fun save(userId: String, deviceId: String) {
        ids[userId] = deviceId
    }

    override suspend fun clear(userId: String) {
        ids.remove(userId)
    }
}
