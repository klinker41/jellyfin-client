package com.makd.afinity.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.components.CardRow
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@Composable
fun ShowGenreSection(
    genre: String,
    shows: List<AfinityShow>,
    loaded: Boolean,
    onVisible: () -> Unit,
    onHidden: () -> Unit,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onVisible()
        onDispose { onHidden() }
    }

    if (loaded && shows.isEmpty()) return

    val cardWidth = widthSizeClass.portraitWidth
    val fixedRowHeight = CardDimensions.rowHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)

    Column(modifier = modifier) {
        HomeSectionHeader(
            title = stringResource(R.string.home_genre_shows_title, genre),
            modifier = Modifier.padding(horizontal = CardDimensions.RowGutter),
        )

        if (!loaded) {
            MediaRowSkeleton(
                cardWidth = cardWidth,
                height = fixedRowHeight,
                contentPadding = CardDimensions.RowContentPadding,
            )
        } else {
            CardRow(
                modifier = Modifier.fillMaxWidth().height(fixedRowHeight),
                contentPadding = CardDimensions.RowContentPadding,
            ) {
                items(
                    items = shows,
                    key = { "show_genre_${genre}_${it.id}" },
                    contentType = { "genre_show_card" },
                ) { show ->
                    MediaItemCard(
                        item = show,
                        onClick = { onItemClick(show) },
                        cardWidth = cardWidth,
                    )
                }
            }
        }
    }
}
