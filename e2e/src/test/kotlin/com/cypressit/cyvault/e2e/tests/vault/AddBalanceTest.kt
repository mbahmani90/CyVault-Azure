package com.cypressit.cyvault.e2e.tests.vault

import com.cypressit.cyvault.e2e.fixtures.AppiumSession
import com.cypressit.cyvault.e2e.fixtures.signedIn
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class AddBalanceTest {
    @get:Rule
    val session = AppiumSession()

    @Test
    fun addedBalanceShowsOnHome() {
        // Unique per run, so an item left over from an earlier run can't make this pass.
        val title = "E2E ${System.currentTimeMillis()}"

        val home = session.signedIn()
        assertTrue(home.isShown(), "Home screen did not appear after sign-in")

        val dialog = home.tapAddItem()
        assertTrue(dialog.isShown(), "Add Item dialog did not open")

        dialog.enterTitle(title).enterDescription("Added by AddBalanceTest").enterValue("12.50").tapSave()

        assertTrue(dialog.isClosed(), "Add Item dialog did not close after Save")
        assertTrue(home.isBalanceShown(title), "New balance \"$title\" is not on the home screen")
    }
}
