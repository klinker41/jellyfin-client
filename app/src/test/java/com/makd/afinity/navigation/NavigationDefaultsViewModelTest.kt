package com.makd.afinity.navigation

import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.common.DrawerLibrariesMode
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationDefaultsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `MainNavigationViewModel initial navigation values match expected defaults`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val preferencesRepository: PreferencesRepository =
                mockk(relaxed = true) {
                    every { getNavigationDrawerEnabledFlow() } returns emptyFlow()
                    every { getDrawerLibrariesModeFlow() } returns emptyFlow()
                    every { getSideSheetEnabledFlow() } returns emptyFlow()
                }
            val appDataRepository: AppDataRepository =
                mockk(relaxed = true) {
                    every { isInitialDataLoaded } returns MutableStateFlow(false)
                }
            val authRepository: AuthRepository =
                mockk(relaxed = true) { every { isAuthenticated } returns MutableStateFlow(false) }
            val sessionManager: SessionManager = mockk {
                every { currentSession } returns MutableStateFlow<Session?>(mockk(relaxed = true))
                every { isServerReachable } returns MutableStateFlow(false)
            }
            val offlineModeManager: OfflineModeManager = mockk {
                coEvery { isCurrentlyOffline() } returns true
            }
            val serverRepository: ServerRepository =
                mockk(relaxed = true) {
                    every { unsupportedServerVersion } returns MutableStateFlow(null)
                }

            val viewModel =
                MainNavigationViewModel(
                    appDataRepository = appDataRepository,
                    authRepository = authRepository,
                    jellyfinRepository = mockk(relaxed = true),
                    mediaRepository = mockk(relaxed = true),
                    watchlistRepository = mockk(relaxed = true),
                    jellyseerrRepository = mockk(relaxed = true),
                    audiobookshelfRepository = mockk(relaxed = true),
                    audiobookshelfPlayer = mockk(relaxed = true),
                    audiobookshelfPlaybackManager = mockk(relaxed = true),
                    musicPlaybackManager = mockk(relaxed = true),
                    themeMusicPlayer = mockk(relaxed = true),
                    castManager = mockk(relaxed = true),
                    liveTvRepository = mockk(relaxed = true),
                    offlineModeManager = offlineModeManager,
                    sessionManager = sessionManager,
                    preferencesRepository = preferencesRepository,
                    pendingNavigationManager = mockk(relaxed = true),
                    serverRepository = serverRepository,
                )

            assertTrue(viewModel.navigationDrawerEnabled.value)
            assertEquals(DrawerLibrariesMode.EACH, viewModel.drawerLibrariesMode.value)
            assertTrue(viewModel.sideSheetEnabled.value)
        }
}
