package com.makd.afinity.ui.player

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.test.core.app.ApplicationProvider
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.player.PlaybackStats
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.data.models.player.SeekStep
import com.makd.afinity.data.models.player.SleepTimerMode
import com.makd.afinity.data.models.player.VideoQuality
import com.makd.afinity.testutil.TestMediaFixtures
import com.makd.afinity.ui.player.components.ChapterSwitcher
import com.makd.afinity.ui.player.components.EpisodeSwitcher
import com.makd.afinity.ui.player.components.PlaybackSpeedPanel
import com.makd.afinity.ui.player.components.PlaybackStatsOverlay
import com.makd.afinity.ui.player.components.PlayerControls
import com.makd.afinity.ui.player.components.PlayerOverlayBackdrop
import com.makd.afinity.ui.player.components.PlayerSeekBarDpadController
import com.makd.afinity.ui.player.components.SleepTimerPanel
import com.makd.afinity.ui.player.components.VersionPickerSheet
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.theme.ThemeMode
import com.makd.afinity.ui.utils.LocalIsTelevision
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w960dp-h540dp")
class PlayerTvControlsRobolectricTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun mockSubtitleSearchViewModel(): SubtitleSearchViewModel =
        mockk(relaxed = true) {
            every { available } returns MutableStateFlow(false)
            every { uiState } returns MutableStateFlow(SubtitleSearchUiState())
            every { added } returns MutableSharedFlow()
        }

    private fun mockPlayer(): Player =
        mockk(relaxed = true) { every { currentTracks } returns Tracks.EMPTY }

    private fun testSourceWithStreams(): AfinitySource {
        val videoStream =
            TestMediaFixtures.testStream(
                    index = 0,
                    type = MediaStreamType.VIDEO,
                    language = "und",
                    codec = "h264",
                    isDefault = true,
                    title = "1080p H264",
                )
                .copy(width = 1920, height = 1080)
        val audioStream =
            TestMediaFixtures.testStream(
                index = 1,
                type = MediaStreamType.AUDIO,
                language = "eng",
                codec = "aac",
                isDefault = true,
                title = "English Stereo",
            )
        return TestMediaFixtures.testSource(
                id = "source-1",
                name = "1080p BluRay",
                mediaStreams = listOf(videoStream, audioStream),
            )
            .copy(
                width = 1920,
                height = 1080,
                videoCodec = "h264",
                bitrate = 8_000_000L,
                container = "mkv",
            )
    }

    private fun defaultUiState(
        showControls: Boolean = true,
        showPlayButton: Boolean = true,
        isPlaying: Boolean = true,
        isBuffering: Boolean = false,
        playWhenReady: Boolean = true,
        chapters: List<AfinityChapter> = emptyList(),
        qualities: List<VideoQuality> =
            listOf(VideoQuality.ORIGINAL, VideoQuality(8_000_000, 1920)),
    ): PlayerViewModel.PlayerUiState {
        val source = testSourceWithStreams()
        val movie = PreviewData.movie().copy(sources = listOf(source))
        return PlayerViewModel.PlayerUiState(
            currentItem = movie,
            currentMediaSourceId = source.id,
            availableSources = listOf(source),
            showControls = showControls,
            showPlayButton = showPlayButton,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            playWhenReady = playWhenReady,
            duration = 300_000L,
            seekBackStep = SeekStep.TEN,
            seekForwardStep = SeekStep.TEN,
            availableQualities = qualities,
            videoQuality = VideoQuality.ORIGINAL,
            outputVideoWidth = 1920,
            outputVideoHeight = 1080,
            chapters = chapters,
            audioStreamIndex = 1,
            subtitleStreamIndex = -1,
        )
    }

    @Test
    fun `seekBar dpad scrubbing accumulates left and right presses and commits without reverting on KeyUp`() {
        val events = mutableListOf<PlayerEvent>()
        val progressState =
            mutableStateOf(
                PlayerViewModel.PlaybackProgress(
                    currentPosition = 60_000L,
                    bufferedPosition = 90_000L,
                ),
            )
        val uiState = mutableStateOf(defaultUiState())

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
                CompositionLocalProvider(LocalIsTelevision provides true) {
                    PlayerControls(
                        uiState = uiState.value,
                        playbackProgress = progressState,
                        player = mockPlayer(),
                        onPlayerEvent = { event ->
                            events += event
                            when (event) {
                                is PlayerEvent.OnSeekBarDragStart ->
                                    uiState.value = uiState.value.copy(isSeeking = true)
                                is PlayerEvent.OnSeekBarValueChange ->
                                    uiState.value =
                                        uiState.value.copy(
                                            seekPosition = event.positionMs,
                                            trickplayPreviewPosition = event.positionMs,
                                        )
                                is PlayerEvent.OnSeekBarDragFinished -> {
                                    progressState.value =
                                        progressState.value.copy(currentPosition = event.positionMs)
                                    uiState.value = uiState.value.copy(isSeeking = false)
                                }
                                else -> Unit
                            }
                        },
                        onBackClick = {},
                        subtitleSearchViewModel = mockSubtitleSearchViewModel(),
                    )
                }
            }
        }

        val seekBarNode =
            composeTestRule.onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            )
        seekBarNode.performSemanticsAction(SemanticsActions.RequestFocus)
        seekBarNode.assertIsFocused()

        // Press Right twice (60s -> 70s -> 80s), then press Center to commit immediately
        seekBarNode.performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionRight)
        }
        composeTestRule.waitForIdle()
        assertTrue(events.contains(PlayerEvent.OnSeekBarDragStart))
        assertTrue(events.contains(PlayerEvent.OnSeekBarValueChange(70_000L)))
        assertTrue(events.contains(PlayerEvent.OnSeekBarValueChange(80_000L)))
        assertFalse(events.any { it is PlayerEvent.OnSeekBarDragFinished })

        // Press Left once (80s -> 70s), then press Center to commit 70s
        seekBarNode.performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.waitForIdle()
        assertEquals(
            PlayerEvent.OnSeekBarDragFinished(70_000L),
            events.last { it is PlayerEvent.OnSeekBarDragFinished },
        )
        assertEquals(70_000L, progressState.value.currentPosition)

        // Pressing Center when not scrubbing toggles Play/Pause
        seekBarNode.performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
        assertTrue(events.last() is PlayerEvent.Pause)

        // Scrub right (70s -> 80s), then press Up to commit 80s and move focus off SeekBar
        seekBarNode.performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionUp)
        }
        composeTestRule.waitForIdle()
        assertEquals(
            PlayerEvent.OnSeekBarDragFinished(80_000L),
            events.last { it is PlayerEvent.OnSeekBarDragFinished },
        )
        seekBarNode.assertIsNotFocused()
    }

    @Test
    fun `seekBar dpad controller commits after debounce and blocks syncplay repeat holds`() =
        runTest {
            val events = mutableListOf<PlayerEvent>()
            var keepAliveCount = 0
            var toggledPlayPause = 0
            val controller =
                PlayerSeekBarDpadController(
                    coroutineScope = CoroutineScope(coroutineContext),
                    onPlayerEvent = { events += it },
                    onKeepControlsVisible = { keepAliveCount++ },
                    commitDebounceMs = 450L,
                )

            assertTrue(
                controller.handleKeyEvent(
                    key = Key.DirectionRight,
                    type = KeyEventType.KeyDown,
                    repeatCount = 0,
                    currentPositionMs = 20_000L,
                    durationMs = 100_000L,
                    seekBackMs = 10_000L,
                    seekForwardMs = 10_000L,
                    isSyncPlay = false,
                    onTogglePlayPause = { toggledPlayPause++ },
                ),
            )
            assertTrue(
                controller.handleKeyEvent(
                    key = Key.DirectionRight,
                    type = KeyEventType.KeyUp,
                    repeatCount = 0,
                    currentPositionMs = 20_000L,
                    durationMs = 100_000L,
                    seekBackMs = 10_000L,
                    seekForwardMs = 10_000L,
                    isSyncPlay = false,
                    onTogglePlayPause = { toggledPlayPause++ },
                ),
            )
            assertEquals(1, keepAliveCount)
            assertFalse(events.any { it is PlayerEvent.OnSeekBarDragFinished })

            advanceTimeBy(450L)
            runCurrent()
            assertEquals(
                listOf(
                    PlayerEvent.OnSeekBarDragStart,
                    PlayerEvent.OnSeekBarValueChange(30_000L),
                    PlayerEvent.OnSeekBarDragFinished(30_000L),
                ),
                events,
            )

            // SyncPlay ignores repeated hold events
            events.clear()
            assertTrue(
                controller.handleKeyEvent(
                    key = Key.DirectionLeft,
                    type = KeyEventType.KeyDown,
                    repeatCount = 1,
                    currentPositionMs = 30_000L,
                    durationMs = 100_000L,
                    seekBackMs = 10_000L,
                    seekForwardMs = 10_000L,
                    isSyncPlay = true,
                    onTogglePlayPause = { toggledPlayPause++ },
                ),
            )
            assertTrue(events.isEmpty())
            assertEquals(0, toggledPlayPause)
        }

    @Test
    fun `center rewind keeps all center buttons visible across seek buffering`() {
        val uiState = mutableStateOf(defaultUiState())
        val progressState =
            mutableStateOf(
                PlayerViewModel.PlaybackProgress(
                    currentPosition = 60_000L,
                    bufferedPosition = 90_000L,
                ),
            )
        var seekRelativeEvents = 0

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(LocalIsTelevision provides true) {
                    PlayerControls(
                        uiState = uiState.value,
                        playbackProgress = progressState,
                        player = mockPlayer(),
                        onPlayerEvent = { event ->
                            if (event is PlayerEvent.SeekRelative) {
                                seekRelativeEvents++
                            }
                        },
                        onBackClick = {},
                        subtitleSearchViewModel = mockSubtitleSearchViewModel(),
                    )
                }
            }
        }

        val rewindDesc = context.getString(R.string.cd_rewind_seconds_fmt, 10)
        val forwardDesc = context.getString(R.string.cd_forward_seconds_fmt, 10)
        val pauseDesc = context.getString(R.string.cd_pause)

        composeTestRule.onNodeWithContentDescription(rewindDesc).performClick()
        assertEquals(1, seekRelativeEvents)

        // Simulate ExoPlayer briefly entering STATE_BUFFERING then returning to STATE_READY
        uiState.value = uiState.value.copy(isPlaying = false, isBuffering = true)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(rewindDesc).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(forwardDesc).assertIsDisplayed()

        uiState.value = uiState.value.copy(isPlaying = true, isBuffering = false)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(rewindDesc).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(pauseDesc).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(forwardDesc).assertIsDisplayed()
    }

    @Test
    fun `track panel traps dpad focus, dismisses on back, and restores controls focus`() {
        var backExitCalled = false
        lateinit var backDispatcher: OnBackPressedDispatcher
        val progressState =
            mutableStateOf(
                PlayerViewModel.PlaybackProgress(
                    currentPosition = 60_000L,
                    bufferedPosition = 90_000L,
                ),
            )

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
                backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                CompositionLocalProvider(LocalIsTelevision provides true) {
                    PlayerControls(
                        uiState = defaultUiState().copy(playMethod = PlayMethod.TRANSCODE),
                        playbackProgress = progressState,
                        player = mockPlayer(),
                        onPlayerEvent = {},
                        onBackClick = { backExitCalled = true },
                        subtitleSearchViewModel = mockSubtitleSearchViewModel(),
                    )
                }
            }
        }

        val tracksLabel = context.getString(R.string.player_tracks_label)
        val tracksTitle = context.getString(R.string.player_audio_title)
        val backDesc = context.getString(R.string.cd_back)

        composeTestRule
            .onNodeWithContentDescription(tracksLabel)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithContentDescription(tracksLabel).assertIsFocused()
        composeTestRule.onNodeWithContentDescription(tracksLabel).performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(tracksTitle).assertIsDisplayed()

        // First audio track receives initial focus; pressing Up/Left stays inside TrackPanel
        val noneLabel = context.getString(R.string.track_none)
        val audioRow = composeTestRule.onNodeWithText("English • AAC Stereo")
        audioRow.assertIsFocused()
        audioRow.performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionLeft)
        }
        composeTestRule.onNodeWithContentDescription(backDesc).assertIsNotFocused()
        composeTestRule.onNodeWithText(noneLabel).assertIsDisplayed()

        // Back dismisses TrackPanel without exiting player and restores focus to Tracks button
        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(tracksTitle).assertDoesNotExist()
        assertFalse(backExitCalled)
        composeTestRule.onNodeWithContentDescription(tracksLabel).assertIsFocused()
    }

    @Test
    fun `playback speed panel steps slider on dpad without dismissing and dismisses on center or back`() {
        var lastSpeed = 1.0f
        var dismissedCount = 0
        lateinit var backDispatcher: OnBackPressedDispatcher
        var showPanel by mutableStateOf(true)

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
                backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                CompositionLocalProvider(LocalIsTelevision provides true) {
                    if (showPanel) {
                        PlayerOverlayBackdrop(
                            onDismiss = {
                                dismissedCount++
                                showPanel = false
                            },
                        ) {
                            PlaybackSpeedPanel(
                                currentSpeed = 1.0f,
                                onSpeedChange = { lastSpeed = it },
                                onDismiss = {
                                    dismissedCount++
                                    showPanel = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // Initial focus lands on 1.00x preset; move Up to the speed slider
        val chip1x =
            composeTestRule.onNodeWithText(context.getString(R.string.player_speed_value_fmt, 1.0f))
        chip1x.assertIsFocused()
        chip1x.performKeyInput { pressKey(Key.DirectionUp) }

        val sliderNode =
            composeTestRule.onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            )
        sliderNode.assertIsFocused()
        sliderNode.performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.waitForIdle()
        assertEquals(1.25f, lastSpeed, 0.01f)
        assertEquals(0, dismissedCount)

        // Pressing Back dismisses cleanly
        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        assertEquals(1, dismissedCount)
        assertFalse(showPanel)
    }

    @Test
    fun `overlay popups trap focus and dismiss cleanly on back`() {
        lateinit var backDispatcher: OnBackPressedDispatcher
        var activePopup by mutableStateOf("chapter")

        val chapters =
            listOf(
                AfinityChapter(startPosition = 0L, name = "Opening"),
                AfinityChapter(startPosition = 60_000L, name = "Chapter Two"),
            )
        val episodes =
            listOf(
                PreviewData.episode(),
                PreviewData.episode().copy(id = UUID.randomUUID(), name = "Episode Two"),
            )
        val sources =
            listOf(
                testSourceWithStreams(),
                testSourceWithStreams().copy(id = "source-2", name = "720p Web"),
            )

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
                backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                CompositionLocalProvider(LocalIsTelevision provides true) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Button(
                            onClick = {},
                            modifier =
                                Modifier.testTag("underlying_btn")
                                    .focusRequester(remember { FocusRequester() }),
                        ) {
                            Text("Underlying")
                        }

                        when (activePopup) {
                            "chapter" ->
                                ChapterSwitcher(
                                    chapters = chapters,
                                    currentPosition = { 0L },
                                    itemId = UUID.randomUUID(),
                                    baseUrl = "https://example.com",
                                    onChapterClick = {},
                                    onDismiss = { activePopup = "none" },
                                )
                            "episode" ->
                                EpisodeSwitcher(
                                    episodes = episodes,
                                    currentIndex = 0,
                                    isPlaying = true,
                                    onEpisodeClick = {},
                                    onDismiss = { activePopup = "none" },
                                )
                            "sleep" ->
                                SleepTimerPanel(
                                    uiState =
                                        defaultUiState()
                                            .copy(
                                                sleepTimerMode = SleepTimerMode.Duration(15),
                                            ),
                                    endOfItemRemainingMs = { 60_000L },
                                    onSelectMode = {},
                                    onCancel = {},
                                    onDismiss = { activePopup = "none" },
                                )
                            "version" ->
                                PlayerOverlayBackdrop(onDismiss = { activePopup = "none" }) {
                                    VersionPickerSheet(
                                        sources = sources,
                                        currentSourceId = "source-1",
                                        onVersionSelected = {},
                                        onDismiss = { activePopup = "none" },
                                    )
                                }
                            "stats" ->
                                PlaybackStatsOverlay(
                                    stats =
                                        PlaybackStats(
                                            playerType = "ExoPlayer",
                                            videoOutput = "Surface",
                                            playMethod = "DirectPlay",
                                            connection = "Local",
                                            hwDec = "MediaCodec",
                                            decoderName = "c2.android.avc.decoder",
                                            avSync = "0 ms",
                                            bufferHealth = "25s",
                                            networkSpeed = "50 Mbps",
                                            cached = "10 MB",
                                            videoBitrate = "8 Mbps",
                                            audioBitrate = "320 kbps",
                                            videoResolution = "1920x1080",
                                            videoRange = "SDR",
                                            colorInfo = "BT.709",
                                            frameRate = "24 fps",
                                            droppedFrames = 0,
                                            container = "MKV",
                                            videoCodec = "H.264",
                                            audioCodec = "AAC",
                                            audioChannels = 2,
                                            audioSampleRate = 48_000,
                                        ),
                                    onClose = { activePopup = "none" },
                                )
                        }
                    }
                }
            }
        }

        // ChapterSwitcher
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Opening").assertIsFocused()
        composeTestRule.onNodeWithText("Opening").performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.onNodeWithTag("underlying_btn").assertIsNotFocused()
        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        assertEquals("none", activePopup)

        // EpisodeSwitcher
        activePopup = "episode"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(episodes.first().name).assertIsFocused()
        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        assertEquals("none", activePopup)

        // SleepTimerPanel
        activePopup = "sleep"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("15").assertIsFocused()
        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        assertEquals("none", activePopup)

        // VersionPickerSheet
        activePopup = "version"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1080p BluRay").assertIsFocused()
        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        assertEquals("none", activePopup)

        // PlaybackStatsOverlay
        activePopup = "stats"
        composeTestRule.waitForIdle()
        val closeStatsBtn =
            composeTestRule.onNodeWithContentDescription(
                context.getString(R.string.cd_playback_stats_close),
            )
        closeStatsBtn.assertIsFocused()
        closeStatsBtn.performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        assertEquals("none", activePopup)
    }

    @Test
    fun `pause details overlay focuses resume button and dismisses on back`() {
        var showControlsCalls = 0
        lateinit var backDispatcher: OnBackPressedDispatcher
        val progressState =
            mutableStateOf(
                PlayerViewModel.PlaybackProgress(
                    currentPosition = 60_000L,
                    bufferedPosition = 90_000L,
                ),
            )

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
                backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                CompositionLocalProvider(LocalIsTelevision provides true) {
                    PlayerControls(
                        uiState =
                            defaultUiState(
                                    showControls = false,
                                    showPlayButton = false,
                                    isPlaying = false,
                                    playWhenReady = false,
                                )
                                .copy(pauseScreenEnabled = true, pauseScreenDelaySeconds = 0),
                        playbackProgress = progressState,
                        player = mockPlayer(),
                        onPlayerEvent = {},
                        onBackClick = {},
                        onShowControls = { showControlsCalls++ },
                        subtitleSearchViewModel = mockSubtitleSearchViewModel(),
                    )
                }
            }
        }

        val resumeText = context.getString(R.string.player_resume)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(resumeText).assertIsDisplayed()
        composeTestRule.onNodeWithText(resumeText).assertIsFocused()

        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(resumeText).assertDoesNotExist()
        assertEquals(1, showControlsCalls)
    }

    @Test
    fun `sleep timer ended overlay marks panel open, traps focus, and resumes on dpad center`() {
        var panelOpen = false
        val events = mutableListOf<PlayerEvent>()
        val progressState =
            mutableStateOf(
                PlayerViewModel.PlaybackProgress(
                    currentPosition = 60_000L,
                    bufferedPosition = 90_000L,
                ),
            )

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
                CompositionLocalProvider(LocalIsTelevision provides true) {
                    PlayerControls(
                        uiState =
                            defaultUiState(
                                    showControls = false,
                                    showPlayButton = false,
                                    isPlaying = false,
                                    playWhenReady = false,
                                )
                                .copy(sleepTimerExpired = true),
                        playbackProgress = progressState,
                        player = mockPlayer(),
                        onPlayerEvent = { events += it },
                        onBackClick = {},
                        onPanelOpenChange = { panelOpen = it },
                        subtitleSearchViewModel = mockSubtitleSearchViewModel(),
                    )
                }
            }
        }

        val resumeText = context.getString(R.string.player_resume)
        composeTestRule.waitForIdle()
        assertTrue(panelOpen)
        val resumeButton = composeTestRule.onNodeWithText(resumeText)
        resumeButton.assertIsDisplayed()
        resumeButton.assertIsFocused()

        resumeButton.performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.waitForIdle()
        assertTrue(events.contains(PlayerEvent.ResumeFromSleepTimer))
    }
}
