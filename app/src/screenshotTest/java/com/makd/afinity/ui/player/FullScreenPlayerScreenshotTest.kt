package com.makd.afinity.ui.player

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.R
import com.makd.afinity.ui.components.formatClock
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.music.player.components.MusicPlayerControls
import com.makd.afinity.ui.player.cast.CastRemoteAction
import com.makd.afinity.ui.player.cast.CastRemoteLayout
import com.makd.afinity.ui.player.cast.CastRemoteTitle
import com.makd.afinity.ui.player.cast.CastRemoteTransport
import com.makd.afinity.ui.player.cast.CastRemoteValueAction
import com.makd.afinity.ui.player.components.AudioCoverArt
import com.makd.afinity.ui.player.components.AudioPlayerControlSlot
import com.makd.afinity.ui.player.components.AudioPlayerLayout
import com.makd.afinity.ui.player.components.AudioPlayerScaffold
import com.makd.afinity.ui.player.components.AudioPlayerTitleBlock
import com.makd.afinity.ui.preview.ScreenshotFullScreenPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewHost

@PreviewTest
@ScreenshotFullScreenPreviews
@Composable
fun MusicPlayerFullScreenScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        val accent = Color(0xFF7B5CFF)
        val isLandscape = isLandscapeWindow()
        AudioPlayerScaffold(
            isLandscape = isLandscape,
            isCasting = false,
            accentColor = accent,
            snackbarHostState = remember { SnackbarHostState() },
            onMinimize = {},
            onCastClick = {},
            onMoreClick = {},
            castRemote = {},
            cover = { style ->
                AudioCoverArt(
                    imageUrl = "preview://primary/ram",
                    blurHash = null,
                    placeholderIcon = painterResource(R.drawable.ic_music),
                    style = style,
                    modifier = AudioPlayerLayout.CoverSizeCap.aspectRatio(1f),
                )
            },
            info = {
                AudioPlayerTitleBlock(
                    title = "Instant Crush (feat. Julian Casablancas)",
                    subtitle = "Daft Punk",
                    accentLine = "Random Access Memories",
                    accentColor = accent,
                    isLandscape = isLandscape,
                    trailing = {
                        IconButton(onClick = {}) {
                            Icon(
                                painter = painterResource(R.drawable.ic_favorite_filled),
                                contentDescription = "Favorite",
                                tint = Color.Red,
                            )
                        }
                    },
                )
            },
            controls = {
                MusicPlayerControls(
                    positionMs = 112_000L,
                    durationMs = 337_000L,
                    bufferedPositionMs = 240_000L,
                    isPlaying = true,
                    isBuffering = false,
                    onPlayPauseClick = {},
                    onPrevious = {},
                    onNext = {},
                    onSeek = {},
                    accentColor = accent,
                    audioCodec = "FLAC",
                )
            },
            actions = {
                AudioPlayerControlSlot(
                    painter = painterResource(R.drawable.ic_arrows_shuffle),
                    contentDescription = "Shuffle",
                    onClick = {},
                    active = true,
                    activeColor = accent,
                )
                AudioPlayerControlSlot(
                    painter = painterResource(R.drawable.ic_repeat),
                    contentDescription = "Repeat",
                    onClick = {},
                    active = true,
                    activeColor = accent,
                )
                AudioPlayerControlSlot(
                    painter = painterResource(R.drawable.ic_article),
                    contentDescription = "Lyrics",
                    onClick = {},
                    activeColor = accent,
                )
                AudioPlayerControlSlot(
                    painter = painterResource(R.drawable.ic_playlist_alt),
                    contentDescription = "Queue",
                    onClick = {},
                    activeColor = accent,
                )
            },
            overlays = {},
        )
    }
}

@PreviewTest
@ScreenshotFullScreenPreviews
@Composable
fun CastRemoteFullScreenScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        val accent = Color(0xFF7B5CFF)
        CastRemoteLayout(
            artworkUrl = "preview://backdrop/dune",
            accentColor = accent,
            deviceName = "Living Room Shield TV",
            onMinimize = {},
            onDeviceClick = {},
            moreButton = {
                IconButton(onClick = {}) {
                    Icon(
                        painter = painterResource(R.drawable.ic_dots_vertical),
                        contentDescription = "More",
                        tint = Color.White,
                    )
                }
            },
            titleBlock = {
                CastRemoteTitle(
                    title = "Good News About Hell",
                    accentColor = accent,
                    overline = "Severance · S1 E1",
                    meta = "1080p · Direct Play",
                )
            },
            transport = {
                CastRemoteTransport(
                    position = 840_000f,
                    valueRange = 0f..3_420_000f,
                    formatTime = { formatClock(it.toLong()) },
                    accentColor = accent,
                    isPlaying = true,
                    isBuffering = false,
                    onSeek = {},
                    onPlayPause = {},
                    rewindPainter = painterResource(R.drawable.ic_rewind_backward_10),
                    rewindContentDescription = "Rewind 10s",
                    onRewind = {},
                    forwardPainter = painterResource(R.drawable.ic_rewind_forward_30),
                    forwardContentDescription = "Forward 30s",
                    onForward = {},
                    onPrevious = {},
                    onNext = {},
                    markFractions = listOf(0.05f, 0.92f),
                    centerLabel = "CHAPTER 1 · ARRIVAL",
                )
            },
            actions = {
                CastRemoteAction(
                    painter = painterResource(R.drawable.ic_subtitles),
                    label = "Tracks",
                    onClick = {},
                    accentColor = accent,
                )
                CastRemoteValueAction(
                    value = "1.0x",
                    label = "Speed",
                    onClick = {},
                    accentColor = accent,
                )
                CastRemoteAction(
                    painter = painterResource(R.drawable.ic_playlist_alt),
                    label = "Episodes",
                    onClick = {},
                    accentColor = accent,
                )
            },
        )
    }
}
