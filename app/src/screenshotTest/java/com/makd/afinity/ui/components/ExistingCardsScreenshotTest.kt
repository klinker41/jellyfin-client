package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.data.models.common.CardSize
import com.makd.afinity.ui.preview.CardSizePreviewProvider
import com.makd.afinity.ui.preview.PreviewCardRow
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.ScreenshotComponentPreviews
import com.makd.afinity.ui.preview.ScreenshotPhonePreview
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import com.makd.afinity.ui.theme.CardDimensions

@Composable
private fun ContinueWatchingCardPreviewContent(cardSize: CardSize) {
    ScreenshotPreviewHost(cardSize = cardSize) {
        PreviewCardRow(landscape = true) { cardWidth ->
            PreviewData.landscapeRow.forEach { item ->
                ContinueWatchingCard(
                    item = item,
                    onClick = {},
                    cardWidth = cardWidth,
                    onMoreClick = {},
                )
            }
        }
    }
}

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun ContinueWatchingCardResponsiveScreenshotTest() {
    ContinueWatchingCardPreviewContent(CardSize.DEFAULT)
}

@PreviewTest
@ScreenshotPhonePreview
@Composable
fun ContinueWatchingCardSizeScreenshotTest(
    @PreviewParameter(CardSizePreviewProvider::class) cardSize: CardSize,
) {
    ContinueWatchingCardPreviewContent(cardSize)
}

@Composable
private fun MediaItemCardPreviewContent(cardSize: CardSize) {
    ScreenshotPreviewHost(cardSize = cardSize) {
        PreviewCardRow { cardWidth ->
            PreviewData.posterRow.forEach { item ->
                MediaItemCard(item = item, onClick = {}, cardWidth = cardWidth)
            }
        }
    }
}

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun MediaItemCardResponsiveScreenshotTest() {
    MediaItemCardPreviewContent(CardSize.DEFAULT)
}

@PreviewTest
@ScreenshotPhonePreview
@Composable
fun MediaItemCardSizeScreenshotTest(
    @PreviewParameter(CardSizePreviewProvider::class) cardSize: CardSize,
) {
    MediaItemCardPreviewContent(cardSize)
}

@Composable
private fun MediaItemGridCardPreviewContent(cardSize: CardSize) {
    ScreenshotPreviewHost(cardSize = cardSize) {
        LazyVerticalGrid(
            columns = CardDimensions.gridCells(),
            modifier = Modifier.fillMaxWidth().height(640.dp),
            contentPadding = PaddingValues(horizontal = CardDimensions.RowGutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(CardDimensions.RowGap),
            verticalArrangement = Arrangement.spacedBy(CardDimensions.GridRowGap),
        ) {
            items(PreviewData.posterRow) { item -> MediaItemGridCard(item = item, onClick = {}) }
        }
    }
}

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun MediaItemGridCardResponsiveScreenshotTest() {
    MediaItemGridCardPreviewContent(CardSize.DEFAULT)
}

@PreviewTest
@ScreenshotPhonePreview
@Composable
fun MediaItemGridCardSizeScreenshotTest(
    @PreviewParameter(CardSizePreviewProvider::class) cardSize: CardSize,
) {
    MediaItemGridCardPreviewContent(cardSize)
}
