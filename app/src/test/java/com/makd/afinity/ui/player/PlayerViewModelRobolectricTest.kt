package com.makd.afinity.ui.player

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.DefaultContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.makd.afinity.R
import com.makd.afinity.cast.CastEvent
import com.makd.afinity.cast.CastManager
import com.makd.afinity.cast.CastSessionState
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.manager.RemoteMessage
import com.makd.afinity.data.manager.RemoteMessageManager
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.livetv.LiveTvPlaybackInfo
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.music.MusicPlaybackState
import com.makd.afinity.data.models.player.AssRenderMode
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.data.models.player.SeekStep
import com.makd.afinity.data.models.player.SkipMode
import com.makd.afinity.data.models.player.SleepTimerMode
import com.makd.afinity.data.models.player.StreamDecision
import com.makd.afinity.data.models.player.VideoQuality
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import com.makd.afinity.data.repository.download.JellyfinDownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.metadata.ItemRatingsLoader
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.repository.segments.SegmentsRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.data.repository.subtitles.DeviceSubtitleStore
import com.makd.afinity.data.repository.subtitles.SubtitleSearchRepository
import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlayer
import com.makd.afinity.player.music.MusicPlaybackManager
import com.makd.afinity.testutil.FakeApiClient
import com.makd.afinity.testutil.MainDispatcherRule
import com.makd.afinity.testutil.TestMediaFixtures.testEpisode
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testSource
import com.makd.afinity.testutil.TestMediaFixtures.testStream
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.util.NetworkConnectivityMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.api.TranscodeReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class PlayerViewModelRobolectricTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    @get:Rule val tempFolder = TemporaryFolder()

    private inner class Harness {
        val context: Context = ApplicationProvider.getApplicationContext()
        val application: Application = ApplicationProvider.getApplicationContext()
        // Return C.LENGTH_UNSET (-1L) instead of relaxed 0L (which triggers instant 0-byte EOF
        // parse errors) and throw plain IOException (not CacheException) to avoid network/DNS I/O.
        val exoCache: SimpleCache =
            mockk(relaxed = true) {
                every { getContentMetadata(any()) } returns DefaultContentMetadata.EMPTY
                every { startReadWriteNonBlocking(any(), any(), any()) } throws
                    IOException("Cache disabled in unit test")
                every { startReadWrite(any(), any(), any()) } throws
                    IOException("Cache disabled in unit test")
            }
        val userId = testUuid(1)
        val apiClient = FakeApiClient(baseUrl = "https://jf.example.com", accessToken = "tok-1")
        val sessionFlow =
            MutableStateFlow<Session?>(Session("srv-1", userId, "https://jf.example.com"))
        val sessionClearedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val castStateFlow = MutableStateFlow(CastSessionState())
        val castEventsFlow = MutableSharedFlow<CastEvent>(extraBufferCapacity = 4)
        val remotePlaystateFlow = MutableSharedFlow<org.jellyfin.sdk.model.api.PlaystateRequest>()
        val remoteGeneralFlow = MutableSharedFlow<org.jellyfin.sdk.model.api.GeneralCommand>()
        val remoteMessageFlow = MutableStateFlow<RemoteMessage?>(null)
        val networkAvailableFlow = MutableStateFlow(true)
        val offlineFlow = MutableStateFlow(false)
        val playlistStateFlow = MutableStateFlow(PlaylistState())

        val playbackRepository: PlaybackRepository = mockk(relaxed = true)
        val playbackStateManager: PlaybackStateManager = mockk(relaxed = true)
        val castManager: CastManager =
            mockk(relaxed = true) {
                every { castState } returns castStateFlow
                every { castEvents } returns castEventsFlow
                every { isCasting } answers { castStateFlow.value.isConnected }
            }
        val mediaRepository: MediaRepository =
            mockk(relaxed = true) { coEvery { getAdditionalParts(any()) } returns emptyList() }
        val itemRatingsLoader: ItemRatingsLoader = mockk(relaxed = true)
        val segmentsRepository: SegmentsRepository =
            mockk(relaxed = true) {
                coEvery { getSegments(any(), any(), any()) } returns emptyList()
            }
        val preferencesRepository: PreferencesRepository =
            mockk(relaxed = true) {
                coEvery { getDefaultVideoZoomMode() } returns
                    com.makd.afinity.data.models.player.VideoZoomMode.FIT
                coEvery { getLogoAutoHide() } returns true
                coEvery { getPauseScreenEnabled() } returns false
                coEvery { getPauseScreenDelaySeconds() } returns 3
                coEvery { getSeekBackStep() } returns SeekStep.TEN
                coEvery { getSeekForwardStep() } returns SeekStep.TEN
                coEvery { getChapterSkipGesture() } returns false
                coEvery { getSwapGestureIndicators() } returns false
                every { getPipBackgroundPlayFlow() } returns MutableStateFlow(false)
                coEvery { getBufferSizeMb() } returns 32
                coEvery { getAssRenderMode() } returns AssRenderMode.OFF
                coEvery { getPreferredAudioLanguage() } returns "eng"
                coEvery { getPreferredSubtitleLanguage() } returns "eng"
                coEvery { getSubtitleModeOverride() } returns ""
                coEvery { getPreferSdhSubtitles() } returns false
                coEvery { getNeverTranscode() } returns false
                coEvery { getVideoQualityWifi() } returns VideoQuality.ORIGINAL.maxBitrate
                coEvery { getVideoQualityCellular() } returns VideoQuality.ORIGINAL.maxBitrate
                coEvery { getAutoPlay() } returns true
                coEvery { getSegmentSkipMode(any()) } returns SkipMode.BUTTON
            }
        val playlistManager: PlaylistManager =
            mockk(relaxed = true) {
                every { playlistState } returns playlistStateFlow
                coEvery { initializePlaylist(any(), any(), any(), any(), any(), any()) } returns
                    true
            }
        val downloadRepository: JellyfinDownloadRepository =
            mockk(relaxed = true) {
                coEvery { getItemDownloadDirectory(any<java.util.UUID>()) } returns
                    tempFolder.newFolder()
            }
        val appDataRepository: AppDataRepository =
            mockk(relaxed = true) { every { sessionCleared } returns sessionClearedFlow }
        val audiobookshelfPlayer: AudiobookshelfPlayer =
            mockk(relaxed = true) { every { isPlaying() } returns false }
        val musicPlaybackManager: MusicPlaybackManager =
            mockk(relaxed = true) { every { state } returns MutableStateFlow(MusicPlaybackState()) }
        val offlineModeManager: OfflineModeManager =
            mockk(relaxed = true) {
                every { isOffline } returns offlineFlow
                every { connectionType } returns
                    MutableStateFlow(com.makd.afinity.data.models.server.ConnectionType.LOCAL)
            }
        val sessionManager: SessionManager =
            mockk(relaxed = true) {
                every { currentSession } returns sessionFlow
                every { getCurrentApiClient() } returns apiClient
            }
        val webSocketManager: JellyfinWebSocketManager =
            mockk(relaxed = true) {
                every { remotePlaystateCommands } returns remotePlaystateFlow
                every { remoteGeneralCommands } returns remoteGeneralFlow
            }
        val remoteMessageManager: RemoteMessageManager =
            mockk(relaxed = true) { every { message } returns remoteMessageFlow }
        val networkMonitor: NetworkConnectivityMonitor = mockk {
            every { isNetworkAvailable } returns networkAvailableFlow
            every { isOnWifi() } returns true
        }
        val serverRepository: ServerRepository =
            mockk(relaxed = true) { coEvery { pingServer(any(), any()) } returns true }
        val serverAdminRepository: ServerAdminRepository = mockk(relaxed = true)
        val subtitleSearchRepository: SubtitleSearchRepository =
            mockk(relaxed = true) { every { currentScope() } returns null }
        val deviceSubtitleStore: DeviceSubtitleStore =
            mockk(relaxed = true) { coEvery { list(any(), any(), any()) } returns emptyList() }

        val store = ViewModelStore()

        fun createViewModel(): PlayerViewModel {
            val factory =
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        PlayerViewModel(
                            context = context,
                            application = application,
                            exoCache = exoCache,
                            playbackRepository = playbackRepository,
                            playbackStateManager = playbackStateManager,
                            castManager = castManager,
                            mediaRepository = mediaRepository,
                            itemRatingsLoader = itemRatingsLoader,
                            segmentsRepository = segmentsRepository,
                            preferencesRepository = preferencesRepository,
                            playlistManager = playlistManager,
                            downloadRepository = downloadRepository,
                            appDataRepository = appDataRepository,
                            apiClient = apiClient,
                            audiobookshelfPlayer = audiobookshelfPlayer,
                            musicPlaybackManager = musicPlaybackManager,
                            offlineModeManager = offlineModeManager,
                            sessionManager = sessionManager,
                            jellyfinWebSocketManager = webSocketManager,
                            remoteMessageManager = remoteMessageManager,
                            networkConnectivityMonitor = networkMonitor,
                            serverRepository = serverRepository,
                            serverAdminRepository = serverAdminRepository,
                            subtitleSearchRepository = subtitleSearchRepository,
                            deviceSubtitleStore = deviceSubtitleStore,
                        )
                            as T
                }
            return ViewModelProvider(store, factory)[PlayerViewModel::class.java]
        }

        fun dispose() {
            store.clear()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun TestScope.awaitOnMain(timeoutMs: Long = 2_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            if (!predicate()) Thread.sleep(10L)
        }
        assertTrue("Timed out waiting on main thread", predicate())
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
    }

    private fun playbackInfoJson(
        sourceId: String = "src-1",
        supportsDirectPlay: Boolean = true,
        supportsTranscoding: Boolean = false,
        transcodingUrl: String? = null,
    ): PlaybackInfoResponse =
        FakeApiClient.decodeJson(
            """{
            "MediaSources":[${
                FakeApiClient.mediaSourceJson(
                    id = sourceId,
                    name = "Primary",
                    supportsDirectPlay = supportsDirectPlay,
                    supportsDirectStream = false,
                    supportsTranscoding = supportsTranscoding,
                    transcodingUrl = transcodingUrl,
                    transcodingSubProtocol = "hls",
                )
            }],
            "PlaySessionId":"ps-vm-1"
        }""",
        )

    @Test
    fun `loadMedia resolves local source, direct play, and transcode fallback on decode error`() =
        runTest(mainDispatcherRule.testDispatcher, timeout = 30.seconds) {
            // 1. Local source skips getPlaybackInfo and plays file:// URI.
            // Use a dedicated Harness so the empty temp file's async ExoPlayer parse error cannot
            // be delivered on the paused Robolectric main looper after remoteMovie loads.
            val localHarness = Harness()
            try {
                val vm = localHarness.createViewModel()
                runCurrent()
                assertTrue(vm.uiState.value.isPlayerReady)

                val localFile = tempFolder.newFile("offline.mkv")
                val localMovie =
                    testMovie(
                        id = testUuid(10),
                        name = "Offline Film",
                        sources =
                            listOf(
                                testSource(
                                    id = "src-local",
                                    type = AfinitySourceType.LOCAL,
                                    path = localFile.absolutePath,
                                    mediaStreams =
                                        listOf(
                                            testStream(0, MediaStreamType.VIDEO),
                                            testStream(1, MediaStreamType.AUDIO, language = "eng"),
                                        ),
                                ),
                            ),
                    )
                vm.handlePlayerEvent(
                    PlayerEvent.LoadMedia(item = localMovie, mediaSourceId = "src-local"),
                )
                runCurrent()

                assertEquals(localMovie, vm.uiState.value.currentItem)
                assertEquals("src-local", vm.uiState.value.currentMediaSourceId)
                assertEquals(PlayMethod.DIRECT_PLAY, vm.uiState.value.playMethod)
                coVerify(exactly = 0) {
                    localHarness.playbackRepository.getPlaybackInfo(
                        itemId = localMovie.id,
                        quality = any(),
                        maxAudioChannels = any(),
                        audioStreamIndex = any(),
                        subtitleStreamIndex = any(),
                        mediaSourceId = any(),
                        startTimeTicks = any(),
                        enableDirectPlay = any(),
                        allowTranscoding = any(),
                    )
                }
            } finally {
                localHarness.dispose()
            }

            val h = Harness()
            try {
                val vm = h.createViewModel()
                runCurrent()
                assertTrue(vm.uiState.value.isPlayerReady)

                // 2. Remote source resolves DirectPlay first, then falls back to Transcode on
                // decode error
                val remoteMovie =
                    testMovie(
                        id = testUuid(11),
                        name = "Remote Film",
                        sources =
                            listOf(
                                testSource(
                                    id = "src-remote",
                                    type = AfinitySourceType.REMOTE,
                                    mediaStreams =
                                        listOf(
                                            testStream(0, MediaStreamType.VIDEO),
                                            testStream(
                                                1,
                                                MediaStreamType.AUDIO,
                                                language = "eng",
                                                isDefault = true,
                                            ),
                                            testStream(
                                                2,
                                                MediaStreamType.SUBTITLE,
                                                language = "eng",
                                                isDefault = true,
                                            ),
                                        ),
                                ),
                            ),
                    )
                val startReported = CompletableDeferred<Unit>()
                coEvery {
                    h.playbackRepository.reportPlaybackStart(
                        itemId = remoteMovie.id,
                        sessionId = any(),
                        mediaSourceId = any(),
                        audioStreamIndex = any(),
                        subtitleStreamIndex = any(),
                        playMethod = any(),
                        liveStreamId = any(),
                        canSeek = any(),
                    )
                } coAnswers
                    {
                        startReported.complete(Unit)
                        true
                    }
                coEvery {
                    h.playbackRepository.getPlaybackInfo(
                        itemId = remoteMovie.id,
                        quality = any(),
                        maxAudioChannels = any(),
                        audioStreamIndex = any(),
                        subtitleStreamIndex = any(),
                        mediaSourceId = "src-remote",
                        startTimeTicks = any(),
                        enableDirectPlay = true,
                        allowTranscoding = true,
                    )
                } returns playbackInfoJson(sourceId = "src-remote", supportsDirectPlay = true)
                coEvery {
                    h.playbackRepository.resolveStream(
                        itemId = remoteMovie.id,
                        source = match { it.supportsDirectPlay },
                        audioStreamIndex = any(),
                        subtitleStreamIndex = any(),
                        startTimeTicks = any(),
                        playSessionId = any(),
                    )
                } returns
                    StreamDecision.DirectPlay(
                        "https://jf.example.com/Videos/${remoteMovie.id}/stream",
                    )
                coEvery {
                    h.playbackRepository.getPlaybackInfo(
                        itemId = remoteMovie.id,
                        quality = any(),
                        maxAudioChannels = any(),
                        audioStreamIndex = any(),
                        subtitleStreamIndex = any(),
                        mediaSourceId = "src-remote",
                        startTimeTicks = any(),
                        enableDirectPlay = false,
                        allowTranscoding = true,
                    )
                } returns
                    playbackInfoJson(
                        sourceId = "src-remote",
                        supportsDirectPlay = false,
                        supportsTranscoding = true,
                        transcodingUrl = "/Videos/${remoteMovie.id}/master.m3u8",
                    )
                coEvery {
                    h.playbackRepository.resolveStream(
                        itemId = remoteMovie.id,
                        source = match { !it.supportsDirectPlay },
                        audioStreamIndex = any(),
                        subtitleStreamIndex = any(),
                        startTimeTicks = any(),
                        playSessionId = any(),
                    )
                } returns
                    StreamDecision.Transcode(
                        url = "https://jf.example.com/Videos/${remoteMovie.id}/master.m3u8",
                        protocol = MediaStreamProtocol.HLS,
                        transcodeReasons = listOf(TranscodeReason.VIDEO_CODEC_NOT_SUPPORTED),
                        burnedInSubtitleIndex = null,
                    )

                vm.handlePlayerEvent(
                    PlayerEvent.LoadMedia(item = remoteMovie, mediaSourceId = "src-remote"),
                )
                runCurrent()
                awaitOnMain { startReported.isCompleted }

                assertEquals(PlayMethod.DIRECT_PLAY, vm.uiState.value.playMethod)
                coVerify(exactly = 0) {
                    h.playbackRepository.getPlaybackInfo(
                        itemId = remoteMovie.id,
                        quality = any(),
                        maxAudioChannels = any(),
                        audioStreamIndex = any(),
                        subtitleStreamIndex = any(),
                        mediaSourceId = "src-remote",
                        startTimeTicks = any(),
                        enableDirectPlay = false,
                        allowTranscoding = any(),
                    )
                }
                coVerify {
                    h.playbackRepository.reportPlaybackStart(
                        itemId = remoteMovie.id,
                        sessionId = any(),
                        mediaSourceId = "src-remote",
                        audioStreamIndex = any(),
                        subtitleStreamIndex = any(),
                        playMethod = PlayMethod.DIRECT_PLAY.serialName,
                        liveStreamId = any(),
                        canSeek = any(),
                    )
                }

                // Simulate decoder failure -> retryWithTranscodeFallback re-queries with
                // enableDirectPlay = false
                val decodeError =
                    PlaybackException(
                        "decoder failed",
                        null,
                        PlaybackException.ERROR_CODE_DECODING_FAILED,
                    )
                vm.onPlayerError(decodeError)
                runCurrent()

                coVerify(exactly = 1) {
                    h.playbackRepository.getPlaybackInfo(
                        itemId = remoteMovie.id,
                        quality = any(),
                        maxAudioChannels = any(),
                        audioStreamIndex = any(),
                        subtitleStreamIndex = any(),
                        mediaSourceId = "src-remote",
                        startTimeTicks = any(),
                        enableDirectPlay = false,
                        allowTranscoding = true,
                    )
                }
                assertEquals(PlayMethod.TRANSCODE, vm.uiState.value.playMethod)
                assertEquals(
                    listOf(TranscodeReason.VIDEO_CODEC_NOT_SUPPORTED),
                    vm.uiState.value.transcodeReasons,
                )

                // stopPlayback notifies PlaybackStateManager with current item
                vm.stopPlayback()
                verify {
                    h.playbackStateManager.notifyPlaybackStopped(
                        itemId = remoteMovie.id,
                        positionMs = any(),
                        runtimeTicks = remoteMovie.runtimeTicks,
                    )
                }
            } finally {
                h.dispose()
            }
        }

    @Test
    fun `segment monitoring supports button skip, undo skip, and outro next-episode advance`() =
        runTest(mainDispatcherRule.testDispatcher, timeout = 30.seconds) {
            val h = Harness()
            try {
                val movieId = testUuid(20)
                val intro =
                    AfinitySegment(AfinitySegmentType.INTRO, startTicks = 0L, endTicks = 30_000L)
                val outro =
                    AfinitySegment(
                        AfinitySegmentType.OUTRO,
                        startTicks = 80_000L,
                        endTicks = 100_000L,
                    )
                val segmentsReady = CompletableDeferred<Unit>()
                coEvery { h.segmentsRepository.getSegments(movieId, "src-seg", true) } coAnswers
                    {
                        segmentsReady.complete(Unit)
                        listOf(intro, outro)
                    }
                coEvery {
                    h.preferencesRepository.getSegmentSkipMode(AfinitySegmentType.INTRO)
                } returns SkipMode.BUTTON

                val vm = h.createViewModel()
                runCurrent()

                val localFile = tempFolder.newFile("seg.mkv")
                val ep =
                    testEpisode(
                        id = movieId,
                        sources =
                            listOf(
                                testSource(
                                    id = "src-seg",
                                    type = AfinitySourceType.LOCAL,
                                    path = localFile.absolutePath,
                                ),
                            ),
                    )
                vm.handlePlayerEvent(
                    PlayerEvent.LoadMedia(ep, "src-seg", startPositionMs = 5_000L),
                )
                runCurrent()
                awaitOnMain { segmentsReady.isCompleted }
                vm.onPlaybackStateChanged(androidx.media3.common.Player.STATE_READY)

                // Advance virtual time in increments while pumping main/test dispatchers until
                // loadSegments() schedules startSegmentMonitoring() and its 2s delay elapses.
                awaitOnMain(timeoutMs = 5_000L) {
                    advanceTimeBy(250L)
                    vm.uiState.value.showSkipButton
                }

                assertTrue(vm.uiState.value.showSkipButton)
                assertEquals(intro, vm.uiState.value.currentSegment)
                assertFalse(vm.uiState.value.skipPromptIsAuto)

                // Skip intro with undo support, then undo
                vm.handlePlayerEvent(PlayerEvent.SkipSegment(intro, undoPositionMs = 5_000L))
                runCurrent()
                assertFalse(vm.uiState.value.showSkipButton)
                assertEquals(5_000L, vm.uiState.value.undoSkipPositionMs)

                vm.handlePlayerEvent(PlayerEvent.UndoSkip)
                runCurrent()
                assertNull(vm.uiState.value.undoSkipPositionMs)

                // Outro skip advances to next episode when PlaylistManager has one
                val nextEp = testEpisode(id = testUuid(21), name = "Next Ep")
                every { h.playlistManager.canAutoAdvance() } returns true
                every { h.playlistManager.getNextItem() } returns nextEp
                coEvery { h.mediaRepository.getItemById(nextEp.id) } returns nextEp

                vm.handlePlayerEvent(PlayerEvent.SkipSegment(outro))
                advanceTimeBy(300L)
                runCurrent()

                verify { h.playlistManager.markCurrentItemAsPlayed() }
                verify { h.playlistManager.next() }

                // Duration sleep timer sets countdown, extends, and cancels cleanly
                vm.handlePlayerEvent(PlayerEvent.SetSleepTimer(SleepTimerMode.Duration(1)))
                runCurrent()
                assertEquals(SleepTimerMode.Duration(1), vm.uiState.value.sleepTimerMode)
                assertTrue(vm.uiState.value.sleepTimerRemainingMs in 50_000L..60_000L)

                vm.handlePlayerEvent(PlayerEvent.ExtendSleepTimer)
                runCurrent()
                assertTrue(vm.uiState.value.sleepTimerRemainingMs >= 15 * 60_000L)

                vm.handlePlayerEvent(PlayerEvent.CancelSleepTimer)
                runCurrent()
                assertNull(vm.uiState.value.sleepTimerMode)
                assertEquals(0L, vm.uiState.value.sleepTimerRemainingMs)
            } finally {
                h.dispose()
            }
        }

    @Test
    fun `errors, track switching, controls, zoom modes, and queue handoffs update state correctly`() =
        runTest(mainDispatcherRule.testDispatcher, timeout = 30.seconds) {
            val h = Harness()
            try {
                val vm = h.createViewModel()
                runCurrent()

                // Empty sources triggers error message
                val noSourceMovie = testMovie(id = testUuid(30), sources = emptyList())
                vm.handlePlayerEvent(PlayerEvent.LoadMedia(noSourceMovie, ""))
                runCurrent()
                assertTrue(vm.uiState.value.showError)
                assertEquals(
                    h.context.getString(R.string.error_no_media_sources),
                    vm.uiState.value.errorMessage,
                )

                // Non-connection unexpected player error
                vm.onPlayerError(
                    PlaybackException("unknown", null, PlaybackException.ERROR_CODE_UNSPECIFIED),
                )
                runCurrent()
                assertTrue(vm.uiState.value.showError)
                assertEquals(
                    h.context.getString(R.string.error_unexpected),
                    vm.uiState.value.errorMessage,
                )

                // Live channel direct stream failure emits liveStreamFailedEvent
                val liveInfo =
                    LiveTvPlaybackInfo(
                        streamUrl = "https://jf.example.com/live/ch1.ts",
                        mediaSourceId = "live-src",
                        playSessionId = "live-ps",
                        liveStreamId = "ls-1",
                        playMethod = PlayMethod.DIRECT_PLAY.serialName,
                        container = "ts",
                    )
                vm.handlePlayerEvent(
                    PlayerEvent.LoadLiveChannel(
                        channelId = testUuid(31),
                        channelName = "News",
                        streamUrl = liveInfo.streamUrl,
                        playbackInfo = liveInfo,
                    ),
                )
                runCurrent()

                vm.liveStreamFailedEvent.test {
                    vm.onPlayerError(
                        PlaybackException(
                            "live failed",
                            null,
                            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                        ),
                    )
                    assertEquals(Unit, awaitItem())
                }

                // SwitchToTrack for text marks subtitleUserSelected
                vm.handlePlayerEvent(PlayerEvent.SwitchToTrack(C.TRACK_TYPE_TEXT, -1))
                runCurrent()
                assertTrue(vm.uiState.value.subtitleUserSelected)

                // Controls, zoom, speed, lock, seek, queue navigation
                vm.handlePlayerEvent(PlayerEvent.SetPlaybackSpeed(1.5f))
                assertEquals(1.5f, vm.uiState.value.playbackSpeed, 0.001f)

                val initialZoom = vm.uiState.value.videoZoomMode
                vm.cycleZoomMode()
                assertFalse(vm.uiState.value.videoZoomMode == initialZoom)

                vm.handlePlayerEvent(PlayerEvent.ToggleLock)
                assertTrue(vm.uiState.value.isControlsLocked)

                // findBestMatchingSource prefers exact name, then height, then first
                val ref = testSource(id = "r", name = "1080p BluRay").copy(height = 1080)
                val c1 = testSource(id = "c1", name = "720p Web").copy(height = 720)
                val c2 = testSource(id = "c2", name = "1080p bluray").copy(height = 1080)
                assertEquals(c2, vm.findBestMatchingSource(ref, listOf(c1, c2)))

                val c3 = testSource(id = "c3", name = "HD").copy(height = 1080)
                assertEquals(c3, vm.findBestMatchingSource(ref, listOf(c1, c3)))

                // initializePlaylistAndPlay + clearPlaylist + jumpToEpisode
                val localFile = tempFolder.newFile("q.mkv")
                val ep1 =
                    testEpisode(
                        id = testUuid(40),
                        sources =
                            listOf(
                                testSource(
                                    id = "s1",
                                    type = AfinitySourceType.LOCAL,
                                    path = localFile.absolutePath,
                                ),
                            ),
                    )
                val ep2 =
                    testEpisode(
                        id = testUuid(41),
                        name = "Ep 2",
                        sources =
                            listOf(
                                testSource(
                                    id = "s2",
                                    type = AfinitySourceType.LOCAL,
                                    path = localFile.absolutePath,
                                ),
                            ),
                    )
                every { h.playlistManager.getCurrentItem() } returns ep1
                coEvery { h.playlistManager.jumpToItem(ep2.id) } returns ep2
                every { h.playlistManager.getPreviousItem() } returns ep1

                vm.initializePlaylistAndPlay(
                    item = ep1,
                    mediaSourceId = "s1",
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                    startPositionMs = 0L,
                )
                runCurrent()
                assertEquals(ep1, vm.uiState.value.currentItem)

                vm.jumpToEpisode(ep2.id)
                runCurrent()
                assertEquals(ep2, vm.uiState.value.currentItem)

                vm.onPreviousEpisode()
                runCurrent()
                assertEquals(ep1, vm.uiState.value.currentItem)

                vm.clearPlaylist()
                verify { h.playlistManager.clearQueue() }
            } finally {
                h.dispose()
            }
        }
}
