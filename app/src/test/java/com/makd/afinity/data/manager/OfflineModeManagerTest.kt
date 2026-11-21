package com.makd.afinity.data.manager

import app.cash.turbine.test
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.util.Locality
import com.makd.afinity.util.NetworkConnectivityMonitor
import com.makd.afinity.util.NetworkLocality
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineModeManagerTest {

    private class Harness(
        initialManualOffline: Boolean = false,
        initialNetworkAvailable: Boolean = true,
        initialServerReachable: Boolean = true,
        initialReason: UnreachableReason? = null,
        initialSessionUrl: String? = "https://jellyfin.example.com",
    ) {
        val manualOfflineFlow = MutableStateFlow(initialManualOffline)
        val networkAvailableFlow = MutableStateFlow(initialNetworkAvailable)
        val serverReachableFlow = MutableStateFlow(initialServerReachable)
        val unreachableReasonFlow = MutableStateFlow(initialReason)
        val sessionFlow =
            MutableStateFlow(
                initialSessionUrl?.let {
                    Session(serverId = "srv-1", userId = testUuid(1), serverUrl = it)
                },
            )

        val preferencesRepository: PreferencesRepository = mockk {
            every { getOfflineModeFlow() } returns manualOfflineFlow
            coEvery { getOfflineMode() } answers { manualOfflineFlow.value }
        }
        var validatedInternet = true
        val networkMonitor: NetworkConnectivityMonitor = mockk {
            every { isNetworkAvailable } returns networkAvailableFlow
            every { isCurrentlyConnected() } answers { networkAvailableFlow.value }
            coEvery { hasValidatedInternet() } answers { validatedInternet }
        }
        val sessionManager: SessionManager = mockk {
            every { isServerReachable } returns serverReachableFlow
            every { unreachableReason } returns unreachableReasonFlow
            every { currentSession } returns sessionFlow
        }
        var localityResult: Locality = Locality.PUBLIC
        val networkLocality: NetworkLocality = mockk {
            coEvery { resolve(any()) } answers { localityResult }
        }
    }

    @Test
    fun connectivityEnforcesPrecedenceAcrossOfflineReasons() =
        runTest(UnconfinedTestDispatcher()) {
            val h = Harness()
            val manager =
                OfflineModeManager(
                    h.preferencesRepository,
                    h.networkMonitor,
                    h.sessionManager,
                    h.networkLocality,
                    backgroundScope,
                )

            manager.connectivity.test {
                assertEquals(Connectivity.Online, awaitItem())

                h.serverReachableFlow.value = false
                assertEquals(
                    Connectivity.ServerUnreachable(UnreachableReason.ALL_ADDRESSES_FAILED),
                    awaitItem(),
                )

                h.unreachableReasonFlow.value = UnreachableReason.NO_ROUTE
                assertEquals(
                    Connectivity.ServerUnreachable(UnreachableReason.NO_ROUTE),
                    awaitItem(),
                )

                h.networkAvailableFlow.value = false
                assertEquals(Connectivity.NoNetwork, awaitItem())

                h.manualOfflineFlow.value = true
                assertEquals(Connectivity.ForcedOffline, awaitItem())

                h.manualOfflineFlow.value = false
                assertEquals(Connectivity.NoNetwork, awaitItem())

                h.networkAvailableFlow.value = true
                assertEquals(
                    Connectivity.ServerUnreachable(UnreachableReason.NO_ROUTE),
                    awaitItem(),
                )
                h.serverReachableFlow.value = true
                assertEquals(Connectivity.Online, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun connectionTypeReflectsLocalityOfflineAndUnknownFallbacks() =
        runTest(UnconfinedTestDispatcher()) {
            val h = Harness(initialSessionUrl = "http://192.168.1.50:8096")
            h.localityResult = Locality.ON_LINK
            val manager =
                OfflineModeManager(
                    h.preferencesRepository,
                    h.networkMonitor,
                    h.sessionManager,
                    h.networkLocality,
                    backgroundScope,
                )

            manager.connectionType.test {
                assertEquals(ConnectionType.LOCAL, awaitItem())

                h.localityResult = Locality.TAILSCALE
                h.sessionFlow.value = Session("srv-1", testUuid(1), "http://100.64.1.2:8096")
                assertEquals(ConnectionType.TAILSCALE, awaitItem())

                h.localityResult = Locality.TUNNELLED
                h.sessionFlow.value = Session("srv-1", testUuid(1), "https://vpn.example.com")
                assertEquals(ConnectionType.VPN, awaitItem())

                h.localityResult = Locality.PUBLIC
                h.sessionFlow.value = Session("srv-1", testUuid(1), "https://public.example.com")
                assertEquals(ConnectionType.REMOTE, awaitItem())

                // When UNKNOWN hits after a known resolution, lastKnownConnectionType is preserved.
                h.localityResult = Locality.UNKNOWN
                h.sessionFlow.value = Session("srv-1", testUuid(1), "https://other.example.com")
                expectNoEvents()

                h.manualOfflineFlow.value = true
                assertEquals(ConnectionType.OFFLINE, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun isCurrentlyOfflineAndIsInternetAvailableCheckAllSignals() =
        runTest(UnconfinedTestDispatcher()) {
            val h = Harness()
            val manager =
                OfflineModeManager(
                    h.preferencesRepository,
                    h.networkMonitor,
                    h.sessionManager,
                    h.networkLocality,
                    backgroundScope,
                )

            assertFalse(manager.isCurrentlyOffline())
            assertTrue(manager.isInternetAvailable())

            h.validatedInternet = false
            assertFalse(manager.isInternetAvailable())

            h.validatedInternet = true
            h.manualOfflineFlow.value = true
            assertTrue(manager.isCurrentlyOffline())
            assertFalse(manager.isInternetAvailable())

            h.manualOfflineFlow.value = false
            h.networkAvailableFlow.value = false
            assertTrue(manager.isCurrentlyOffline())

            h.networkAvailableFlow.value = true
            h.serverReachableFlow.value = false
            assertTrue(manager.isCurrentlyOffline())
        }
}
