package com.cypressit.cyvault.feature.vault.device.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath

/**
 * The device ID the API gave each account's device key. It isn't secret (the key is, and stays in
 * the Keystore or Secure Enclave), so it's kept in app-private storage.
 */
interface DeviceIdStore {
    suspend fun deviceId(userId: String): String?

    suspend fun save(userId: String, deviceId: String)

    suspend fun clear(userId: String)
}

/** Keeps the IDs in a Preferences DataStore. Only one may exist per file: Koin keeps it a single. */
class DataStoreDeviceIdStore(private val dataStore: DataStore<Preferences>) : DeviceIdStore {

    override suspend fun deviceId(userId: String): String? = dataStore.data.first()[keyFor(userId)]

    override suspend fun save(userId: String, deviceId: String) {
        dataStore.edit { it[keyFor(userId)] = deviceId }
    }

    override suspend fun clear(userId: String) {
        dataStore.edit { it.remove(keyFor(userId)) }
    }

    private fun keyFor(userId: String) = stringPreferencesKey("device-id-$userId")
}

/** The DataStore file in [directory], app-private storage that each platform picks. */
internal fun deviceIdDataStore(directory: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { "$directory/$DEVICE_ID_FILE".toPath() })

private const val DEVICE_ID_FILE = "device_ids.preferences_pb"
