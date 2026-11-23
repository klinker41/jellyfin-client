package com.makd.afinity.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.AfinityPersonImage
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.podcasts.ContinueListeningEntry
import com.makd.afinity.ui.home.components.ContinueListeningRowSection
import com.makd.afinity.ui.home.components.LatestPodcastRowSection
import com.makd.afinity.ui.preview.ScreenshotComponentPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import com.makd.afinity.ui.preview.previewWidthSizeClass
import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.PersonKind

private fun previewUuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

private val previewAudiobook =
    AfinityAudiobook(
        id = previewUuid("book-well-of-ascension"),
        name = "The Well of Ascension",
        originalTitle = null,
        overview = "",
        played = false,
        favorite = false,
        liked = false,
        canPlay = true,
        canDownload = false,
        runtimeTicks = 1_042_122_000_000L,
        playbackPositionTicks = 189_160_000_000L,
        unplayedItemCount = null,
        images = AfinityImages(primary = "preview://audiobook/well".toUri()),
        providerIds = null,
        externalUrls = null,
        album = "MB2 - The Well of Ascension",
        people =
            listOf(
                AfinityPerson(
                    id = previewUuid("author-bs"),
                    name = "Brandon Sanderson",
                    type = PersonKind.AUTHOR,
                    role = "",
                    image = AfinityPersonImage(null, null),
                ),
            ),
        publisher = null,
        premiereDate = null,
        productionYear = 2007,
        genres = emptyList(),
        communityRating = null,
        dateCreated = null,
        parentId = null,
        trackNumber = null,
        discNumber = null,
        container = "m4b",
        lastPlayedDate = LocalDateTime.of(2026, 11, 23, 14, 0),
    )

private val previewPodcastEpisodes =
    listOf(
        AfinityTrack(
            id = previewUuid("pod-dh-12"),
            name = "Discussing The Deathly Hallows - Chapter 12",
            albumId = previewUuid("album-btp"),
            album = "Behind the Portrait",
            artistId = null,
            artist = "Through the Griffin Door",
            artists = emptyList(),
            indexNumber = 12,
            discNumber = 7,
            productionYear = 2026,
            runtimeTicks = 30_244_800_000L,
            playbackPositionTicks = 7_000_000_000L,
            played = false,
            favorite = false,
            playCount = 1,
            normalizationGain = null,
            images = AfinityImages(primary = "preview://podcast/dh12".toUri()),
            lastPlayedDate = LocalDateTime.of(2026, 11, 23, 14, 15),
        ),
        AfinityTrack(
            id = previewUuid("pod-trp-1"),
            name = "Training Tuesday - Base Building",
            albumId = previewUuid("album-trp"),
            album = "The Running Public",
            artistId = null,
            artist = "The Running Public",
            artists = emptyList(),
            indexNumber = 666,
            discNumber = 1,
            productionYear = 2026,
            runtimeTicks = 42_000_000_000L,
            playbackPositionTicks = 0L,
            played = false,
            favorite = false,
            playCount = 0,
            normalizationGain = null,
            images = AfinityImages(primary = "preview://podcast/trp".toUri()),
        ),
    )

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun PodcastHomeSectionsScreenshotTest() {
    ScreenshotPreviewHost {
        val widthClass = previewWidthSizeClass()
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ContinueListeningRowSection(
                title = "Continue Listening",
                entries =
                    listOf(
                        ContinueListeningEntry.PodcastEpisode(previewPodcastEpisodes.first()),
                        ContinueListeningEntry.Audiobook(previewAudiobook),
                    ),
                onAudiobookClick = {},
                onPodcastClick = {},
                widthSizeClass = widthClass,
            )

            LatestPodcastRowSection(
                title = "Podcasts",
                episodes = previewPodcastEpisodes,
                onEpisodeClick = {},
                widthSizeClass = widthClass,
            )
        }
    }
}
