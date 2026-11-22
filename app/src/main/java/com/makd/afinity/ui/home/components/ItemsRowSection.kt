package com.makd.afinity.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.components.CardRow
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.components.rememberCardRowListState
import com.makd.afinity.ui.theme.CardDimensions

@Composable
fun ItemsRowSection(
    title: String,
    items: List<AfinityItem>,
    sectionKey: String,
    onItemClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier,
    cardStyle: CustomSectionCardStyle = CustomSectionCardStyle.PORTRAIT,
    onViewAllClick: (() -> Unit)? = null,
    scrollState: LazyListState = rememberCardRowListState(),
) {
    val isLandscape = cardStyle == CustomSectionCardStyle.LANDSCAPE
    val cardWidth = CardDimensions.cardWidthFor(cardStyle)
    val aspectRatio = CardDimensions.aspectRatioFor(cardStyle)
    val fixedRowHeight = CardDimensions.rowHeight(cardWidth, aspectRatio)

    Column(modifier = modifier) {
        HomeSectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = CardDimensions.RowGutter),
            onViewAllClick = onViewAllClick,
        )

        CardRow(
            state = scrollState,
            modifier = Modifier.fillMaxWidth().height(fixedRowHeight),
            contentPadding = CardDimensions.RowContentPadding,
        ) {
            items(
                items = items,
                key = { item -> "${sectionKey}_${item.id}" },
                contentType = {
                    if (isLandscape) "landscape_media_card" else "portrait_media_card"
                },
            ) { item ->
                if (isLandscape) {
                    ContinueWatchingCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        cardWidth = cardWidth,
                    )
                } else {
                    MediaItemCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        cardWidth = cardWidth,
                        aspectRatio = aspectRatio,
                    )
                }
            }
        }
    }
}
