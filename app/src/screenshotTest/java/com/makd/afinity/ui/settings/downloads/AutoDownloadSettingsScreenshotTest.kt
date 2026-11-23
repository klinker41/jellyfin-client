package com.makd.afinity.ui.settings.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.ui.preview.ScreenshotComponentPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import java.util.UUID

private fun previewUuid(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

private val previewLibraries =
    listOf(
        AutoDownloadLibraryOption(
            id = previewUuid("lib-audiobooks"),
            name = "Audiobooks",
            type = CollectionType.Books,
            isPodcast = false,
            enabled = true,
        ),
        AutoDownloadLibraryOption(
            id = previewUuid("lib-podcasts"),
            name = "Podcasts",
            type = CollectionType.Music,
            isPodcast = true,
            enabled = true,
        ),
        AutoDownloadLibraryOption(
            id = previewUuid("lib-movies"),
            name = "Movies",
            type = CollectionType.Movies,
            isPodcast = false,
            enabled = false,
        ),
        AutoDownloadLibraryOption(
            id = previewUuid("lib-shows"),
            name = "TV Shows",
            type = CollectionType.TvShows,
            isPodcast = false,
            enabled = false,
        ),
    )

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun AutoDownloadSettingsScreenshotTest() {
    ScreenshotPreviewHost {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            AutoDownloadSettingsContent(
                uiState =
                    AutoDownloadSettingsUiState(
                        libraries = previewLibraries,
                        isSyncing = false,
                        canDownload = true,
                    ),
                onToggleLibrary = { _, _ -> },
                onSyncNow = {},
            )

            AutoDownloadLibrariesSheetContent(
                libraries = previewLibraries,
                onToggleLibrary = { _, _ -> },
            )
        }
    }
}
