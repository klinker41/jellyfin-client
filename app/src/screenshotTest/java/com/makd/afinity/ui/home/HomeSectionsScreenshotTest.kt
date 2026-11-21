package com.makd.afinity.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.ui.home.components.HomeSectionHeader
import com.makd.afinity.ui.home.components.ItemsRowSection
import com.makd.afinity.ui.home.components.NextUpSection
import com.makd.afinity.ui.home.components.PopularStudiosSection
import com.makd.afinity.ui.home.components.SquareMediaTile
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.ScreenshotComponentPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewFixtures
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import com.makd.afinity.ui.preview.previewWidthSizeClass
import com.makd.afinity.ui.theme.CardDimensions

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun HomeSectionsScreenshotTest() {
    ScreenshotPreviewHost {
        val widthClass = previewWidthSizeClass()
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = CardDimensions.RowGutter),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeSectionHeader(title = "Featured", bottomPadding = 4.dp)
                HomeSectionHeader(
                    title = "Because you watched ${PreviewData.LONG_TITLE}",
                    onViewAllClick = {},
                    bottomPadding = 4.dp,
                )
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = CardDimensions.RowGutter),
                horizontalArrangement = Arrangement.spacedBy(CardDimensions.RowGap),
            ) {
                SquareMediaTile(
                    imageUrl = "preview://album/1",
                    contentDescription = "Discovery",
                    title = "Random Access Memories",
                    subtitle = "Daft Punk",
                    onClick = {},
                )
                SquareMediaTile(
                    imageUrl = "preview://album/2",
                    contentDescription = "Podcast",
                    title = "The Weekly Cinema Podcast With a Very Long Title",
                    onClick = {},
                )
                SquareMediaTile(
                    imageUrl = "preview://album/3",
                    contentDescription = "Soundtrack",
                    title = "Dune: Part Two",
                    subtitle = "Hans Zimmer",
                    onClick = {},
                )
            }

            PopularStudiosSection(
                studios = ScreenshotPreviewFixtures.studios,
                onStudioClick = {},
                widthSizeClass = widthClass,
            )

            ItemsRowSection(
                title = "Sci-Fi Essentials",
                items = PreviewData.posterRow,
                sectionKey = "scifi",
                onItemClick = {},
                cardStyle = CustomSectionCardStyle.PORTRAIT,
                onViewAllClick = {},
            )

            NextUpSection(
                episodes = ScreenshotPreviewFixtures.episodes,
                onEpisodeClick = {},
                widthSizeClass = widthClass,
            )
        }
    }
}
