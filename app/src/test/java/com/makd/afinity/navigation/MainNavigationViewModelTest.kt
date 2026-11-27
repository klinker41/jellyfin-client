package com.makd.afinity.navigation

import app.cash.turbine.test
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.PendingNavigationManager
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.common.CardSize
import com.makd.afinity.data.models.common.DetailLayout
import com.makd.afinity.data.models.common.DrawerLibrariesMode
import com.makd.afinity.data.models.common.EpisodeLayout
import com.makd.afinity.data.models.common.HeroStyle
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.music.MusicPlaybackState
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.player.ContinueListeningQueue
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackState
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testEpisode
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testShow
import com.makd.afinity.testutil.TestMediaFixtures.testTrack
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainNavigationViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class Harness(
        initialAuthenticated: Boolean = true,
        initialReachable: Boolean = true,
        initialOffline: Boolean = false,
        initialSession: Session? =
            Session(
                serverId = "srv-1",
                userId = testUuid(1),
                serverUrl = "https://jf.example",
                server = Server(id = "srv-1", name = "Home Server", address = "https://jf.example"),
                isAdmin = true,
            ),
    ) {
        val isAuthenticatedFlow = MutableStateFlow(initialAuthenticated)
        val isServerReachableFlow = MutableStateFlow(initialReachable)
        val currentSessionFlow = MutableStateFlow(initialSession)
        val isInitialLoadedFlow = MutableStateFlow(false)
        val loadingProgressFlow = MutableStateFlow(0f)
        val loadingPhaseFlow = MutableStateFlow("")
        val favoritesCountFlow = MutableStateFlow(0)
        val librariesFlow = MutableStateFlow<List<AfinityCollection>>(emptyList())
        val sessionClearedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val watchlistCountFlow = MutableStateFlow<Int?>(null)
        val hasLiveTvAccessFlow = MutableStateFlow(false)
        val jellyseerrAuthFlow = MutableStateFlow(false)
        val absAuthFlow = MutableStateFlow(false)
        val unsupportedServerVersionFlow = MutableStateFlow<String?>(null)
        val pendingRouteFlow = MutableStateFlow<String?>("downloads")
        val detailLayoutFlow = MutableStateFlow(DetailLayout.CLASSIC)
        val episodeLayoutFlow = MutableStateFlow(EpisodeLayout.HORIZONTAL)
        val absPlaybackStateFlow = MutableStateFlow(AudiobookshelfPlaybackState())
        val musicPlaybackStateFlow = MutableStateFlow(MusicPlaybackState())

        var offlineOnStart = initialOffline

        val appDataRepository: AppDataRepository =
            mockk(relaxed = true) {
                every { isInitialDataLoaded } returns isInitialLoadedFlow
                every { loadingProgress } returns loadingProgressFlow
                every { loadingPhase } returns loadingPhaseFlow
                every { favoritesCountFlow } returns this@Harness.favoritesCountFlow
                every { libraries } returns librariesFlow
                every { sessionCleared } returns sessionClearedFlow
                coEvery { loadInitialData() } returns Unit
                coEvery { skipInitialDataLoad() } returns Unit
            }
        val authRepository: AuthRepository = mockk {
            every { isAuthenticated } returns isAuthenticatedFlow
        }
        val jellyfinRepository: JellyfinRepository =
            mockk(relaxed = true) { coEvery { refreshServerInfo() } returns Unit }
        val mediaRepository: MediaRepository = mockk(relaxed = true)
        val watchlistRepository: WatchlistRepository = mockk {
            every { watchlistCountFlow } returns this@Harness.watchlistCountFlow
        }
        val jellyseerrRepository: JellyseerrRepository = mockk {
            every { isAuthenticated } returns jellyseerrAuthFlow
        }
        val audiobookshelfRepository: AudiobookshelfRepository = mockk {
            every { isAuthenticated } returns absAuthFlow
        }
        val liveTvRepository: LiveTvRepository = mockk {
            every { hasLiveTvAccessFlow } returns this@Harness.hasLiveTvAccessFlow
        }
        val offlineModeManager: OfflineModeManager = mockk {
            coEvery { isCurrentlyOffline() } answers { offlineOnStart }
        }
        val sessionManager: SessionManager = mockk {
            every { currentSession } returns currentSessionFlow
            every { isServerReachable } returns isServerReachableFlow
        }
        val preferencesRepository: PreferencesRepository = mockk {
            every { getShowRatingsFlow() } returns MutableStateFlow(true)
            every { getBlurUnwatchedEpisodesFlow() } returns MutableStateFlow(false)
            every { getShowAwardsFlow() } returns MutableStateFlow(true)
            every { getOriginalImagesFlow() } returns MutableStateFlow(false)
            every { getCardSizeFlow() } returns MutableStateFlow(CardSize.DEFAULT)
            every { getHeroStyleFlow() } returns MutableStateFlow(HeroStyle.FULL_BLEED)
            every { getSideSheetEnabledFlow() } returns MutableStateFlow(true)
            every { getNavigationDrawerEnabledFlow() } returns MutableStateFlow(false)
            every { getDrawerLibrariesModeFlow() } returns MutableStateFlow(DrawerLibrariesMode.OFF)
            every { getDetailLayoutFlow() } returns detailLayoutFlow
            every { getEpisodeLayoutFlow() } returns episodeLayoutFlow
            coEvery { getOnboardingFirstRunDone() } returns true
        }
        val pendingNavigationManager: PendingNavigationManager =
            mockk(relaxed = true) { every { pendingRoute } returns pendingRouteFlow }
        val serverRepository: ServerRepository = mockk {
            every { unsupportedServerVersion } returns unsupportedServerVersionFlow
        }

        fun createViewModel(): MainNavigationViewModel =
            MainNavigationViewModel(
                appDataRepository = appDataRepository,
                authRepository = authRepository,
                jellyfinRepository = jellyfinRepository,
                mediaRepository = mediaRepository,
                watchlistRepository = watchlistRepository,
                jellyseerrRepository = jellyseerrRepository,
                audiobookshelfRepository = audiobookshelfRepository,
                audiobookshelfPlayer = mockk(relaxed = true),
                audiobookshelfPlaybackManager =
                    mockk(relaxed = true) { every { playbackState } returns absPlaybackStateFlow },
                musicPlaybackManager =
                    mockk(relaxed = true) { every { state } returns musicPlaybackStateFlow },
                themeMusicPlayer = mockk(relaxed = true),
                castManager = mockk(relaxed = true),
                liveTvRepository = liveTvRepository,
                offlineModeManager = offlineModeManager,
                sessionManager = sessionManager,
                preferencesRepository = preferencesRepository,
                pendingNavigationManager = pendingNavigationManager,
                continueListeningQueue = ContinueListeningQueue(),
                serverRepository = serverRepository,
            )
    }

    @Test
    fun initLoadsInitialDataAndRefreshesServerInfoWhenAuthenticatedOnlineAndReachable() =
        runTest(mainDispatcherRule.testDispatcher) {
            val h = Harness()
            val vm = h.createViewModel()

            coVerify(exactly = 1) { h.appDataRepository.loadInitialData() }
            coVerify(exactly = 1) { h.jellyfinRepository.refreshServerInfo() }
            assertTrue(vm.isOnboardingFirstRunDone())

            vm.consumePendingNavigation()
            verify(exactly = 1) { h.pendingNavigationManager.consume() }
        }

    @Test
    fun initSkipsInitialDataAndServerRefreshWhenOfflineOrUnreachable() =
        runTest(mainDispatcherRule.testDispatcher) {
            val offlineHarness = Harness(initialOffline = true)
            offlineHarness.createViewModel()
            coVerify(exactly = 1) { offlineHarness.appDataRepository.skipInitialDataLoad() }
            coVerify(exactly = 0) { offlineHarness.appDataRepository.loadInitialData() }
            coVerify(exactly = 0) { offlineHarness.jellyfinRepository.refreshServerInfo() }

            val unreachableHarness = Harness(initialReachable = false)
            unreachableHarness.createViewModel()
            coVerify(exactly = 1) { unreachableHarness.appDataRepository.skipInitialDataLoad() }
            coVerify(exactly = 0) { unreachableHarness.jellyfinRepository.refreshServerInfo() }
        }

    @Test
    fun freshLoginLoadsDataAndRetryFallsBackToSkipAfterThreeFailures() =
        runTest(mainDispatcherRule.testDispatcher) {
            val h = Harness(initialAuthenticated = false, initialOffline = true)
            coEvery { h.appDataRepository.loadInitialData() } throws RuntimeException("boom")
            h.createViewModel()
            coVerify(exactly = 0) { h.appDataRepository.loadInitialData() }

            // Transitioning auth false -> true invokes loadAppData(skipOfflineCheck = true).
            h.isAuthenticatedFlow.value = true
            advanceUntilIdle()
            coVerify(exactly = 3) { h.appDataRepository.loadInitialData() }
            coVerify(exactly = 1) { h.appDataRepository.skipInitialDataLoad() }
        }

    @Test
    fun retrySucceedsAfterTransientFailureWithoutSkippingDataLoad() =
        runTest(mainDispatcherRule.testDispatcher) {
            val h = Harness()
            var attempts = 0
            coEvery { h.appDataRepository.loadInitialData() } coAnswers
                {
                    attempts++
                    if (attempts == 1) throw RuntimeException("transient")
                }

            h.createViewModel()
            advanceUntilIdle()

            coVerify(exactly = 2) { h.appDataRepository.loadInitialData() }
            coVerify(exactly = 0) { h.appDataRepository.skipInitialDataLoad() }
        }

    @Test
    fun exposesCombinedLoadingSessionWatchlistStatesAndResolvesPlayableItems() =
        runTest(mainDispatcherRule.testDispatcher) {
            val h = Harness()
            val vm = h.createViewModel()

            vm.serverName.test {
                assertEquals("Home Server", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.isAdmin.test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.watchlistCount.test {
                assertEquals(0, awaitItem())
                h.watchlistCountFlow.value = 5
                assertEquals(5, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            vm.appLoadingState.test {
                assertTrue(awaitItem().isLoading)
                h.loadingProgressFlow.value = 0.6f
                assertEquals(0.6f, awaitItem().loadingProgress)
                h.loadingPhaseFlow.value = "Syncing"
                val mid = awaitItem()
                assertEquals(0.6f, mid.loadingProgress)
                assertEquals("Syncing", mid.loadingPhase)
                h.isInitialLoadedFlow.value = true
                assertFalse(awaitItem().isLoading)
                cancelAndIgnoreRemainingEvents()
            }

            val movie = testMovie(id = testUuid(1))
            val show = testShow(id = testUuid(2))
            val ep = testEpisode(id = testUuid(3), seriesId = show.id)
            coEvery { h.mediaRepository.getEpisodeToPlay(show.id) } returns ep

            assertSame(movie, vm.resolvePlayableItem(movie))
            assertSame(ep, vm.resolvePlayableItem(show))

            coEvery { h.mediaRepository.getEpisodeToPlay(show.id) } throws
                IllegalStateException("fail")
            assertNull(vm.resolvePlayableItem(show))
        }

    @Test
    fun detailEpisodeLayoutAndActiveAudioPlaybackFlowsTrackUpdates() =
        runTest(mainDispatcherRule.testDispatcher) {
            val h = Harness()
            val vm = h.createViewModel()

            vm.detailLayout.test {
                assertEquals(DetailLayout.CLASSIC, awaitItem())
                h.detailLayoutFlow.value = DetailLayout.MODERN
                assertEquals(DetailLayout.MODERN, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            vm.episodeLayout.test {
                assertEquals(EpisodeLayout.HORIZONTAL, awaitItem())
                h.episodeLayoutFlow.value = EpisodeLayout.VERTICAL
                assertEquals(EpisodeLayout.VERTICAL, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            vm.hasActiveAudioPlayback.test {
                assertFalse(awaitItem())

                h.absPlaybackStateFlow.value = AudiobookshelfPlaybackState(sessionId = "abs-1")
                assertTrue(awaitItem())

                h.absPlaybackStateFlow.value = AudiobookshelfPlaybackState(sessionId = null)
                assertFalse(awaitItem())

                h.musicPlaybackStateFlow.value =
                    MusicPlaybackState(currentTrack = testTrack(id = testUuid(9)))
                assertTrue(awaitItem())

                h.musicPlaybackStateFlow.value = MusicPlaybackState(currentTrack = null)
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
