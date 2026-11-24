package com.makd.afinity.data.manager

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingNavigationManagerTest {

    @Test
    fun `navigateTo and consume update pendingRoute`() {
        val manager = PendingNavigationManager()
        assertNull(manager.pendingRoute.value)

        manager.navigateTo("settings")
        assertEquals("settings", manager.pendingRoute.value)

        manager.consume()
        assertNull(manager.pendingRoute.value)
    }

    @Test
    fun `requestHome emits repeatedly on homeRequested`() = runTest {
        val manager = PendingNavigationManager()

        manager.homeRequested.test {
            manager.requestHome()
            assertEquals(Unit, awaitItem())

            manager.requestHome()
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
