package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.R
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.ScreenshotComponentPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import com.makd.afinity.ui.preview.ScreenshotWindowPreviews

@Composable
private fun PreviewActionIcon(iconRes: Int) {
    IconButton(onClick = {}) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
    }
}

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun DetailActionRowScreenshotTest() {
    ScreenshotPreviewHost {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 4, 3).forEach { count ->
                DetailActionRow(
                    onShuffle = {},
                    onPlay = {},
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    listOf(
                            R.drawable.ic_favorite,
                            R.drawable.ic_compass,
                            R.drawable.ic_download,
                            R.drawable.ic_delete,
                            R.drawable.ic_edit,
                        )
                        .take(count)
                        .forEach { PreviewActionIcon(it) }
                }
            }
        }
    }
}

@PreviewTest
@ScreenshotWindowPreviews
@Composable
fun EmptyStateScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        EmptyState(
            icon = painterResource(id = R.drawable.ic_server),
            title = stringResource(R.string.empty_servers_title),
            message = stringResource(R.string.empty_servers_message),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@PreviewTest
@ScreenshotWindowPreviews
@Composable
fun ListPickerDialogScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        val containerHeight = windowHeightDp()
        ListPickerDialog(
            title = stringResource(R.string.custom_sections_new),
            onDismiss = {},
            height = min(720.dp, containerHeight * 0.9f),
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PreviewData.posterRow.forEach { item ->
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun LoadingButtonScreenshotTest() {
    ScreenshotPreviewHost {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoadingButton(
                loading = false,
                text = stringResource(R.string.btn_test_connection),
                onClick = {},
            )
            LoadingButton(
                loading = false,
                text = stringResource(R.string.jellyseerr_quick_connect_button),
                onClick = {},
            )
            LoadingButton(loading = true, text = "", onClick = {})
        }
    }
}

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun SectionRowHeaderScreenshotTest() {
    ScreenshotPreviewHost {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionRowHeader(
                title = stringResource(R.string.home_continue_watching),
                onViewAllClick = {},
            )
            SectionRowHeader(title = stringResource(R.string.watchlist_title), onViewAllClick = {})
            SectionRowHeader(title = stringResource(R.string.favorites_title))
            SectionRowHeader(
                title = "Because you watched ${PreviewData.LONG_TITLE}",
                onViewAllClick = {},
            )
        }
    }
}

@PreviewTest
@ScreenshotWindowPreviews
@Composable
fun FullScreenErrorScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        FullScreenError(
            message = stringResource(R.string.error_load_content_generic),
            actionText = stringResource(R.string.action_retry),
            onActionClick = {},
        )
    }
}

@PreviewTest
@ScreenshotWindowPreviews
@Composable
fun FullScreenEmptyScreenshotTest() {
    ScreenshotPreviewHost(modifier = Modifier.fillMaxSize()) {
        FullScreenEmpty(
            title = stringResource(R.string.empty_servers_title),
            message = stringResource(R.string.empty_servers_message),
            icon = painterResource(id = R.drawable.ic_server),
            actionText = stringResource(R.string.action_retry),
            onActionClick = {},
        )
    }
}
