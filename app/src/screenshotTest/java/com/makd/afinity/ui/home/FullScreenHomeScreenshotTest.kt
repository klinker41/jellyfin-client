package com.makd.afinity.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AfinityTopBar
import com.makd.afinity.ui.components.HeroCarousel
import com.makd.afinity.ui.components.rememberTopBarBlurState
import com.makd.afinity.ui.components.topBarBlurBackdrop
import com.makd.afinity.ui.components.topBarBlurSource
import com.makd.afinity.ui.home.components.ItemsRowSection
import com.makd.afinity.ui.home.components.NextUpSection
import com.makd.afinity.ui.home.components.OptimizedContinueWatchingSection
import com.makd.afinity.ui.home.components.PopularStudiosSection
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.ScreenshotFullScreenPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewFixtures
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import com.makd.afinity.ui.preview.ScreenshotWindowPreviews
import com.makd.afinity.ui.preview.previewWidthSizeClass
import com.makd.afinity.ui.utils.rememberTopBarScrollProgress
import com.makd.afinity.ui.utils.topBarScrollKey

@PreviewTest
@ScreenshotFullScreenPreviews
@Composable
fun HomeFullScreenScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        val widthClass = previewWidthSizeClass()
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

        AfinityScreen(
            mode = AfinityScreenMode.Hero,
            topBar = { backgroundOpacity ->
                AfinityTopBar(
                    title = { AfinityTopAppBarTitle("AFinity") },
                    backgroundOpacity = backgroundOpacity,
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { _ ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                        items = ScreenshotPreviewFixtures.episodes.take(2) + heroMovie,
                        onItemClick = {},
                        widthSizeClass = widthClass,
                    )
                }

                item(key = "next_up") {
                    NextUpSection(
                        episodes = ScreenshotPreviewFixtures.episodes,
                        onEpisodeClick = {},
                        widthSizeClass = widthClass,
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
                    )
                }
            }
        }
    }
}

@PreviewTest
@ScreenshotWindowPreviews
@Composable
fun HomeScrolledTopBarScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        val widthClass = previewWidthSizeClass()
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
        val listState =
            rememberLazyListState(
                initialFirstVisibleItemIndex = 1,
                initialFirstVisibleItemScrollOffset = 80,
            )
        val blurProgress by rememberTopBarScrollProgress(listState, hasHeroSlot = true)
        val blurState =
            rememberTopBarBlurState(
                progress = { blurProgress },
                scrollKey = { listState.topBarScrollKey() },
            )
        val backgroundColor = MaterialTheme.colorScheme.background

        AfinityScreen(
            mode = AfinityScreenMode.Immersive,
            topBar = { backgroundOpacity ->
                AfinityTopBar(
                    title = { AfinityTopAppBarTitle("AFinity") },
                    modifier =
                        Modifier.topBarBlurBackdrop(blurState, containerColor = backgroundColor),
                    backgroundOpacity = backgroundOpacity,
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { _ ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().topBarBlurSource(blurState),
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
                        items = ScreenshotPreviewFixtures.episodes.take(2) + heroMovie,
                        onItemClick = {},
                        widthSizeClass = widthClass,
                    )
                }

                item(key = "next_up") {
                    NextUpSection(
                        episodes = ScreenshotPreviewFixtures.episodes,
                        onEpisodeClick = {},
                        widthSizeClass = widthClass,
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
                    )
                }
            }
        }
    }
}
