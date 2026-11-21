package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.ScreenshotComponentPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewFixtures
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import com.makd.afinity.ui.preview.ScreenshotWindowPreviews
import com.makd.afinity.ui.preview.previewWidthSizeClass

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun EpisodeListCardScreenshotTest() {
    ScreenshotPreviewHost {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenshotPreviewFixtures.episodes.forEach { episode ->
                EpisodeListCard(item = episode, onClick = {}, thumbnailWidth = 148.dp)
            }
        }
    }
}

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun LibraryCardsGridScreenshotTest() {
    ScreenshotPreviewHost {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenshotPreviewFixtures.collections.chunked(2).forEach { rowLibs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowLibs.forEach { library ->
                        LibraryCard(
                            library = library,
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowLibs.size == 1) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun DownloadRowsAndSeasonSelectorScreenshotTest() {
    ScreenshotPreviewHost {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DownloadListItemRow(
                imageUrl = "preview://primary/movie-Dune",
                title = "Dune (2021)",
                aspectRatio = 2f / 3f,
                onClick = {},
                onDelete = {},
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Downloading · 64% · 4.2 GB", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { 0.64f }, modifier = Modifier.fillMaxWidth())
                }
            }

            DownloadListItemRow(
                imageUrl = "preview://primary/album-1",
                title = "Original Soundtrack",
                aspectRatio = 1f,
                imageAlpha = 0.4f,
                onDelete = {},
            ) {
                Text(
                    "Unavailable offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SeasonSelector(
                    availableSeasons = 4,
                    seasonNumbers = listOf(0, 1, 2, 3, 4),
                    selectedSeasons = listOf(2, 3),
                    disabledSeasons = listOf(1),
                    onSeasonsChange = {},
                )
                SeasonSelector(
                    availableSeasons = 0,
                    seasonNumbers = emptyList(),
                    selectedSeasons = emptyList(),
                    onSeasonsChange = {},
                )
            }
        }
    }
}

@PreviewTest
@ScreenshotWindowPreviews
@Composable
fun MediaCategoryGridPortraitScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        MediaCategoryGrid(
            items = PreviewData.posterRow,
            landscape = false,
            widthSizeClass = previewWidthSizeClass(),
            playerClearance = 0.dp,
            onItemClick = {},
        )
    }
}

@PreviewTest
@ScreenshotWindowPreviews
@Composable
fun MediaCategoryGridLandscapeScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        MediaCategoryGrid(
            items = PreviewData.landscapeRow,
            landscape = true,
            widthSizeClass = previewWidthSizeClass(),
            playerClearance = 0.dp,
            onItemClick = {},
        )
    }
}
