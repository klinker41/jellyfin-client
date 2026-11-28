package com.makd.afinity.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.ui.components.TransportControls
import com.makd.afinity.ui.player.components.SeekBar
import com.makd.afinity.ui.player.components.SkipButton
import com.makd.afinity.ui.player.components.UndoSkipChip
import com.makd.afinity.ui.preview.ScreenshotComponentPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewFixtures
import com.makd.afinity.ui.preview.ScreenshotPreviewHost

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun TransportControlsScreenshotTest() {
    ScreenshotPreviewHost {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            TransportControls(
                position = 0.35f,
                bufferedPosition = 0.6f,
                range = 0f..1f,
                isPlaying = true,
                isBuffering = false,
                elapsedLabel = "14:22",
                totalLabel = "41:08",
                onSeek = {},
                onPlayPauseClick = {},
                onSkipBackward = {},
                onSkipForward = {},
                onPrevious = {},
                onNext = {},
            )

            TransportControls(
                position = 0f,
                bufferedPosition = 0.1f,
                range = 0f..1f,
                isPlaying = false,
                isBuffering = true,
                previousEnabled = false,
                nextEnabled = false,
                elapsedLabel = "0:00",
                totalLabel = "57:00",
                onSeek = {},
                onPlayPauseClick = {},
                onSkipBackward = {},
                onSkipForward = {},
                onPrevious = {},
                onNext = {},
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun SkipButtonsScreenshotTest() {
    ScreenshotPreviewHost {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkipButton(
                segment = ScreenshotPreviewFixtures.introSegment,
                skipButtonText = "Skip Intro",
                onClick = {},
                isAutoSkip = false,
            )
            SkipButton(
                segment = ScreenshotPreviewFixtures.outroSegment,
                skipButtonText = "Skip Credits",
                onClick = {},
                isAutoSkip = true,
            )
            UndoSkipChip(onClick = {})
        }
    }
}

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun VideoSeekBarScreenshotTest() {
    ScreenshotPreviewHost {
        Column(
            modifier = Modifier.fillMaxWidth().background(Color.Black).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SeekBar(
                uiState =
                    PlayerViewModel.PlayerUiState(
                        duration = 2_468_000L,
                        chapters =
                            listOf(
                                AfinityChapter(startPosition = 180_000L, name = "Intro"),
                                AfinityChapter(startPosition = 1_650_000L, name = "Climax"),
                            ),
                    ),
                playbackProgress =
                    remember {
                        mutableStateOf(
                            PlayerViewModel.PlaybackProgress(
                                currentPosition = 862_000L,
                                bufferedPosition = 1_500_000L,
                            ),
                        )
                    },
                onPlayerEvent = {},
                seekBarAnchor = null,
            )

            SeekBar(
                uiState =
                    PlayerViewModel.PlayerUiState(
                        duration = 7_230_000L,
                        showRemainingTime = true,
                    ),
                playbackProgress =
                    remember {
                        mutableStateOf(
                            PlayerViewModel.PlaybackProgress(
                                currentPosition = 4_325_000L,
                                bufferedPosition = 5_100_000L,
                            ),
                        )
                    },
                onPlayerEvent = {},
                seekBarAnchor = null,
            )

            SeekBar(
                uiState = PlayerViewModel.PlayerUiState(isLiveChannel = true),
                playbackProgress = remember { mutableStateOf(PlayerViewModel.PlaybackProgress()) },
                onPlayerEvent = {},
                seekBarAnchor = null,
            )
        }
    }
}
