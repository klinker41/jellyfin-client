package com.makd.afinity.ui.settings

import android.content.Context
import app.cash.turbine.test
import com.makd.afinity.data.manager.Connectivity
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.HomeRow
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.auth.QuickConnectAuthorization
import com.makd.afinity.data.models.common.CardSize
import com.makd.afinity.data.models.common.DetailLayout
import com.makd.afinity.data.models.common.DrawerLibrariesMode
import com.makd.afinity.data.models.common.EpisodeLayout
import com.makd.afinity.data.models.common.HeroStyle
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.data.models.player.SkipMode
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.subtitles.DeviceSubtitleStats
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.network.LastFmApiService
import com.makd.afinity.data.network.MdbListApiService
import com.makd.afinity.data.network.OmdbApiService
import com.makd.afinity.data.network.TmdbApiService
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.DeletedItemsRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.home.HomeLayoutPreferencesRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.data.repository.subtitles.DeviceSubtitleStore
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlayer
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.util.NetworkConnectivityMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jellyfin.sdk.model.api.InstallationInfo
import org.jellyfin.sdk.model.api.PluginInfo
import org.jellyfin.sdk.model.api.PluginStatus
import org.jellyfin.sdk.model.api.SystemInfo
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class Harness {
        val userId = testUuid(1)
        val server =
            Server(
                id = "srv-1",
                name = "Home Jellyfin",
                address = "https://jf.example.com",
                version = "10.10.3",
            )
        val user = User(id = userId, name = "Alex", serverId = "srv-1", isAdmin = true)

        val currentUserFlow = MutableStateFlow<User?>(user)
        val currentServerFlow = MutableStateFlow<Server?>(server)
        val currentSessionFlow =
            MutableStateFlow<Session?>(
                Session("srv-1", userId, server.address, server = server, isAdmin = true),
            )
        val systemInfoFlow = MutableStateFlow<Result<SystemInfo>?>(null)
        val pluginsFlow = MutableStateFlow<Result<List<PluginInfo>>?>(null)
        val scheduledTasksFlow = MutableStateFlow<Result<List<TaskInfo>>?>(null)
        val jellyfinDownloadsFlow = MutableStateFlow<List<DownloadInfo>>(emptyList())
        val absDownloadsFlow = MutableStateFlow<List<AbsDownloadInfo>>(emptyList())
        val hiddenRowsFlow = MutableStateFlow<Set<HomeRow>>(emptySet())
        val offlineModeFlow = MutableStateFlow(false)
        val networkAvailableFlow = MutableStateFlow(true)
        val subtitleChangesFlow = MutableStateFlow(0L)

        val context: Context =
            mockk(relaxed = true) {
                every { getString(any()) } returns "Error"
                every { getString(any(), any()) } returns "Formatted error"
            }
        val authRepository: AuthRepository =
            mockk(relaxed = true) { every { currentUser } returns currentUserFlow }
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) {
                every { getShowRatingsFlow() } returns MutableStateFlow(true)
                every { getShowAwardsFlow() } returns MutableStateFlow(true)
                every { getOriginalImagesFlow() } returns MutableStateFlow(false)
                every { getThemeMusicEnabledFlow() } returns MutableStateFlow(false)
                every { getThemeMusicVolumeFlow() } returns MutableStateFlow(50)
                every { getThemeMusicOnMobileDataFlow() } returns MutableStateFlow(false)
                every { getWikidataEnabledFlow(any(), any()) } returns MutableStateFlow(true)
                every { getSubtitleDbEnabledFlow(any(), any()) } returns MutableStateFlow(true)
                every { getCombineLibrarySectionsFlow() } returns MutableStateFlow(false)
                every { getHomeSortByDateAddedFlow() } returns MutableStateFlow(true)
                every { getMergeContinueWatchingNextUpFlow() } returns MutableStateFlow(false)
                every { getNextUpMaxDaysFlow() } returns MutableStateFlow(30)
                every { getNavigationDrawerEnabledFlow() } returns MutableStateFlow(false)
                every { getSideSheetEnabledFlow() } returns MutableStateFlow(true)
                every { getDrawerLibrariesModeFlow() } returns
                    MutableStateFlow(DrawerLibrariesMode.OFF)
                every { getEpisodeLayoutFlow() } returns MutableStateFlow(EpisodeLayout.HORIZONTAL)
                every { getDetailLayoutFlow() } returns MutableStateFlow(DetailLayout.CLASSIC)
                every { getCardSizeFlow() } returns MutableStateFlow(CardSize.DEFAULT)
                every { getHeroStyleFlow() } returns MutableStateFlow(HeroStyle.FULL_BLEED)
                every { getThemeModeFlow() } returns MutableStateFlow("DARK")
                every { getDynamicColorsFlow() } returns MutableStateFlow(true)
                every { getAutoPlayFlow() } returns MutableStateFlow(true)
                every { getSkipIntroModeFlow() } returns MutableStateFlow(SkipMode.BUTTON)
                every { getSkipOutroModeFlow() } returns MutableStateFlow(SkipMode.BUTTON)
                every { getSegmentSkipModeFlow(any()) } returns MutableStateFlow(SkipMode.DISABLED)
                every { getPipGestureEnabledFlow() } returns MutableStateFlow(false)
                every { getPipBackgroundPlayFlow() } returns MutableStateFlow(true)
                every { getOfflineModeFlow() } returns offlineModeFlow
            }
        val securePreferencesRepository: SecurePreferencesRepository = mockk(relaxed = true)
        val appDataRepository: AppDataRepository =
            mockk(relaxed = true) {
                every { userProfileImageUrl } returns
                    MutableStateFlow("https://jf.example.com/avatar")
            }
        val homeLayoutPreferencesRepository: HomeLayoutPreferencesRepository =
            mockk(relaxed = true) { every { hiddenRows } returns hiddenRowsFlow }
        val serverRepository: ServerRepository =
            object : ServerRepository by mockk(relaxed = true) {
                override val currentServer: StateFlow<Server?> = currentServerFlow

                override fun getBaseUrl(): String = server.address

                override fun getCurrentServer(): Server? = currentServerFlow.value
            }
        val sessionManager: SessionManager =
            mockk(relaxed = true) { every { currentSession } returns currentSessionFlow }
        val offlineModeManager: OfflineModeManager = mockk {
            every { connectionType } returns MutableStateFlow(ConnectionType.LOCAL)
            every { connectivity } returns MutableStateFlow(Connectivity.Online)
        }
        val networkConnectivityMonitor: NetworkConnectivityMonitor = mockk {
            every { isNetworkAvailable } returns networkAvailableFlow
        }
        val jellyseerrRepository: JellyseerrRepository =
            mockk(relaxed = true) { every { isAuthenticated } returns MutableStateFlow(false) }
        val audiobookshelfRepository: AudiobookshelfRepository =
            mockk(relaxed = true) { every { isAuthenticated } returns MutableStateFlow(false) }
        val audiobookshelfPlayer: AudiobookshelfPlayer = mockk(relaxed = true)
        val tmdbApiService: TmdbApiService = mockk(relaxed = true)
        val mdbListApiService: MdbListApiService = mockk(relaxed = true)
        val omdbApiService: OmdbApiService = mockk(relaxed = true)
        val lastFmApiService: LastFmApiService = mockk(relaxed = true)
        val downloadRepository: DownloadRepository =
            mockk(relaxed = true) {
                every { getCompletedDownloadsFlow() } returns jellyfinDownloadsFlow
            }
        val absDownloadRepository: AbsDownloadRepository =
            mockk(relaxed = true) { every { getCompletedDownloadsFlow() } returns absDownloadsFlow }
        val adminRepository: ServerAdminRepository =
            mockk(relaxed = true) {
                every { systemInfo } returns systemInfoFlow
                every { plugins } returns pluginsFlow
                every { scheduledTasks } returns scheduledTasksFlow
            }
        val deviceSubtitleStore: DeviceSubtitleStore =
            mockk(relaxed = true) {
                every { changes } returns subtitleChangesFlow
                coEvery { stats(any()) } returns DeviceSubtitleStats(3, 4096L)
            }
        val deletedItemsRepository: DeletedItemsRepository =
            mockk(relaxed = true) { coEvery { deletedIds() } returns emptySet() }

        val viewModel =
            SettingsViewModel(
                context = context,
                authRepository = authRepository,
                preferencesRepository = preferencesRepository,
                securePreferencesRepository = securePreferencesRepository,
                appDataRepository = appDataRepository,
                homeLayoutPreferencesRepository = homeLayoutPreferencesRepository,
                serverRepository = serverRepository,
                sessionManager = sessionManager,
                offlineModeManager = offlineModeManager,
                networkConnectivityMonitor = networkConnectivityMonitor,
                jellyseerrRepository = jellyseerrRepository,
                audiobookshelfRepository = audiobookshelfRepository,
                audiobookshelfPlayer = audiobookshelfPlayer,
                tmdbApiService = tmdbApiService,
                mdbListApiService = mdbListApiService,
                omdbApiService = omdbApiService,
                lastFmApiService = lastFmApiService,
                downloadRepository = downloadRepository,
                absDownloadRepository = absDownloadRepository,
                adminRepository = adminRepository,
                deviceSubtitleStore = deviceSubtitleStore,
                deletedItemsRepository = deletedItemsRepository,
            )
    }

    @Test
    fun `controlPanelStatus aggregates restart flags, plugins, and running task`() = runTest {
        val harness = Harness()
        val sysInfo =
            mockk<SystemInfo>(relaxed = true) {
                every { hasPendingRestart } returns true
                val install =
                    mockk<InstallationInfo>(relaxed = true) {
                        every { name } returns "IntroSkipper"
                    }
                every { completedInstallations } returns listOf(install)
            }
        val plugin =
            mockk<PluginInfo>(relaxed = true) {
                every { status } returns PluginStatus.RESTART
                every { name } returns "PlaybackReporting"
            }
        val task =
            mockk<TaskInfo>(relaxed = true) {
                every { state } returns TaskState.RUNNING
                every { name } returns "Scan Media Library"
            }

        harness.viewModel.controlPanelStatus.test {
            awaitItem()
            harness.systemInfoFlow.value = Result.success(sysInfo)
            harness.pluginsFlow.value = Result.success(listOf(plugin))
            harness.scheduledTasksFlow.value = Result.success(listOf(task))

            val latest = expectMostRecentItem()
            assertTrue(latest.restartRequired)
            assertTrue(latest.hasPendingConfigRestart)
            assertEquals(listOf("PlaybackReporting", "IntroSkipper"), latest.restartingPluginNames)
            assertEquals("Scan Media Library", latest.runningTask)
        }
    }

    @Test
    fun `effectiveOfflineMode, hidden row flags, and hasOfflineMedia reflect upstream flows`() =
        runTest {
            val harness = Harness()

            harness.viewModel.effectiveOfflineMode.test {
                assertFalse(awaitItem())
                harness.offlineModeFlow.value = true
                assertTrue(awaitItem())
                harness.offlineModeFlow.value = false
                assertFalse(awaitItem())
                harness.networkAvailableFlow.value = false
                assertTrue(awaitItem())
            }

            harness.viewModel.latestRowsVisible.test {
                assertTrue(awaitItem())
                harness.hiddenRowsFlow.value = setOf(HomeRow.LATEST_MOVIES, HomeRow.LATEST_TV)
                assertFalse(expectMostRecentItem())
            }

            harness.viewModel.hasOfflineMedia.test {
                assertFalse(awaitItem())
                harness.absDownloadsFlow.value = listOf(mockk(relaxed = true))
                assertTrue(expectMostRecentItem())
            }
        }

    @Test
    fun `validateAndSaveTmdbKey persists valid keys and surfaces error on invalid response`() =
        runTest {
            val harness = Harness()
            coEvery { harness.tmdbApiService.validateApiKey("good-key") } returns
                Response.success(mockk(relaxed = true))
            coEvery { harness.tmdbApiService.validateApiKey("bad-key") } returns
                Response.error(401, "Unauthorized".toResponseBody(null))

            var saved = false
            harness.viewModel.validateAndSaveTmdbKey("good-key") { saved = true }
            advanceUntilIdle()

            assertTrue(saved)
            assertEquals("good-key", harness.viewModel.tmdbApiKey.value)
            coVerify {
                harness.securePreferencesRepository.saveTmdbApiKey(
                    "srv-1",
                    harness.userId.toString(),
                    "good-key",
                )
            }

            harness.viewModel.validateAndSaveTmdbKey("bad-key") {}
            advanceUntilIdle()
            assertEquals(
                "Invalid TMDB API Key",
                harness.viewModel.uiState.value.tmdbKeyValidationError,
            )
        }

    @Test
    fun `logout clears app data, auth, jellyseerr, and audiobookshelf`() = runTest {
        val harness = Harness()

        harness.viewModel.logout()
        advanceUntilIdle()

        coVerify { harness.appDataRepository.clearAllData() }
        coVerify { harness.authRepository.logout() }
        coVerify { harness.jellyseerrRepository.logout() }
        coVerify { harness.audiobookshelfPlayer.release() }
        coVerify { harness.audiobookshelfRepository.logout() }
    }

    @Test
    fun `authorizeQuickConnect and preference setters update state and persist`() = runTest {
        val harness = Harness()
        coEvery { harness.authRepository.authorizeQuickConnect("123456") } returns
            QuickConnectAuthorization.APPROVED

        harness.viewModel.authorizeQuickConnect("123456")
        harness.viewModel.setSkipIntroMode(SkipMode.AUTO_SKIP)
        harness.viewModel.setSegmentSkipMode(AfinitySegmentType.COMMERCIAL, SkipMode.BUTTON)
        advanceUntilIdle()

        assertTrue(harness.viewModel.uiState.value.quickConnectAuthSuccess)
        coVerify { harness.preferencesRepository.setSkipIntroMode(SkipMode.AUTO_SKIP) }
        coVerify {
            harness.preferencesRepository.setSegmentSkipMode(
                AfinitySegmentType.COMMERCIAL,
                SkipMode.BUTTON,
            )
        }
    }

    @Test
    fun `initial navigation state defaults match drawer, each library, and side sheet enabled`() =
        runTest {
            val harness = Harness()
            every { harness.preferencesRepository.getNavigationDrawerEnabledFlow() } returns
                emptyFlow()
            every { harness.preferencesRepository.getDrawerLibrariesModeFlow() } returns emptyFlow()
            every { harness.preferencesRepository.getSideSheetEnabledFlow() } returns emptyFlow()

            val vm =
                SettingsViewModel(
                    context = harness.context,
                    authRepository = harness.authRepository,
                    preferencesRepository = harness.preferencesRepository,
                    securePreferencesRepository = harness.securePreferencesRepository,
                    appDataRepository = harness.appDataRepository,
                    homeLayoutPreferencesRepository = harness.homeLayoutPreferencesRepository,
                    serverRepository = harness.serverRepository,
                    sessionManager = harness.sessionManager,
                    offlineModeManager = harness.offlineModeManager,
                    networkConnectivityMonitor = harness.networkConnectivityMonitor,
                    jellyseerrRepository = harness.jellyseerrRepository,
                    audiobookshelfRepository = harness.audiobookshelfRepository,
                    audiobookshelfPlayer = harness.audiobookshelfPlayer,
                    tmdbApiService = harness.tmdbApiService,
                    mdbListApiService = harness.mdbListApiService,
                    omdbApiService = harness.omdbApiService,
                    lastFmApiService = harness.lastFmApiService,
                    downloadRepository = harness.downloadRepository,
                    absDownloadRepository = harness.absDownloadRepository,
                    adminRepository = harness.adminRepository,
                    deviceSubtitleStore = harness.deviceSubtitleStore,
                    deletedItemsRepository = harness.deletedItemsRepository,
                )

            assertTrue(vm.navigationDrawerEnabled.value)
            assertEquals(DrawerLibrariesMode.EACH, vm.drawerLibrariesMode.value)
            assertTrue(vm.sideSheetEnabled.value)
        }
}
