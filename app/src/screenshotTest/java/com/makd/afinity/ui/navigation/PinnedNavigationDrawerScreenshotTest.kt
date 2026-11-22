package com.makd.afinity.ui.navigation

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AfinityTopBar
import com.makd.afinity.ui.components.AppNavigationDrawerContent
import com.makd.afinity.ui.components.AppPinnedNavigationDrawer
import com.makd.afinity.ui.components.HeroCarousel
import com.makd.afinity.ui.components.LocalPinnedDrawerStartInset
import com.makd.afinity.ui.components.PinnedDrawerStartOccupancy
import com.makd.afinity.ui.components.pinnedDrawerBlurSource
import com.makd.afinity.ui.components.rememberPinnedDrawerBlurState
import com.makd.afinity.ui.components.rememberTopBarBlurState
import com.makd.afinity.ui.components.topBarBlurBackdrop
import com.makd.afinity.ui.components.topBarBlurSource
import com.makd.afinity.ui.home.components.ItemsRowSection
import com.makd.afinity.ui.home.components.NextUpSection
import com.makd.afinity.ui.home.components.OptimizedContinueWatchingSection
import com.makd.afinity.ui.home.components.PopularStudiosSection
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.ScreenshotPreviewFixtures
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import com.makd.afinity.ui.preview.previewWidthSizeClass
import com.makd.afinity.ui.settings.SessionSwitcherState
import com.makd.afinity.ui.utils.rememberTopBarScrollProgress
import com.makd.afinity.ui.utils.topBarScrollKey

private const val NIGHT = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL

@Preview(
    name = "Foldable unfolded dark",
    group = "PinnedDrawer",
    device = "spec:width=673dp,height=841dp",
    uiMode = NIGHT,
)
@Preview(
    name = "Foldable unfolded light",
    group = "PinnedDrawer",
    device = "spec:width=673dp,height=841dp",
)
@Preview(
    name = "Foldable unfolded landscape",
    group = "PinnedDrawer",
    device = "spec:width=841dp,height=673dp",
    uiMode = NIGHT,
)
@Preview(
    name = "Tablet landscape",
    group = "PinnedDrawer",
    device = "spec:width=1280dp,height=800dp",
    uiMode = NIGHT,
)
annotation class ScreenshotPinnedDrawerPreviews

@PreviewTest
@ScreenshotPinnedDrawerPreviews
@Composable
fun PinnedNavigationDrawerAtRestScreenshotTest() {
    PinnedDrawerHomeHarness(
        verticalListState = rememberLazyListState(),
        continueWatchingRowState = rememberLazyListState(),
        nextUpRowState = rememberLazyListState(),
        scifiRowState = rememberLazyListState(),
    )
}

@PreviewTest
@ScreenshotPinnedDrawerPreviews
@Composable
fun PinnedNavigationDrawerScrolledBehindScreenshotTest() {
    PinnedDrawerHomeHarness(
        verticalListState =
            rememberLazyListState(
                initialFirstVisibleItemIndex = 1,
                initialFirstVisibleItemScrollOffset = 40,
            ),
        continueWatchingRowState =
            rememberLazyListState(
                initialFirstVisibleItemIndex = 1,
                initialFirstVisibleItemScrollOffset = 80,
            ),
        nextUpRowState =
            rememberLazyListState(
                initialFirstVisibleItemIndex = 1,
                initialFirstVisibleItemScrollOffset = 120,
            ),
        scifiRowState =
            rememberLazyListState(
                initialFirstVisibleItemIndex = 2,
                initialFirstVisibleItemScrollOffset = 0,
            ),
    )
}

@Composable
private fun PinnedDrawerHomeHarness(
    verticalListState: LazyListState,
    continueWatchingRowState: LazyListState,
    nextUpRowState: LazyListState,
    scifiRowState: LazyListState,
) {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        val widthClass = previewWidthSizeClass()
        val blurState = rememberPinnedDrawerBlurState(enabled = { true })
        val topBarBlurProgress by
            rememberTopBarScrollProgress(verticalListState, hasHeroSlot = true)
        val topBarBlurState =
            rememberTopBarBlurState(
                progress = { topBarBlurProgress },
                scrollKey = { verticalListState.topBarScrollKey() },
            )
        val backgroundColor = MaterialTheme.colorScheme.background
        val homeDestination = remember {
            NavDestination("composable").apply { route = Destination.HOME.route }
        }
        val heroMovie =
            PreviewData.movie(progress = 0f).copy(
                runtimeTicks = 0L,
                overview =
                    "A noble family becomes embroiled in a war for control over the galaxy's most valuable asset.",
            )
        val heroShow =
            PreviewData.show().copy(
                overview =
                    "Mark leads a team of office workers whose memories have been surgically divided between work and personal lives.",
            )

        val nextUpEpisodes =
            ScreenshotPreviewFixtures.episodes +
                listOf(
                    PreviewData.episode(
                        name = "Hide and Seek",
                        seriesName = "Severance",
                        season = 1,
                        index = 6,
                        progress = 0f,
                        premiereDate = ScreenshotPreviewFixtures.AIRED_DATE.plusDays(21),
                    ),
                    PreviewData.episode(
                        name = "Defiant Jazz",
                        seriesName = "Severance",
                        season = 1,
                        index = 7,
                        progress = 0f,
                        premiereDate = ScreenshotPreviewFixtures.AIRED_DATE.plusDays(28),
                    ),
                )

        CompositionLocalProvider(
            LocalPinnedDrawerStartInset provides PinnedDrawerStartOccupancy,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().pinnedDrawerBlurSource(blurState)) {
                    AfinityScreen(
                        mode = AfinityScreenMode.Immersive,
                        topBar = { backgroundOpacity ->
                            AfinityTopBar(
                                title = { AfinityTopAppBarTitle("Jellyfin") },
                                modifier =
                                    Modifier.topBarBlurBackdrop(
                                        topBarBlurState,
                                        containerColor = backgroundColor,
                                    ),
                                backgroundOpacity = backgroundOpacity,
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) { _ ->
                        LazyColumn(
                            state = verticalListState,
                            modifier = Modifier.fillMaxSize().topBarBlurSource(topBarBlurState),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            item(key = "hero") {
                                HeroCarousel(
                                    items = listOf(heroMovie, heroShow),
                                    onWatchNowClick = {},
                                    onPlayTrailerClick = {},
                                    onMoreInformationClick = {},
                                    slideSeconds = 0,
                                )
                            }

                            item(key = "continue_watching") {
                                OptimizedContinueWatchingSection(
                                    items =
                                        ScreenshotPreviewFixtures.episodes +
                                            listOf(heroMovie, heroShow),
                                    onItemClick = {},
                                    widthSizeClass = widthClass,
                                    scrollState = continueWatchingRowState,
                                )
                            }

                            item(key = "next_up") {
                                NextUpSection(
                                    episodes = nextUpEpisodes,
                                    onEpisodeClick = {},
                                    widthSizeClass = widthClass,
                                    scrollState = nextUpRowState,
                                )
                            }

                            item(key = "popular_studios") {
                                PopularStudiosSection(
                                    studios = ScreenshotPreviewFixtures.studios,
                                    onStudioClick = {},
                                    widthSizeClass = widthClass,
                                )
                            }

                            item(key = "scifi_row") {
                                ItemsRowSection(
                                    title = "Sci-Fi Essentials",
                                    items = PreviewData.posterRow,
                                    sectionKey = "scifi",
                                    onItemClick = {},
                                    cardStyle = CustomSectionCardStyle.PORTRAIT,
                                    onViewAllClick = {},
                                    scrollState = scifiRowState,
                                )
                            }
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
