package com.makd.afinity.data.manager

import app.cash.turbine.test
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.util.NetworkConnectivityMonitor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadPermissionsTest {

    @Test
    fun `isAllowedByServer mirrors session canDownload policy`() =
        runTest(UnconfinedTestDispatcher()) {
            val sessionFlow = MutableStateFlow<Session?>(null)
            val sessionManager: SessionManager = mockk {
                every { currentSession } returns sessionFlow
            }
            val prefs: PreferencesRepository = mockk {
                every { getDownloadWifiOnlyFlow() } returns MutableStateFlow(false)
            }
            val networkMonitor: NetworkConnectivityMonitor = mockk {
                every { isOnWifiFlow } returns MutableStateFlow(true)
            }
            val permissions =
                DownloadPermissions(sessionManager, prefs, networkMonitor, backgroundScope)

            permissions.isAllowedByServer.test {
                assertTrue(awaitItem())

                sessionFlow.value =
                    Session("srv-1", testUuid(1), "https://jf.example.com", canDownload = false)
                assertFalse(awaitItem())

                sessionFlow.value =
                    Session("srv-1", testUuid(1), "https://jf.example.com", canDownload = true)
                assertTrue(awaitItem())
            }
        }

    @Test
    fun `isAllowedOnNetwork respects wifi-only preference and wifi transport state`() =
        runTest(UnconfinedTestDispatcher()) {
            val wifiOnlyFlow = MutableStateFlow(true)
            val onWifiFlow = MutableStateFlow(false)
            val sessionManager: SessionManager = mockk {
                every { currentSession } returns MutableStateFlow(null)
            }
            val prefs: PreferencesRepository = mockk {
                every { getDownloadWifiOnlyFlow() } returns wifiOnlyFlow
            }
            val networkMonitor: NetworkConnectivityMonitor = mockk {
                every { isOnWifiFlow } returns onWifiFlow
            }
            val permissions =
                DownloadPermissions(sessionManager, prefs, networkMonitor, backgroundScope)

            permissions.isAllowedOnNetwork.test {
                assertFalse(awaitItem())

                onWifiFlow.value = true
                assertTrue(awaitItem())

                onWifiFlow.value = false
                assertFalse(awaitItem())

                wifiOnlyFlow.value = false
                assertTrue(awaitItem())
            }
        }
}
