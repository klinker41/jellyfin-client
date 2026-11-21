package com.makd.afinity.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.ScreenshotFullScreenPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewFixtures
import com.makd.afinity.ui.preview.ScreenshotPreviewHost

@PreviewTest
@ScreenshotFullScreenPreviews
@Composable
fun SearchExploreFullScreenScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchTopBar(
                searchQuery = "",
                onSearchQueryChange = {},
                onBackClick = {},
                focusRequester = remember { FocusRequester() },
                onSearch = {},
            )
            SearchHomeContent(
                genres =
                    listOf(
                        "Science Fiction",
                        "Drama",
                        "Thriller",
                        "Action",
                        "Documentary",
                        "Animation",
                        "Mystery",
                        "Fantasy",
                    ),
                onGenreClick = {},
            )
        }
    }
}

@PreviewTest
@ScreenshotFullScreenPreviews
@Composable
fun SearchResultsFullScreenScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchTopBar(
                searchQuery = "Dune",
                onSearchQueryChange = {},
                onBackClick = {},
                focusRequester = remember { FocusRequester() },
                onSearch = {},
            )
            SearchResultsContent(
                results =
                    listOf(
                        PreviewData.movie(),
                        PreviewData.show(),
                        ScreenshotPreviewFixtures.episodes.first(),
                    ),
                onItemClick = {},
                onEpisodeClick = {},
            )
        }
    }
}
