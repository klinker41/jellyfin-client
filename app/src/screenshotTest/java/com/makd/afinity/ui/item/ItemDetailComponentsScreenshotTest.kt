package com.makd.afinity.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.makd.afinity.ui.item.components.shared.CastSection
import com.makd.afinity.ui.item.components.shared.CertifiedFreshStamp
import com.makd.afinity.ui.item.components.shared.ExternalLinksSection
import com.makd.afinity.ui.item.components.shared.MediaStreamBadges
import com.makd.afinity.ui.item.components.shared.MetadataDot
import com.makd.afinity.ui.item.components.shared.MetadataFlowRow
import com.makd.afinity.ui.item.components.shared.MetadataRow
import com.makd.afinity.ui.item.components.shared.MetadataText
import com.makd.afinity.ui.item.components.shared.MetadataTimeLeft
import com.makd.afinity.ui.item.components.shared.RatingStamp
import com.makd.afinity.ui.item.components.shared.VerifiedHotStamp
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.ScreenshotComponentPreviews
import com.makd.afinity.ui.preview.ScreenshotPreviewFixtures
import com.makd.afinity.ui.preview.ScreenshotPreviewHost
import com.makd.afinity.ui.preview.previewWidthSizeClass

@OptIn(ExperimentalLayoutApi::class)
@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun ItemDetailBadgesAndMetadataScreenshotTest() {
    ScreenshotPreviewHost {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf(
                    ScreenshotPreviewFixtures.source4kDvAtmos,
                    ScreenshotPreviewFixtures.source1080pHdrDts,
                    ScreenshotPreviewFixtures.sourceSdStereo,
                )
                .forEach { source ->
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MediaStreamBadges(source = source)
                    }
                }

            MetadataRow(item = ScreenshotPreviewFixtures.movieForMetadata())

            MetadataFlowRow {
                MetadataTimeLeft(
                    progress = 0.45f,
                    remainingTicks = 35 * ScreenshotPreviewFixtures.TICKS_PER_MINUTE,
                )
                MetadataDot()
                MetadataText("2h 35m")
                MetadataDot()
                MetadataText("PG-13")
            }

            MetadataRow(item = PreviewData.boxSet(name = "Dune Collection", count = 2))
        }
    }
}

@PreviewTest
@ScreenshotComponentPreviews
@Composable
fun ItemDetailCastStampsAndLinksScreenshotTest() {
    ScreenshotPreviewHost {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CastSection(
                item = ScreenshotPreviewFixtures.movieForMetadata(),
                onPersonClick = {},
                widthSizeClass = previewWidthSizeClass(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RatingStamp(
                    text = "Fresh",
                    colors = CertifiedFreshStamp,
                    modifier = Modifier.size(64.dp),
                )
                RatingStamp(
                    text = "Hot",
                    colors = VerifiedHotStamp,
                    modifier = Modifier.size(64.dp),
                )
            }

            ExternalLinksSection(externalUrls = ScreenshotPreviewFixtures.externalUrls)
        }
    }
}
