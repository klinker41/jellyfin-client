package com.makd.afinity.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AfinityTopBar
import com.makd.afinity.ui.components.LibraryCard
import com.makd.afinity.ui.player.AudioMiniPlayer
import com.makd.afinity.ui.player.AudioMiniPlayerState
import com.makd.afinity.ui.preview.ScreenshotFullScreenPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewFixtures
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import com.makd.afinity.ui.preview.previewWidthSizeClass
import com.makd.afinity.ui.theme.CardDimensions

@OptIn(ExperimentalSharedTransitionApi::class)
@PreviewTest
@ScreenshotFullScreenPreviews
@Composable
fun NavigationShellFullScreenScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        val widthClass = previewWidthSizeClass()
        val layoutType =
            if (widthClass == WindowWidthSizeClass.Compact) {
                NavigationSuiteType.NavigationBar
            } else {
                NavigationSuiteType.NavigationRail
            }
        val destinations =
            listOf(
                Destination.HOME,
                Destination.FAVORITES,
                Destination.WATCHLIST,
                Destination.AUDIOBOOKS,
            )

        NavigationSuiteScaffold(
            layoutType = layoutType,
            navigationSuiteItems = {
                destinations.forEachIndexed { index, destination ->
                    val selected = index == 0
                    item(
                        selected = selected,
                        onClick = {},
                        icon = {
                            Icon(
                                painter =
                                    painterResource(
                                        id =
                                            if (selected) destination.selectedIconRes
                                            else destination.unselectedIconRes,
                                    ),
                                contentDescription = stringResource(destination.titleRes),
                            )
                        },
                        label = {
                            if (selected) {
                                Text(
                                    text = stringResource(destination.titleRes),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            navigationSuiteColors =
                NavigationSuiteDefaults.colors(
                    navigationBarContainerColor = MaterialTheme.colorScheme.surface,
                    navigationRailContainerColor = MaterialTheme.colorScheme.surface,
                ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AfinityScreen(
                    topBar = { backgroundOpacity ->
                        AfinityTopBar(
                            title = { AfinityTopAppBarTitle("Libraries") },
                            backgroundOpacity = backgroundOpacity,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { _ ->
                    LazyVerticalGrid(
                        columns = CardDimensions.landscapeGridCells(),
                        contentPadding =
                            PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 96.dp,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(CardDimensions.RowGap),
                        verticalArrangement = Arrangement.spacedBy(CardDimensions.GridRowGap),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = ScreenshotPreviewFixtures.collections,
                            key = { it.id },
                        ) { library ->
                            LibraryCard(
                                library = library,
                                targetWidth = 160.dp,
                                targetHeight = 90.dp,
                                onClick = {},
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                SharedTransitionLayout(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                ) {
                    AnimatedVisibility(visible = true) {
                        AudioMiniPlayer(
                            state =
                                AudioMiniPlayerState.Music(
                                    title = "Instant Crush",
                                    artist = "Daft Punk feat. Julian Casablancas",
                                    coverUrl = null,
                                    blurHash = "L6PZfSi_.AyE_3t7t7R**0o#DgR4",
                                    positionMs = 95_000L,
                                    durationMs = 337_000L,
                                    isPlaying = true,
                                    isBuffering = false,
                                ),
                            animatedVisibilityScope = this@AnimatedVisibility,
                            onPlayPauseClick = {},
                            onBackward = {},
                            onForward = {},
                            onCloseClick = {},
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
