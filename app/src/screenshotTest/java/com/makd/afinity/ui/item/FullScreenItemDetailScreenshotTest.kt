package com.makd.afinity.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.data.models.common.DetailLayout
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.components.BackdropStyle
import com.makd.afinity.ui.components.ImageHeroKind
import com.makd.afinity.ui.components.LandscapeDetailArrangement
import com.makd.afinity.ui.components.LandscapeDetailLayout
import com.makd.afinity.ui.components.PortraitDetailLayout
import com.makd.afinity.ui.components.PortraitHero
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.item.components.movieDetailItems
import com.makd.afinity.ui.item.components.seriesDetailItems
import com.makd.afinity.ui.item.components.shared.ActionButtonsRow
import com.makd.afinity.ui.item.components.shared.LandscapeDetailTheme
import com.makd.afinity.ui.item.components.shared.MediaLogoHeader
import com.makd.afinity.ui.item.components.shared.MetadataRow
import com.makd.afinity.ui.item.components.shared.PrimaryPlaybackButton
import com.makd.afinity.ui.item.components.shared.rememberItemBackdropChain
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.ScreenshotFullScreenPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewFixtures
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import com.makd.afinity.ui.preview.previewWidthSizeClass

@PreviewTest
@ScreenshotFullScreenPreviews
@Composable
fun MovieDetailFullScreenScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        val widthClass = previewWidthSizeClass()
        val movie =
            ScreenshotPreviewFixtures.movieForMetadata().copy(
                overview =
                    "Paul Atreides arrives on Arrakis, a desert planet whose rare spice makes it the most valuable world in the universe.",
            )

        DetailFullScreenScaffold(
            item = movie,
            headerActions = { landscapeMode ->
                DetailHeaderActions(
                    item = movie,
                    nextEpisode = null,
                    isInWatchlist = true,
                    hasTrailer = true,
                    isLandscape = landscapeMode,
                )
            },
            detailItems = { horizontalPadding ->
                movieDetailItems(
                    item = movie,
                    baseUrl = "https://jellyfin.example.com",
                    specialFeatures = emptyList(),
                    containingBoxSets = listOf(PreviewData.boxSet()),
                    onSpecialFeatureClick = {},
                    onPlayClick = { _, _ -> },
                    onItemClick = {},
                    onPersonClick = {},
                    widthSizeClass = widthClass,
                    horizontalPadding = horizontalPadding,
                    detailLayout = DetailLayout.MODERN,
                )
            },
        )
    }
}

@PreviewTest
@ScreenshotFullScreenPreviews
@Composable
fun SeriesDetailFullScreenScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        val widthClass = previewWidthSizeClass()
        val show =
            PreviewData.show().copy(
                overview =
                    "Mark leads a team of office workers whose memories have been surgically divided between work and personal lives.",
                people = ScreenshotPreviewFixtures.people,
            )
        val seasons =
            listOf(
                PreviewData.season(name = "Season 1", unplayed = 0).copy(played = true),
                PreviewData.season(name = "Season 2", unplayed = 6),
            )
        val nextEpisode =
            ScreenshotPreviewFixtures.episodes.first().copy(
                runtimeTicks = 0L,
                playbackPositionTicks = 0L,
                sources = listOf(ScreenshotPreviewFixtures.source1080pHdrDts),
            )

        DetailFullScreenScaffold(
            item = show,
            headerActions = { landscapeMode ->
                DetailHeaderActions(
                    item = show,
                    nextEpisode = nextEpisode,
                    isInWatchlist = false,
                    hasTrailer = false,
                    isLandscape = landscapeMode,
                )
            },
            detailItems = { horizontalPadding ->
                seriesDetailItems(
                    item = show,
                    seasons = seasons,
                    nextEpisode = nextEpisode,
                    specialFeatures = emptyList(),
                    containingBoxSets = emptyList(),
                    onEpisodeClick = {},
                    onEpisodeMoreClick = {},
                    onSpecialFeatureClick = {},
                    onItemClick = {},
                    onSeasonClick = {},
                    onPersonClick = {},
                    widthSizeClass = widthClass,
                    horizontalPadding = horizontalPadding,
                    detailLayout = DetailLayout.MODERN,
                )
            },
        )
    }
}

@Composable
private fun DetailFullScreenScaffold(
    item: AfinityItem,
    headerActions: @Composable (isLandscape: Boolean) -> Unit,
    detailItems: LazyListScope.(horizontalPadding: Dp) -> Unit,
) {
    val isLandscape = isLandscapeWindow()
    val listState = rememberLazyListState()

    if (isLandscape) {
        LandscapeDetailTheme {
            LandscapeDetailLayout(
                backdropStyle = BackdropStyle.Cinematic,
                backdrop = rememberItemBackdropChain(item),
                listState = listState,
                arrangement = LandscapeDetailArrangement.Single,
                items = {
                    item(key = "header") {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            MediaLogoHeader(logoUrl = null, name = item.name, isLandscape = true)
                            MetadataRow(item = item)
                            headerActions(true)
                        }
                    }
                    detailItems(32.dp)
                },
            )
        }
    } else {
        PortraitDetailLayout(
            hero = PortraitHero.Image(ImageHeroKind.Backdrop, rememberItemBackdropChain(item)),
            listState = listState,
            edgeToEdgeItems = true,
            header = {
                MediaLogoHeader(logoUrl = null, name = item.name, isLandscape = false)
                MetadataRow(item = item)
                headerActions(false)
            },
            items = { detailItems(16.dp) },
        )
    }
}

@Composable
private fun DetailHeaderActions(
    item: AfinityItem,
    nextEpisode: AfinityEpisode?,
    isInWatchlist: Boolean,
    hasTrailer: Boolean,
    isLandscape: Boolean,
) {
    val primaryButton: @Composable () -> Unit = {
        PrimaryPlaybackButton(
            item = item,
            nextEpisode = nextEpisode,
            selectedMediaSource = null,
            isLoading = false,
            onPlayRequested = { _, _ -> },
        )
    }
    val actionButtons: @Composable () -> Unit = {
        ActionButtonsRow(
            item = item,
            isInWatchlist = isInWatchlist,
            hasTrailer = hasTrailer,
            downloadInfo = null,
            onPlayTrailer = {},
            onToggleWatchlist = {},
            onShufflePlay = {},
            onToggleFavorite = {},
            onToggleWatched = {},
            onDownloadClick = {},
            onPauseDownload = {},
            onResumeDownload = {},
            onCancelDownload = {},
            isLandscape = isLandscape,
        )
    }

    if (isLandscape) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.widthIn(max = 200.dp)) { primaryButton() }
            actionButtons()
        }
    } else {
        primaryButton()
        actionButtons()
    }
}
