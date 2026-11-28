package com.makd.afinity.ui.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.makd.afinity.R
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.data.models.player.SeekStep
import com.makd.afinity.data.models.player.VideoQuality
import com.makd.afinity.testutil.TestMediaFixtures
import com.makd.afinity.ui.player.components.PlayerControls
import com.makd.afinity.ui.player.components.SEEK_BAR_CURRENT_TIME_TEST_TAG
import com.makd.afinity.ui.player.components.SEEK_BAR_END_TIME_TEST_TAG
import com.makd.afinity.ui.player.components.SeekBarAnchor
import com.makd.afinity.ui.player.components.SeekBarTimeLabels
import com.makd.afinity.ui.player.components.formatSeekBarTimeLabels
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.theme.ThemeMode
import com.makd.afinity.ui.utils.LocalIsTelevision
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w960dp-h540dp")
class SeekBarTimestampsTest {

    private val touchModeResetRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
                try {
                    base.evaluate()
                } finally {
                    InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
                }
            }
        }
    }

    val composeTestRule = createComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(touchModeResetRule).around(composeTestRule)

    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun mockSubtitleSearchViewModel(): SubtitleSearchViewModel =
        mockk(relaxed = true) {
            every { available } returns MutableStateFlow(false)
            every { uiState } returns MutableStateFlow(SubtitleSearchUiState())
            every { added } returns MutableSharedFlow()
        }

    private fun mockPlayer(): Player =
        mockk(relaxed = true) { every { currentTracks } returns Tracks.EMPTY }

    private fun defaultUiState(
        durationMs: Long = 300_000L,
        isLiveChannel: Boolean = false,
        showRemainingTime: Boolean = false,
        isSeeking: Boolean = false,
        seekPosition: Long = 0L,
    ): PlayerViewModel.PlayerUiState {
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
        val source =
            TestMediaFixtures.testSource(
                id = "source-1",
                name = "1080p BluRay",
                mediaStreams = listOf(videoStream, audioStream),
            )
        val movie = PreviewData.movie().copy(sources = listOf(source))
        return PlayerViewModel.PlayerUiState(
            currentItem = movie,
            currentMediaSourceId = source.id,
            availableSources = listOf(source),
            showControls = true,
            showPlayButton = true,
            isPlaying = true,
            playWhenReady = true,
            duration = durationMs,
            isLiveChannel = isLiveChannel,
            showRemainingTime = showRemainingTime,
            isSeeking = isSeeking,
            seekPosition = seekPosition,
            seekBackStep = SeekStep.TEN,
            seekForwardStep = SeekStep.TEN,
            availableQualities = listOf(VideoQuality.ORIGINAL),
            videoQuality = VideoQuality.ORIGINAL,
            outputVideoWidth = 1920,
            outputVideoHeight = 1080,
            audioStreamIndex = 1,
            subtitleStreamIndex = -1,
        )
    }

    @Test
    fun `formatSeekBarTimeLabels formats current and total time under and over one hour`() {
        assertEquals(
            SeekBarTimeLabels(current = "1:05", end = "5:00"),
            formatSeekBarTimeLabels(
                positionMs = 65_000L,
                durationMs = 300_000L,
                showRemainingTime = false,
            ),
        )
        assertEquals(
            SeekBarTimeLabels(current = "1:01:01", end = "2:15:30"),
            formatSeekBarTimeLabels(
                positionMs = 3_661_000L,
                durationMs = 8_130_000L,
                showRemainingTime = false,
            ),
        )
    }

    @Test
    fun `formatSeekBarTimeLabels formats remaining time and clamps negative or overflow values`() {
        assertEquals(
            SeekBarTimeLabels(current = "1:05", end = "-3:55"),
            formatSeekBarTimeLabels(
                positionMs = 65_000L,
                durationMs = 300_000L,
                showRemainingTime = true,
            ),
        )
        assertEquals(
            SeekBarTimeLabels(current = "0:00", end = "0:00"),
            formatSeekBarTimeLabels(
                positionMs = -5_000L,
                durationMs = -10_000L,
                showRemainingTime = false,
            ),
        )
        assertEquals(
            SeekBarTimeLabels(current = "6:00", end = "-0:00"),
            formatSeekBarTimeLabels(
                positionMs = 360_000L,
                durationMs = 300_000L,
                showRemainingTime = true,
            ),
        )
    }

    @Test
    fun `seekBar displays current time at start and total time at end above full-width slider`() {
        val progressState =
            mutableStateOf(
                PlayerViewModel.PlaybackProgress(
                    currentPosition = 60_000L,
                    bufferedPosition = 120_000L,
                ),
            )

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                Box(modifier = Modifier.fillMaxSize().testTag("player_root")) {
                    PlayerControls(
                        uiState = defaultUiState(durationMs = 300_000L),
                        playbackProgress = progressState,
                        player = mockPlayer(),
                        onPlayerEvent = {},
                        onBackClick = {},
                        subtitleSearchViewModel = mockSubtitleSearchViewModel(),
                    )
                }
            }
        }

        val currentNode = composeTestRule.onNodeWithTag(SEEK_BAR_CURRENT_TIME_TEST_TAG)
        val endNode = composeTestRule.onNodeWithTag(SEEK_BAR_END_TIME_TEST_TAG)
        val seekBarNode =
            composeTestRule.onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            )
        val rootBounds = composeTestRule.onNodeWithTag("player_root").getBoundsInRoot()

        currentNode.assertIsDisplayed().assertTextEquals("1:00")
        endNode.assertIsDisplayed().assertTextEquals("5:00")

        val currentBounds = currentNode.getBoundsInRoot()
        val endBounds = endNode.getBoundsInRoot()
        val seekBounds = seekBarNode.getBoundsInRoot()

        assertTrue(currentBounds.bottom <= seekBounds.top)
        assertTrue(endBounds.bottom <= seekBounds.top)
        assertEquals(rootBounds.left.value + 24f, currentBounds.left.value, 1f)
        assertEquals(rootBounds.right.value - 24f, endBounds.right.value, 1f)
        assertTrue(seekBounds.left <= currentBounds.left)
        assertTrue(seekBounds.right >= endBounds.right)

        val minExpectedWidth = (rootBounds.right.value - rootBounds.left.value) - 48f
        assertTrue((seekBounds.right.value - seekBounds.left.value) >= minExpectedWidth - 1.5f)
    }

    @Test
    fun `seekBar timestamps do not overlap on compact phone width`() {
        val progressState =
            mutableStateOf(
                PlayerViewModel.PlaybackProgress(
                    currentPosition = 3_661_000L,
                    bufferedPosition = 4_000_000L,
                ),
            )

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                Box(modifier = Modifier.width(320.dp).height(640.dp).testTag("compact_root")) {
                    PlayerControls(
                        uiState = defaultUiState(durationMs = 8_130_000L),
                        playbackProgress = progressState,
                        player = mockPlayer(),
                        onPlayerEvent = {},
                        onBackClick = {},
                        subtitleSearchViewModel = mockSubtitleSearchViewModel(),
                    )
                }
            }
        }

        val currentNode = composeTestRule.onNodeWithTag(SEEK_BAR_CURRENT_TIME_TEST_TAG)
        val endNode = composeTestRule.onNodeWithTag(SEEK_BAR_END_TIME_TEST_TAG)
        val seekBarNode =
            composeTestRule.onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            )
        val rootBounds = composeTestRule.onNodeWithTag("compact_root").getBoundsInRoot()

        currentNode.assertIsDisplayed().assertTextEquals("1:01:01")
        endNode.assertIsDisplayed().assertTextEquals("2:15:30")

        val currentBounds = currentNode.getBoundsInRoot()
        val endBounds = endNode.getBoundsInRoot()
        val seekBounds = seekBarNode.getBoundsInRoot()

        assertTrue(currentBounds.right < endBounds.left)
        assertTrue(currentBounds.bottom <= seekBounds.top)
        assertTrue(endBounds.bottom <= seekBounds.top)
        assertTrue(seekBounds.left <= currentBounds.left)
        assertTrue(seekBounds.right >= endBounds.right)
        assertTrue(
            (seekBounds.right.value - seekBounds.left.value) >=
                (rootBounds.right.value - rootBounds.left.value) - 49.5f,
        )
    }

    @Test
    fun `scrubbing updates start timestamp while clicking end timestamp toggles remaining time`() {
        val uiState =
            mutableStateOf(
                defaultUiState(
                    durationMs = 300_000L,
                    isSeeking = true,
                    seekPosition = 125_000L,
                ),
            )
        val progressState =
            mutableStateOf(
                PlayerViewModel.PlaybackProgress(
                    currentPosition = 60_000L,
                    bufferedPosition = 180_000L,
                ),
            )
        val events = mutableListOf<PlayerEvent>()

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                PlayerControls(
                    uiState = uiState.value,
                    playbackProgress = progressState,
                    player = mockPlayer(),
                    onPlayerEvent = { event ->
                        events += event
                        if (event is PlayerEvent.ToggleRemainingTime) {
                            uiState.value =
                                uiState.value.copy(
                                    showRemainingTime = !uiState.value.showRemainingTime,
                                )
                        }
                    },
                    onBackClick = {},
                    subtitleSearchViewModel = mockSubtitleSearchViewModel(),
                )
            }
        }

        val currentNode = composeTestRule.onNodeWithTag(SEEK_BAR_CURRENT_TIME_TEST_TAG)
        val endNode = composeTestRule.onNodeWithTag(SEEK_BAR_END_TIME_TEST_TAG)

        currentNode.assertTextEquals("2:05")
        endNode.assertTextEquals("5:00")

        endNode.performClick()
        composeTestRule.waitForIdle()

        assertTrue(events.contains(PlayerEvent.ToggleRemainingTime))
        currentNode.assertTextEquals("2:05")
        endNode.assertTextEquals("-2:55")
    }

    @Test
    fun `seekBarAnchor encompasses timestamps above slider for trickplay clearance`() {
        val seekBarAnchor = SeekBarAnchor()
        lateinit var density: Density
        val progressState =
            mutableStateOf(
                PlayerViewModel.PlaybackProgress(
                    currentPosition = 60_000L,
                    bufferedPosition = 120_000L,
                ),
            )

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                density = LocalDensity.current
                PlayerControls(
                    uiState = defaultUiState(durationMs = 300_000L),
                    playbackProgress = progressState,
                    player = mockPlayer(),
                    onPlayerEvent = {},
                    onBackClick = {},
                    seekBarAnchor = seekBarAnchor,
                    subtitleSearchViewModel = mockSubtitleSearchViewModel(),
                )
            }
        }

        composeTestRule.waitForIdle()
        val anchorBounds = seekBarAnchor.bounds
        assertNotNull(anchorBounds)
        anchorBounds!!

        val currentBounds =
            composeTestRule.onNodeWithTag(SEEK_BAR_CURRENT_TIME_TEST_TAG).getBoundsInRoot()
        val endBounds = composeTestRule.onNodeWithTag(SEEK_BAR_END_TIME_TEST_TAG).getBoundsInRoot()

        with(density) {
            assertTrue(anchorBounds.top <= currentBounds.top.toPx() + 1f)
            assertEquals(currentBounds.left.toPx(), anchorBounds.left, 1f)
            assertEquals(endBounds.right.toPx(), anchorBounds.right, 1f)
        }
    }

    @Test
    fun `live channel hides timestamps and shows live badge`() {
        val progressState =
            mutableStateOf(
                PlayerViewModel.PlaybackProgress(currentPosition = 0L, bufferedPosition = 0L),
            )

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                PlayerControls(
                    uiState = defaultUiState(isLiveChannel = true),
                    playbackProgress = progressState,
                    player = mockPlayer(),
                    onPlayerEvent = {},
                    onBackClick = {},
                    subtitleSearchViewModel = mockSubtitleSearchViewModel(),
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.livetv_live_badge))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(SEEK_BAR_CURRENT_TIME_TEST_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(SEEK_BAR_END_TIME_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `tv dpad pressing up from seekBar skips non-focusable end timestamp`() {
        val progressState =
            mutableStateOf(
                PlayerViewModel.PlaybackProgress(
                    currentPosition = 60_000L,
                    bufferedPosition = 120_000L,
                ),
            )

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
                CompositionLocalProvider(LocalIsTelevision provides true) {
                    PlayerControls(
                        uiState = defaultUiState(durationMs = 300_000L),
                        playbackProgress = progressState,
                        player = mockPlayer(),
                        onPlayerEvent = {},
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

        seekBarNode.performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.waitForIdle()

        seekBarNode.assertIsNotFocused()
        composeTestRule.onNodeWithTag(SEEK_BAR_END_TIME_TEST_TAG).assertIsNotFocused()
    }
}
