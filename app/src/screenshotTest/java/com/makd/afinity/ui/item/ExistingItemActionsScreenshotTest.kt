package com.makd.afinity.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.item.components.shared.ActionButtonsRow
import com.makd.afinity.ui.item.components.shared.DetailPrimaryButton
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.ScreenshotComponentPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewHost

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun ActionButtonsRowScreenshotTest() {
    ScreenshotPreviewHost {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            listOf<AfinityItem>(PreviewData.show(), PreviewData.movie()).forEach { item ->
                ActionButtonsRow(
                    item = item,
                    isInWatchlist = false,
                    hasTrailer = true,
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
                    modifier = Modifier.padding(horizontal = 16.dp),
                    isAdmin = true,
                )
            }
        }
    }
}

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun DetailPrimaryButtonScreenshotTest() {
    ScreenshotPreviewHost {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DetailPrimaryButton(
                text = stringResource(R.string.action_resume_playback),
                onClick = {},
                icon = painterResource(id = R.drawable.ic_player_play_filled),
            )
            DetailPrimaryButton(
                text = stringResource(R.string.play_error_missing_source),
                onClick = {},
                icon = painterResource(id = R.drawable.ic_alert_triangle),
                enabled = false,
            )
            DetailPrimaryButton(
                text = stringResource(R.string.status_loading),
                onClick = {},
                enabled = false,
                isLoading = true,
            )
        }
    }
}
