package com.makd.afinity.ui.item

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.data.models.common.DetailLayout
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopBar
import com.makd.afinity.ui.components.AppNavigationDrawerContent
import com.makd.afinity.ui.components.AppPinnedNavigationDrawer
import com.makd.afinity.ui.components.BackdropStyle
import com.makd.afinity.ui.components.DetailGutter
import com.makd.afinity.ui.components.ImageHeroKind
import com.makd.afinity.ui.components.LandscapeDetailArrangement
import com.makd.afinity.ui.components.LandscapeDetailLayout
import com.makd.afinity.ui.components.LocalPinnedDrawerStartInset
import com.makd.afinity.ui.components.PinnedDrawerStartOccupancy
import com.makd.afinity.ui.components.PortraitDetailLayout
import com.makd.afinity.ui.components.PortraitHero
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.components.pinnedDrawerBlurSource
import com.makd.afinity.ui.components.rememberDetailTopBarBlurState
import com.makd.afinity.ui.components.rememberPinnedDrawerBlurState
import com.makd.afinity.ui.item.components.seriesDetailItems
import com.makd.afinity.ui.item.components.shared.ActionButtonsRow
import com.makd.afinity.ui.item.components.shared.MediaLogoHeader
import com.makd.afinity.ui.item.components.shared.MetadataRow
import com.makd.afinity.ui.item.components.shared.PrimaryPlaybackButton
import com.makd.afinity.ui.item.components.shared.detailItem
import com.makd.afinity.ui.item.components.shared.rememberItemBackdropChain
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.ScreenshotPreviewFixtures
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import com.makd.afinity.ui.preview.previewWidthSizeClass
import com.makd.afinity.ui.settings.SessionSwitcherState

private const val NIGHT = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL

@Preview(
    name = "Foldable unfolded dark",
    group = "DetailPinnedDrawer",
    device = "spec:width=673dp,height=841dp",
    uiMode = NIGHT,
)
@Preview(
    name = "Foldable unfolded light",
    group = "DetailPinnedDrawer",
    device = "spec:width=673dp,height=841dp",
)
@Preview(
    name = "Foldable unfolded landscape",
    group = "DetailPinnedDrawer",
    device = "spec:width=841dp,height=673dp",
    uiMode = NIGHT,
)
@Preview(
    name = "Tablet landscape",
    group = "DetailPinnedDrawer",
    device = "spec:width=1280dp,height=800dp",
    uiMode = NIGHT,
)
annotation class ScreenshotDetailPinnedDrawerPreviews

@PreviewTest
@ScreenshotDetailPinnedDrawerPreviews
@Composable
fun DetailPinnedDrawerBlurredScrolledScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        val widthClass = previewWidthSizeClass()
        val isLandscape = isLandscapeWindow()
        val blurState = rememberPinnedDrawerBlurState(enabled = { true })
        val listState =
            rememberLazyListState(
                initialFirstVisibleItemIndex = if (isLandscape) 1 else 2,
                initialFirstVisibleItemScrollOffset = 80,
            )
        val topBarBlurState = rememberDetailTopBarBlurState(listState, hasHeroSlot = !isLandscape)
        val homeDestination = remember {
            NavDestination("composable").apply { route = Destination.HOME.route }
        }
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
                PreviewData.season(name = "Season 3", unplayed = 8),
                PreviewData.season(name = "Season 4", unplayed = 10),
            )
        val nextEpisode =
            ScreenshotPreviewFixtures.episodes.first().copy(
                runtimeTicks = 0L,
                playbackPositionTicks = 0L,
                sources = listOf(ScreenshotPreviewFixtures.source1080pHdrDts),
            )

        CompositionLocalProvider(
            LocalPinnedDrawerStartInset provides PinnedDrawerStartOccupancy,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().pinnedDrawerBlurSource(blurState)) {
                    AfinityScreen(
                        topBar = { backgroundOpacity ->
                            AfinityTopBar(title = {}, backgroundOpacity = backgroundOpacity)
                        },
                        mode = AfinityScreenMode.Hero,
                        topBarBlurState = topBarBlurState,
                        topBarBlurContainerColor =
                            if (isLandscape) Color.Black
                            else MaterialTheme.colorScheme.background,
                    ) {
                        val headerActions: @Composable (Boolean) -> Unit = { landscapeMode ->
                            val primaryButton: @Composable () -> Unit = {
                                PrimaryPlaybackButton(
                                    item = show,
                                    nextEpisode = nextEpisode,
                                    selectedMediaSource = null,
                                    isLoading = false,
                                    onPlayRequested = { _, _ -> },
                                )
                            }
                            val actionButtons: @Composable () -> Unit = {
                                ActionButtonsRow(
                                    item = show,
                                    isInWatchlist = false,
                                    hasTrailer = false,
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
                                    isLandscape = landscapeMode,
                                )
                            }
                            if (landscapeMode) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Box(modifier = Modifier.widthIn(max = 200.dp)) {
                                        primaryButton()
                                    }
                                    actionButtons()
                                }
                            } else {
                                primaryButton()
                                actionButtons()
                            }
                        }

                        if (isLandscape) {
                            LandscapeDetailLayout(
                                backdropStyle = BackdropStyle.Cinematic,
                                backdrop = rememberItemBackdropChain(show),
                                listState = listState,
                                arrangement = LandscapeDetailArrangement.Single,
                                edgeToEdgeItems = true,
                                items = {
                                    detailItem(
                                        key = "header",
                                        horizontalPadding = DetailGutter,
                                        gap = 0.dp,
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                        ) {
                                            MediaLogoHeader(
                                                logoUrl = null,
                                                name = show.name,
                                                isLandscape = true,
                                            )
                                            MetadataRow(item = show)
                                            headerActions(true)
                                        }
                                    }
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
                                        horizontalPadding = DetailGutter,
                                        detailLayout = DetailLayout.MODERN,
                                    )
                                },
                            )
                        } else {
                            PortraitDetailLayout(
                                hero =
                                    PortraitHero.Image(
                                        ImageHeroKind.Backdrop,
                                        rememberItemBackdropChain(show),
                                    ),
                                listState = listState,
                                edgeToEdgeItems = true,
                                header = {
                                    MediaLogoHeader(
                                        logoUrl = null,
                                        name = show.name,
                                        isLandscape = false,
                                    )
                                    MetadataRow(item = show)
                                    headerActions(false)
                                },
                                items = {
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
                                        horizontalPadding = DetailGutter,
                                        detailLayout = DetailLayout.MODERN,
                                    )
                                },
                            )
                        }
                    }
                }

                AppPinnedNavigationDrawer(
                    blurState = blurState,
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    AppNavigationDrawerContent(
                        currentDestination = homeDestination,
                        userName = "jklinker",
                        userProfileImageUrl = "preview://avatar/jklinker",
                        serverName = "unraid-server",
                        connectionType = ConnectionType.REMOTE,
                        isAdmin = true,
                        isOffline = false,
                        favoritesCount = 2,
                        watchlistCount = 0,
                        isJellyseerrAuthenticated = false,
                        isAudiobookshelfAuthenticated = true,
                        hasLiveTvAccess = true,
                        librariesInDrawer = false,
                        libraries = ScreenshotPreviewFixtures.collections,
                        selectedLibraryId = null,
                        sessionState = SessionSwitcherState(),
                        onDestinationClick = {},
                        onLibraryClick = {},
                        onSettingsClick = {},
                        onAddAccountClick = {},
                        onSwitchSession = { _, _ -> },
                        onForgetSession = {},
                        onCloseDrawer = {},
                        isPinned = true,
                        appVersionName = "0.12.10",
                    )
                }
            }
        }
    }
}
