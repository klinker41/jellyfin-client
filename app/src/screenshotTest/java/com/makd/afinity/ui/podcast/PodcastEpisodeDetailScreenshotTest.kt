package com.makd.afinity.ui.podcast

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.ui.preview.ScreenshotFullScreenPreviews
import com.makd.afinity.ui.preview.ScreenshotPhonePreview
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import java.time.LocalDateTime
import java.util.UUID

private fun previewUuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

private val previewEpisode =
    AfinityTrack(
        id = previewUuid("pod-episode-detail"),
        name = "Training Tuesday - Building Aerobic Capacity Without Burning Out",
        albumId = previewUuid("album-trp"),
        album = "The Running Public",
        artistId = previewUuid("artist-trp"),
        artist = "Brakken Kraker & Kirk DeWindt",
        artists = listOf("Brakken Kraker", "Kirk DeWindt"),
        indexNumber = 665,
        discNumber = 1,
        productionYear = 2026,
        runtimeTicks = 42_000_000_000L,
        playbackPositionTicks = 14_700_000_000L,
        played = false,
        favorite = true,
        playCount = 0,
        normalizationGain = null,
        images = AfinityImages(primary = "preview://podcast/trp-665".toUri()),
        dateCreated = LocalDateTime.of(2026, 11, 22, 3, 40),
        premiereDate = LocalDateTime.of(2026, 11, 22, 3, 40),
        overview =
            "We break down how to structure aerobic base blocks across a 16-week build, when to cap intensity on tired legs, and how to pace long runs by effort instead of ego.",
    )

@PreviewTest
@ScreenshotFullScreenPreviews
@Composable
fun PodcastEpisodeDetailScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        PodcastEpisodeDetailScaffold(
            uiState = PodcastEpisodeDetailUiState(episode = previewEpisode, isLoading = false),
            isOffline = false,
            downloadInfo = null,
            allowedByServer = true,
            allowedOnNetwork = true,
            onRetry = {},
            onShowClick = {},
            onPlayClick = { _, _ -> },
            onTogglePlayed = {},
            onToggleFavorite = {},
            onDownloadClick = {},
            onDownloadLongClick = {},
            onPauseDownload = {},
            onResumeDownload = {},
            onCancelDownload = {},
        )
    }
}

@PreviewTest
@ScreenshotPhonePreview
@Composable
fun PodcastEpisodeDetailFinishedScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        PodcastEpisodeDetailScaffold(
            uiState =
                PodcastEpisodeDetailUiState(
                    episode = previewEpisode.copy(played = true, playbackPositionTicks = 0L),
                    isLoading = false,
                ),
            isOffline = false,
            downloadInfo = null,
            allowedByServer = true,
            allowedOnNetwork = true,
            onRetry = {},
            onShowClick = {},
            onPlayClick = { _, _ -> },
            onTogglePlayed = {},
            onToggleFavorite = {},
            onDownloadClick = {},
            onDownloadLongClick = {},
            onPauseDownload = {},
            onResumeDownload = {},
            onCancelDownload = {},
        )
    }
}
