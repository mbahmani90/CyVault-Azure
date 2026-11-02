package com.cypressit.cyvault.feature.vault.device.data.local

import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeFalse
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataStoreDeviceIdStoreTest {

    // One directory per test: a DataStore file may only have one instance open.
    private val directory: File = createTempDirectory("device-ids").toFile()
    private val store = DataStoreDeviceIdStore(deviceIdDataStore(directory.absolutePath))

    // DataStore on the JVM saves by renaming its temp file over the old one, which Windows refuses
    // once the file exists: a second write fails there. Android and CI (Linux) rename fine.
    @BeforeTest fun skipOnWindows() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
    }

    @AfterTest fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun deviceId_beforeSaving_isNull() = runTest {
        assertNull(store.deviceId("user-1"))
    }

    @Test
    fun save_keepsOneIdPerAccount() = runTest {
        store.save("user-1", "device-1")
        store.save("user-2", "device-2")

        assertEquals("device-1", store.deviceId("user-1"))
        assertEquals("device-2", store.deviceId("user-2"))
    }

    @Test
    fun clear_removesOnlyThatAccountsId() = runTest {
        store.save("user-1", "device-1")
        store.save("user-2", "device-2")

        store.clear("user-1")

        assertNull(store.deviceId("user-1"))
        assertEquals("device-2", store.deviceId("user-2"))
    }
}
