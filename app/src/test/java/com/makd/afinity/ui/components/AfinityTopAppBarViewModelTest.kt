package com.makd.afinity.ui.components

import com.makd.afinity.data.manager.Connectivity
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AfinityTopAppBarViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun exposesOfflineManagerFlowsAndHandlesRetryAndGoOnlineIncludingReentrancyAndFailures() =
        runTest(mainDispatcherRule.testDispatcher) {
            val connectionTypeFlow = MutableStateFlow(ConnectionType.OFFLINE)
            val connectivityFlow = MutableStateFlow<Connectivity>(Connectivity.ForcedOffline)
            val offlineModeManager: OfflineModeManager = mockk {
                every { connectionType } returns connectionTypeFlow
                every { connectivity } returns connectivityFlow
            }

            val reconnectGate = CompletableDeferred<Boolean>()
            val serverRepository: ServerRepository = mockk {
                coEvery { forceReconnect() } coAnswers { reconnectGate.await() }
            }
            val preferencesRepository: PreferencesRepository = mockk {
                coEvery { setOfflineMode(any()) } returns Unit
            }

            val viewModel =
                AfinityTopAppBarViewModel(
                    offlineModeManager,
                    serverRepository,
                    preferencesRepository,
                )

            assertSame(connectionTypeFlow, viewModel.connectionType)
            assertSame(connectivityFlow, viewModel.connectivity)
            assertFalse(viewModel.isRetrying.value)

            // Re-entrant calls while in flight are ignored.
            viewModel.retryConnection()
            assertTrue(viewModel.isRetrying.value)
            viewModel.retryConnection()
            viewModel.goOnline()

            reconnectGate.complete(true)
            assertFalse(viewModel.isRetrying.value)
            coVerify(exactly = 1) { serverRepository.forceReconnect() }
            coVerify(exactly = 0) { preferencesRepository.setOfflineMode(false) }

            // goOnline disables manual offline mode and resets isRetrying in finally even on
            // cancellation.
            coEvery { serverRepository.forceReconnect() } throws CancellationException("cancelled")
            viewModel.goOnline()
            assertFalse(viewModel.isRetrying.value)
            coVerify(exactly = 1) { preferencesRepository.setOfflineMode(false) }
            assertEquals(ConnectionType.OFFLINE, viewModel.connectionType.value)
        }
}
